package com.sooparo.schedulecatcher.core

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** CalDAV 서버(네이버 캘린더)에 PUT 할 iCalendar(RFC 5545) 문서를 만든다. */
object ICalendarBuilder {

    private val UTC_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC)
    private val DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd")

    data class Event(
        val uid: String,
        val title: String,
        val start: LocalDateTime,
        /** null 이면 시간 일정은 1시간, 종일 일정은 하루로 처리 */
        val end: LocalDateTime?,
        val allDay: Boolean,
        val location: String? = null,
        val description: String? = null,
        /** 캘린더 자체 알림(분 단위, 일정 시작 전). null 이면 알림 없음 */
        val reminderMinutesBefore: Int? = null,
    )

    fun build(event: Event, zone: ZoneId = ZoneId.of("Asia/Seoul"), stamp: Instant = Instant.now()): String {
        val lines = mutableListOf(
            "BEGIN:VCALENDAR",
            "VERSION:2.0",
            "PRODID:-//sooparo//ScheduleCatcher//KO",
            "CALSCALE:GREGORIAN",
            "BEGIN:VEVENT",
            "UID:${event.uid}",
            "DTSTAMP:${UTC_FORMAT.format(stamp)}",
        )

        if (event.allDay) {
            val startDate = event.start.toLocalDate()
            val endDate = event.end?.toLocalDate()?.takeIf { it.isAfter(startDate) } ?: startDate.plusDays(1)
            lines += "DTSTART;VALUE=DATE:${DATE_FORMAT.format(startDate)}"
            lines += "DTEND;VALUE=DATE:${DATE_FORMAT.format(endDate)}"
        } else {
            val end = event.end?.takeIf { it.isAfter(event.start) } ?: event.start.plusHours(1)
            lines += "DTSTART:${UTC_FORMAT.format(event.start.atZone(zone).toInstant())}"
            lines += "DTEND:${UTC_FORMAT.format(end.atZone(zone).toInstant())}"
        }

        lines += "SUMMARY:${escape(event.title)}"
        event.location?.takeIf { it.isNotBlank() }?.let { lines += "LOCATION:${escape(it)}" }
        event.description?.takeIf { it.isNotBlank() }?.let { lines += "DESCRIPTION:${escape(it)}" }

        event.reminderMinutesBefore?.let { minutes ->
            lines += "BEGIN:VALARM"
            lines += "ACTION:DISPLAY"
            lines += "DESCRIPTION:${escape(event.title)}"
            lines += "TRIGGER:-PT${minutes.coerceAtLeast(0)}M"
            lines += "END:VALARM"
        }

        lines += "END:VEVENT"
        lines += "END:VCALENDAR"

        return lines.joinToString(separator = "\r\n", postfix = "\r\n") { fold(it) }
    }

    /** RFC 5545 3.3.11 TEXT 이스케이프 */
    fun escape(value: String): String =
        value.replace("\\", "\\\\")
            .replace(";", "\\;")
            .replace(",", "\\,")
            .replace("\r\n", "\\n")
            .replace("\n", "\\n")
            .replace("\r", "\\n")

    /** RFC 5545 3.1 75옥텟 줄 접기. UTF-8 멀티바이트 문자(한글)를 중간에서 자르지 않는다. */
    fun fold(line: String): String {
        val out = StringBuilder()
        var octets = 0
        var limit = 75
        var i = 0
        while (i < line.length) {
            val cp = line.codePointAt(i)
            val chars = Character.charCount(cp)
            val size = String(Character.toChars(cp)).toByteArray(Charsets.UTF_8).size
            if (octets + size > limit) {
                out.append("\r\n ")
                octets = 0
                limit = 74 // 이어지는 줄은 선행 공백 1옥텟 포함 75
            }
            out.append(line, i, i + chars)
            octets += size
            i += chars
        }
        return out.toString()
    }
}
