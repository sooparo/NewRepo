package com.sooparo.schedulecatcher

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/** 매일 아침 오늘 일정과 할 일을 요약해서 알려준다 */
object BriefingScheduler {

    private const val REQUEST_CODE = 0x0B12

    fun schedule(context: Context) {
        val prefs = Prefs(context)
        val am = context.getSystemService(AlarmManager::class.java)
        val pi = pendingIntent(context)
        am.cancel(pi)
        if (!prefs.briefingEnabled) return

        val minute = prefs.briefingMinuteOfDay
        val now = LocalDateTime.now()
        var at = now.toLocalDate().atTime(LocalTime.of(minute / 60, minute % 60))
        if (!at.isAfter(now)) at = at.plusDays(1)
        val millis = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        try {
            if (AlarmScheduler.canScheduleExact(context)) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
            }
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
        }
    }

    private fun pendingIntent(context: Context) = PendingIntent.getBroadcast(
        context,
        REQUEST_CODE,
        Intent(context, BriefingReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** 오늘 브리핑 알림 표시. 보여줄 게 없으면 false */
    fun show(context: Context, force: Boolean = false): Boolean {
        val today = LocalDate.now()
        val events = Agenda.eventsOn(context, today)
        val todos = Agenda.todosForToday(context, today)
        if (events.isEmpty() && todos.isEmpty() && !force) return false

        val lines = buildList {
            events.forEach { e ->
                add("📅 " + (if (e.allDay) "종일" else Formats.time(e.at.toLocalTime())) + "  " + e.title)
            }
            todos.forEach { t ->
                add("✅ " + t.title + (t.dueDate?.let { "  (" + Formats.shortDue(it, today) + ")" } ?: ""))
            }
        }
        val title = "☀️ 오늘 일정 ${events.size}개 · 할 일 ${todos.size}개"
        val style = NotificationCompat.InboxStyle().setBigContentTitle(title)
        lines.take(8).forEach { style.addLine(it) }
        if (lines.size > 8) style.setSummaryText("외 ${lines.size - 8}개")
        if (lines.isEmpty()) style.addLine("오늘 등록된 일정과 할 일이 없어요.")

        val open = PendingIntent.getActivity(
            context, REQUEST_CODE, Intent(context, TasksActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(context, Notifications.CH_BRIEFING)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(lines.firstOrNull() ?: "오늘 등록된 일정과 할 일이 없어요.")
            .setStyle(style)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        Notifications.post(context, REQUEST_CODE, n)
        return true
    }
}

class BriefingReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        BriefingScheduler.show(context)
        BriefingScheduler.schedule(context) // 다음 날 예약
        WidgetUpdater.refresh(context) // 날짜가 바뀌었으니 위젯도 갱신
    }
}
