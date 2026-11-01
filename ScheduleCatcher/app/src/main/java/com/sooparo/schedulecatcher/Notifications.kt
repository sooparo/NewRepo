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
        nm.createNotificationChannels(listOf(detect, result, alarm))
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

    /** 메시지에서 감지한 일정 → [캘린더] [알람] [둘 다] 선택 알림 */
    fun showDetected(context: Context, data: ScheduleData) {
        val nid = data.notificationId
        val open = PendingIntent.getActivity(
            context,
            nid * 4,
            data.putInto(Intent(context, ConfirmActivity::class.java)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        fun action(target: SaveTarget, label: String, slot: Int): NotificationCompat.Action {
            val intent = data.putInto(Intent(context, QuickSaveActivity::class.java))
                .putExtra(QuickSaveActivity.EXTRA_TARGET, target.name)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
            val pi = PendingIntent.getActivity(
                context,
                nid * 4 + slot,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            return NotificationCompat.Action.Builder(0, label, pi).build()
        }

        val whenText = Formats.whenText(data)
        val details = buildString {
            append(whenText)
            data.location?.let { append("\n📍 ").append(it) }
            data.sourceLabel?.let { append("\n").append(it) }
            append("\n\n탭하면 내용을 수정할 수 있어요. 어디에 저장할까요?")
        }

        val n = NotificationCompat.Builder(context, CH_DETECT)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("일정 감지: ${data.title}")
            .setContentText("$whenText · 어디에 저장할까요?")
            .setStyle(NotificationCompat.BigTextStyle().bigText(details))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(open)
            .setAutoCancel(true)
            .addAction(action(SaveTarget.CALENDAR, "📅 캘린더", 1))
            .addAction(action(SaveTarget.ALARM, "⏰ 알람", 2))
            .addAction(action(SaveTarget.BOTH, "둘 다", 3))
            .build()

        post(context, nid, n)
    }

    /** 저장 실패 등 → 탭하면 확인 화면으로 */
    fun showResult(context: Context, data: ScheduleData, message: String) {
        val open = PendingIntent.getActivity(
            context,
            data.notificationId * 4,
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
