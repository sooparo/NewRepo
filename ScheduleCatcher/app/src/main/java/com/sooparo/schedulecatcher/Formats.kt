package com.sooparo.schedulecatcher

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

object Formats {
    private val DATE = DateTimeFormatter.ofPattern("M월 d일 (E)", Locale.KOREAN)
    private val DATE_YEAR = DateTimeFormatter.ofPattern("yyyy년 M월 d일 (E)", Locale.KOREAN)
    private val TIME = DateTimeFormatter.ofPattern("a h:mm", Locale.KOREAN)

    fun date(d: LocalDate): String =
        if (d.year == LocalDate.now().year) DATE.format(d) else DATE_YEAR.format(d)

    fun time(t: LocalTime): String = TIME.format(t)

    fun dateTime(dt: LocalDateTime): String = "${date(dt.toLocalDate())} ${time(dt.toLocalTime())}"

    fun whenText(data: ScheduleData): String = buildString {
        if (!data.dateKnown) {
            append("마감일 없음")
            return@buildString
        }
        data.recurrence?.let { rec ->
            append(rec.label())
            if (!data.allDay) append(' ').append(time(data.start.toLocalTime()))
            append(" (첫 일정 ").append(date(data.start.toLocalDate())).append(')')
            return@buildString
        }
        append(date(data.start.toLocalDate()))
        if (data.allDay) {
            append(" 종일")
        } else {
            append(' ').append(time(data.start.toLocalTime()))
            data.end?.let { append(" ~ ").append(time(it.toLocalTime())) }
        }
    }

    fun leadText(minutes: Int): String = when {
        minutes == 0 -> "일정 시각에"
        minutes % 1440 == 0 -> "${minutes / 1440}일 전"
        minutes % 60 == 0 -> "${minutes / 60}시간 전"
        else -> "${minutes}분 전"
    }

    /** 위젯/브리핑용 짧은 표기: "오늘 15:00", "내일 종일", "11/10(화) 14:00" */
    fun shortWhen(dt: LocalDateTime, allDay: Boolean, today: LocalDate = LocalDate.now()): String {
        val d = dt.toLocalDate()
        val day = when (d) {
            today -> "오늘"
            today.plusDays(1) -> "내일"
            else -> SHORT_DATE.format(d)
        }
        return if (allDay) "$day 종일" else "$day ${SHORT_TIME.format(dt)}"
    }

    fun shortDue(d: LocalDate, today: LocalDate = LocalDate.now()): String = when {
        d.isBefore(today) -> "기한 지남"
        d == today -> "오늘까지"
        d == today.plusDays(1) -> "내일까지"
        else -> "D-${java.time.temporal.ChronoUnit.DAYS.between(today, d)}"
    }

    private val SHORT_DATE = DateTimeFormatter.ofPattern("M/d(E)", Locale.KOREAN)
    private val SHORT_TIME = DateTimeFormatter.ofPattern("HH:mm")

    val LEAD_OPTIONS = listOf(0, 10, 30, 60, 120, 1440)
}
