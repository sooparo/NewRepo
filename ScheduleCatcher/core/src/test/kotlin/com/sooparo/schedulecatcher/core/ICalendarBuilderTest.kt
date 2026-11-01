package com.sooparo.schedulecatcher.core

import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ICalendarBuilderTest {

    private val stamp = Instant.parse("2026-11-04T01:00:00Z")

    @Test fun timedEventConvertsSeoulToUtc() {
        val ics = ICalendarBuilder.build(
            ICalendarBuilder.Event(
                uid = "abc",
                title = "QA 회의",
                start = LocalDateTime.of(2026, 11, 5, 15, 0),
                end = null,
                allDay = false,
                location = "회의실 A",
                reminderMinutesBefore = 30,
            ),
            stamp = stamp,
        )
        assertTrue("DTSTART:20261105T060000Z" in ics, ics)
        assertTrue("DTEND:20261105T070000Z" in ics, ics) // 기본 1시간
        assertTrue("SUMMARY:QA 회의" in ics)
        assertTrue("LOCATION:회의실 A" in ics)
        assertTrue("TRIGGER:-PT30M" in ics)
        assertTrue(ics.endsWith("END:VCALENDAR\r\n"))
    }

    @Test fun allDayEvent() {
        val ics = ICalendarBuilder.build(
            ICalendarBuilder.Event("x", "송년회", LocalDateTime.of(2026, 12, 24, 0, 0), null, allDay = true),
            stamp = stamp,
        )
        assertTrue("DTSTART;VALUE=DATE:20261224" in ics)
        assertTrue("DTEND;VALUE=DATE:20261225" in ics)
    }

    @Test fun escapesText() {
        assertEquals("a\\, b\\; c\\\\ d\\ne", ICalendarBuilder.escape("a, b; c\\ d\ne"))
    }

    @Test fun foldsLongLinesWithoutBreakingHangul() {
        val line = "DESCRIPTION:" + "가".repeat(60)
        val folded = ICalendarBuilder.fold(line)
        folded.split("\r\n").forEach { part ->
            assertTrue(part.toByteArray(Charsets.UTF_8).size <= 75, "line too long: $part")
        }
        assertEquals(line, folded.replace("\r\n ", ""))
    }
}
