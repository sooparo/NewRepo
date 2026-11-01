package com.sooparo.schedulecatcher

import android.app.Notification
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.sooparo.schedulecatcher.core.Recurrence
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra(EXTRA_ID, -1)
        if (id == -1) return
        when (intent.action) {
            ACTION_FIRE -> fire(context, id)
            ACTION_DISMISS -> Notifications.cancel(context, alarmNotificationId(id))
            ACTION_SNOOZE -> snooze(context, id, intent)
        }
    }

    private fun fire(context: Context, id: Int) {
        val entry = AlarmStore.get(context, id) ?: AlarmStore.Entry(id, System.currentTimeMillis(), "일정 알람", "")
        AlarmStore.remove(context, id)
        showAlarm(context, entry)
        scheduleNextOccurrence(context, entry)
    }

    /** 반복 알람: 다음 일정 시각 기준으로 다시 예약 */
    private fun scheduleNextOccurrence(context: Context, entry: AlarmStore.Entry) {
        val rec = Recurrence.fromRRule(entry.rrule) ?: return
        val anchorAt = entry.anchorAt ?: return
        val zone = ZoneId.systemDefault()
        val anchor = LocalDateTime.ofInstant(Instant.ofEpochMilli(anchorAt), zone)
        val thisEvent = LocalDateTime.ofInstant(Instant.ofEpochMilli(entry.triggerAt + entry.leadMs), zone)
        val next = rec.nextAfter(anchor, thisEvent) ?: return
        val nextTrigger = next.atZone(zone).toInstant().toEpochMilli() - entry.leadMs
        AlarmScheduler.schedule(context, entry.copy(triggerAt = nextTrigger))
    }

    private fun snooze(context: Context, id: Int, intent: Intent) {
        Notifications.cancel(context, alarmNotificationId(id))
        val entry = AlarmStore.Entry(
            // 반복 알람의 다음 회차와 겹치지 않도록 다시 알림은 별도 id
            id = snoozeId(id),
            triggerAt = System.currentTimeMillis() + SNOOZE_MS,
            title = intent.getStringExtra(EXTRA_TITLE) ?: "일정 알람",
            info = intent.getStringExtra(EXTRA_INFO).orEmpty(),
        )
        AlarmScheduler.schedule(context, entry)
    }

    companion object {
        const val ACTION_FIRE = "com.sooparo.schedulecatcher.ALARM_FIRE"
        const val ACTION_DISMISS = "com.sooparo.schedulecatcher.ALARM_DISMISS"
        const val ACTION_SNOOZE = "com.sooparo.schedulecatcher.ALARM_SNOOZE"
        const val EXTRA_ID = "alarm.id"
        const val EXTRA_TITLE = "alarm.title"
        const val EXTRA_INFO = "alarm.info"
        private const val SNOOZE_MS = 5 * 60 * 1000L

        fun alarmNotificationId(id: Int) = 0x70000000 or (id and 0x0FFFFFFF)

        private fun snoozeId(id: Int) = 0x10000000 or (id and 0x0FFFFFFF)

        fun actionIntent(context: Context, action: String, entry: AlarmStore.Entry): Intent =
            Intent(context, AlarmReceiver::class.java)
                .setAction(action)
                .putExtra(EXTRA_ID, entry.id)
                .putExtra(EXTRA_TITLE, entry.title)
                .putExtra(EXTRA_INFO, entry.info)

        fun showAlarm(context: Context, entry: AlarmStore.Entry) {
            val nid = alarmNotificationId(entry.id)

            val ring = Intent(context, AlarmRingActivity::class.java)
                .putExtra(EXTRA_ID, entry.id)
                .putExtra(EXTRA_TITLE, entry.title)
                .putExtra(EXTRA_INFO, entry.info)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION)
            val ringPi = PendingIntent.getActivity(
                context, nid, ring, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )

            fun broadcast(action: String, slot: Int) = PendingIntent.getBroadcast(
                context,
                nid + slot,
                actionIntent(context, action, entry),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )

            val n = NotificationCompat.Builder(context, Notifications.CH_ALARM)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("⏰ ${entry.title}")
                .setContentText(entry.info)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setFullScreenIntent(ringPi, true)
                .setContentIntent(ringPi)
                .setOngoing(true)
                .setAutoCancel(false)
                .setTimeoutAfter(10 * 60 * 1000L)
                .addAction(0, "끄기", broadcast(ACTION_DISMISS, 1))
                .addAction(0, "5분 뒤 다시", broadcast(ACTION_SNOOZE, 2))
                .build()
            // 사용자가 끌 때까지 알람음 반복
            n.flags = n.flags or Notification.FLAG_INSISTENT
            Notifications.post(context, nid, n)
        }
    }
}
