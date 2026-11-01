package com.sooparo.schedulecatcher

import android.content.Intent
import com.sooparo.schedulecatcher.core.MessageAnalyzer
import com.sooparo.schedulecatcher.core.ParsedSchedule
import com.sooparo.schedulecatcher.core.Recurrence
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

/** 화면/알림 사이에 주고받는 일정(또는 할 일) 데이터. */
data class ScheduleData(
    val id: String,
    val title: String,
    val start: LocalDateTime,
    val end: LocalDateTime?,
    val allDay: Boolean,
    val location: String?,
    val sourceText: String,
    val sourceApp: String?,
    val sender: String?,
    /** 반복 규칙 RRULE (예: FREQ=WEEKLY;BYDAY=TU) */
    val rrule: String? = null,
    /** 메시지가 '할 일(업무 요청)'로 감지됨 */
    val isTask: Boolean = false,
    /** false 면 날짜 정보가 없음 (마감일 없는 할 일) */
    val dateKnown: Boolean = true,
) {
    val notificationId: Int get() = id.hashCode() and 0x0FFFFFFF

    val recurrence: Recurrence? get() = Recurrence.fromRRule(rrule)

    fun putInto(intent: Intent): Intent = intent.apply {
        putExtra(X_ID, id)
        putExtra(X_TITLE, title)
        putExtra(X_START, start.toString())
        putExtra(X_END, end?.toString())
        putExtra(X_ALL_DAY, allDay)
        putExtra(X_LOCATION, location)
        putExtra(X_TEXT, sourceText)
        putExtra(X_APP, sourceApp)
        putExtra(X_SENDER, sender)
        putExtra(X_RRULE, rrule)
        putExtra(X_TASK, isTask)
        putExtra(X_DATE_KNOWN, dateKnown)
    }

    /** "카카오톡 · 김OO" */
    val sourceLabel: String?
        get() = listOfNotNull(sourceApp, sender).joinToString(" · ").ifEmpty { null }

    companion object {
        private const val X_ID = "schedule.id"
        private const val X_TITLE = "schedule.title"
        private const val X_START = "schedule.start"
        private const val X_END = "schedule.end"
        private const val X_ALL_DAY = "schedule.allDay"
        private const val X_LOCATION = "schedule.location"
        private const val X_TEXT = "schedule.text"
        private const val X_APP = "schedule.app"
        private const val X_SENDER = "schedule.sender"
        private const val X_RRULE = "schedule.rrule"
        private const val X_TASK = "schedule.task"
        private const val X_DATE_KNOWN = "schedule.dateKnown"

        fun from(parsed: ParsedSchedule, sourceApp: String?, sender: String?, id: String = UUID.randomUUID().toString()) =
            ScheduleData(
                id = id,
                title = parsed.title,
                start = parsed.start,
                end = parsed.end,
                allDay = parsed.allDay,
                location = parsed.location,
                sourceText = parsed.sourceText,
                sourceApp = sourceApp,
                sender = sender,
                rrule = parsed.recurrence?.toRRule(),
            )

        /** 메시지 분석 결과 → 화면용 데이터. 일정과 할 일이 함께 감지되면 할 일 제목을 우선한다. */
        fun from(detection: MessageAnalyzer.Detection, sourceApp: String?, sender: String?, id: String = UUID.randomUUID().toString()): ScheduleData? {
            val schedule = detection.schedule
            val todo = detection.todo
            if (schedule != null) {
                val base = from(schedule, sourceApp, sender, id)
                return if (todo != null) base.copy(title = todo.title, isTask = true) else base
            }
            if (todo == null) return null
            val due = todo.dueDate
            return ScheduleData(
                id = id,
                title = todo.title,
                start = (due ?: LocalDate.now()).let { d -> todo.dueTime?.let { d.atTime(it) } ?: d.atStartOfDay() },
                end = null,
                allDay = todo.dueTime == null,
                location = null,
                sourceText = todo.sourceText,
                sourceApp = sourceApp,
                sender = sender,
                isTask = true,
                dateKnown = due != null,
            )
        }

        fun fromIntent(intent: Intent?): ScheduleData? {
            intent ?: return null
            val id = intent.getStringExtra(X_ID) ?: return null
            val start = intent.getStringExtra(X_START)?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() } ?: return null
            return ScheduleData(
                id = id,
                title = intent.getStringExtra(X_TITLE).orEmpty(),
                start = start,
                end = intent.getStringExtra(X_END)?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() },
                allDay = intent.getBooleanExtra(X_ALL_DAY, false),
                location = intent.getStringExtra(X_LOCATION),
                sourceText = intent.getStringExtra(X_TEXT).orEmpty(),
                sourceApp = intent.getStringExtra(X_APP),
                sender = intent.getStringExtra(X_SENDER),
                rrule = intent.getStringExtra(X_RRULE),
                isTask = intent.getBooleanExtra(X_TASK, false),
                dateKnown = intent.getBooleanExtra(X_DATE_KNOWN, true),
            )
        }
    }
}

/** 저장 대상 조합: 캘린더 / 알람 / 할 일 */
data class SaveTargets(val calendar: Boolean, val alarm: Boolean, val todo: Boolean) {
    val isEmpty: Boolean get() = !calendar && !alarm && !todo

    fun encode(): String = buildString {
        if (calendar) append('c')
        if (alarm) append('a')
        if (todo) append('t')
    }

    companion object {
        val CALENDAR = SaveTargets(calendar = true, alarm = false, todo = false)
        val ALARM = SaveTargets(calendar = false, alarm = true, todo = false)
        val TODO = SaveTargets(calendar = false, alarm = false, todo = true)
        val BOTH = SaveTargets(calendar = true, alarm = true, todo = false)

        fun decode(s: String?): SaveTargets? =
            s?.let { SaveTargets('c' in it, 'a' in it, 't' in it) }?.takeUnless { it.isEmpty }
    }
}
