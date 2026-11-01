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

    val LEAD_OPTIONS = listOf(0, 10, 30, 60, 120, 1440)
}
