package com.sooparo.schedulecatcher

import android.content.Intent
import com.sooparo.schedulecatcher.core.ParsedSchedule
import java.time.LocalDateTime
import java.util.UUID

/** 화면/알림 사이에 주고받는 일정 데이터. */
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
) {
    val notificationId: Int get() = id.hashCode() and 0x0FFFFFFF

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

        fun from(parsed: ParsedSchedule, sourceApp: String?, sender: String?) = ScheduleData(
            id = UUID.randomUUID().toString(),
            title = parsed.title,
            start = parsed.start,
            end = parsed.end,
            allDay = parsed.allDay,
            location = parsed.location,
            sourceText = parsed.sourceText,
            sourceApp = sourceApp,
            sender = sender,
        )

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
            )
        }
    }
}

enum class SaveTarget(val calendar: Boolean, val alarm: Boolean, val label: String) {
    CALENDAR(true, false, "네이버 캘린더"),
    ALARM(false, true, "알람"),
    BOTH(true, true, "캘린더 + 알람"),
}
