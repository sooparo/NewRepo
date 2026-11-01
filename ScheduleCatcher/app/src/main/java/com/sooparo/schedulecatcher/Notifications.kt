package com.sooparo.schedulecatcher

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

object Notifications {
    const val CH_DETECT = "detect"
    const val CH_RESULT = "result"
    const val CH_ALARM = "alarm_v1"
    const val CH_BRIEFING = "briefing"

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)

        val detect = NotificationChannel(CH_DETECT, "일정 감지", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "메시지에서 일정을 찾으면 저장 방법(캘린더/알람)을 물어봅니다."
        }
        val result = NotificationChannel(CH_RESULT, "저장 결과", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "캘린더 저장 실패 등 결과 안내"
        }
        val alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM) ?: Settings.System.DEFAULT_ALARM_ALERT_URI
        val alarm = NotificationChannel(CH_ALARM, "일정 알람", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "저장한 일정의 알람"
            setSound(
                alarmSound,
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 800, 400, 800, 400, 800)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setBypassDnd(true)
        }
        val briefing = NotificationChannel(CH_BRIEFING, "아침 브리핑", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "매일 아침 오늘 일정과 할 일 요약"
        }
        nm.createNotificationChannels(listOf(detect, result, alarm, briefing))
    }

    fun canPost(context: Context): Boolean =
        (Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()

    @SuppressLint("MissingPermission")
    fun post(context: Context, id: Int, notification: Notification) {
        if (!canPost(context)) return
        runCatching { NotificationManagerCompat.from(context).notify(id, notification) }
    }

    fun cancel(context: Context, id: Int) = NotificationManagerCompat.from(context).cancel(id)

    /**
     * 메시지에서 감지한 일정 → [캘린더] [알람] [할 일] 선택 알림.
     * 같은 [ScheduleData.id] 로 다시 부르면(연속 메시지로 내용 보완) 소리 없이 내용만 갱신된다.
     */
    fun showDetected(context: Context, data: ScheduleData) {
        val nid = data.notificationId
        val prefs = Prefs(context)
        val open = PendingIntent.getActivity(
            context,
            nid * 8,
            data.putInto(Intent(context, ConfirmActivity::class.java)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        fun action(targets: SaveTargets, label: String, slot: Int): NotificationCompat.Action {
            val intent = data.putInto(Intent(context, QuickSaveActivity::class.java))
                .putExtra(QuickSaveActivity.EXTRA_TARGETS, targets.encode())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
            val pi = PendingIntent.getActivity(
                context,
                nid * 8 + slot,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            return NotificationCompat.Action.Builder(0, label, pi).build()
        }

        val whenText = Formats.whenText(data)
        val kind = if (data.isTask) "할 일 감지" else "일정 감지"
        val details = buildString {
            append(whenText)
            data.location?.let { append("\n📍 ").append(it) }
            data.sourceLabel?.let { append("\n").append(it) }
            append("\n\n탭하면 내용을 고치고 여러 곳에 함께 저장할 수 있어요.")
        }

        val builder = NotificationCompat.Builder(context, CH_DETECT)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("$kind: ${data.title}")
            .setContentText("$whenText · 어디에 저장할까요?")
            .setStyle(NotificationCompat.BigTextStyle().bigText(details))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)

        when {
            data.isTask && !data.dateKnown -> builder
                .addAction(action(SaveTargets.TODO, "✅ 할 일 추가", 1))
            data.isTask -> builder
                .addAction(action(SaveTargets.TODO, "✅ 할 일", 1))
                .addAction(action(SaveTargets.CALENDAR, "📅 캘린더", 2))
                .addAction(action(SaveTargets.ALARM, "⏰ 알람", 3))
            else -> builder
                .addAction(action(SaveTargets.CALENDAR, "📅 캘린더", 1))
                .addAction(action(SaveTargets.ALARM, "⏰ 알람", 2))
                .addAction(
                    if (prefs.thirdButtonBoth) action(SaveTargets.BOTH, "둘 다", 3) else action(SaveTargets.TODO, "✅ 할 일", 3),
                )
        }

        post(context, nid, builder.build())
    }

    /** 저장 실패 등 → 탭하면 확인 화면으로 */
    fun showResult(context: Context, data: ScheduleData, message: String) {
        val open = PendingIntent.getActivity(
            context,
            data.notificationId * 8,
            data.putInto(Intent(context, ConfirmActivity::class.java)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(context, CH_RESULT)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(data.title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        post(context, data.notificationId, n)
    }
}
