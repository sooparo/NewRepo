package com.sooparo.schedulecatcher.core

import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NewFeaturesTest {

    // 2026-11-04 (수) 10:00 기준
    private val now = LocalDateTime.of(2026, 11, 4, 10, 0)
    private val parser = ScheduleParser(now = { now })
    private val todo = TodoDetector(parser)

    // ---- 반복 일정 ------------------------------------------------------------------------------

    @Test fun weeklyRecurrence() {
        val s = assertNotNull(parser.parse("매주 화요일 오후 2시 집담회"))
        assertEquals(LocalDateTime.of(2026, 11, 10, 14, 0), s.start)
        assertEquals("FREQ=WEEKLY;BYDAY=TU", s.recurrence?.toRRule())
        assertEquals("매주 화요일", s.recurrence?.label())
        assertEquals("집담회", s.title)
    }

    @Test fun biweeklyMultipleDays() {
        val s = assertNotNull(parser.parse("격주 월,목요일 7시 30분 컨퍼런스"))
        assertEquals(LocalDateTime.of(2026, 11, 5, 7, 30), s.start) // 이번 주 목요일이 첫 일정
        assertEquals("FREQ=WEEKLY;INTERVAL=2;BYDAY=MO,TH", s.recurrence?.toRRule())
    }

    @Test fun weekdaysDaily() {
        val s = assertNotNull(parser.parse("평일마다 아침 8시 QA"))
        assertEquals(LocalDateTime.of(2026, 11, 5, 8, 0), s.start) // 오늘 8시는 지남 → 내일(목)
        assertEquals("평일 매일", s.recurrence?.label())
    }

    @Test fun monthly() {
        val s = assertNotNull(parser.parse("매월 3일 오전 9시 장비 점검"))
        assertEquals(LocalDateTime.of(2026, 12, 3, 9, 0), s.start)
        assertEquals("FREQ=MONTHLY;BYMONTHDAY=3", s.recurrence?.toRRule())
        assertEquals("장비 점검", s.title)
    }

    @Test fun dailyWithTime() {
        val s = assertNotNull(parser.parse("매일 오후 5시 회의"))
        assertEquals(LocalDateTime.of(2026, 11, 4, 17, 0), s.start)
        assertEquals(Recurrence.Freq.DAILY, s.recurrence?.freq)
    }

    @Test fun recurrenceNextAfterAndRoundTrip() {
        val r = Recurrence(Recurrence.Freq.WEEKLY, 2, setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY))
        val anchor = LocalDateTime.of(2026, 11, 5, 7, 30) // 목
        assertEquals(LocalDateTime.of(2026, 11, 16, 7, 30), r.nextAfter(anchor, anchor)) // 다음 주 건너뛰고 월
        assertEquals(r, Recurrence.fromRRule(r.toRRule()))
        assertEquals(r, Recurrence.fromRRule("RRULE:" + r.toRRule()))
    }

    @Test fun monthlySkipsShortMonths() {
        val r = Recurrence(Recurrence.Freq.MONTHLY, byMonthDay = 31)
        val anchor = LocalDateTime.of(2026, 10, 31, 9, 0)
        assertEquals(LocalDateTime.of(2026, 12, 31, 9, 0), r.nextAfter(anchor, anchor))
    }

    @Test fun icsHasRRule() {
        val ics = ICalendarBuilder.build(
            ICalendarBuilder.Event("u", "집담회", LocalDateTime.of(2026, 11, 10, 14, 0), null, false, rrule = "FREQ=WEEKLY;BYDAY=TU"),
        )
        assertTrue("RRULE:FREQ=WEEKLY;BYDAY=TU\r\n" in ics)
    }

    // ---- 할 일 ----------------------------------------------------------------------------------

    @Test fun todoWithoutDate() {
        val t = assertNotNull(todo.detect("선생님 치료계획 검토 부탁드립니다."))
        assertEquals("치료계획 검토", t.title)
        assertNull(t.dueDate)
    }

    @Test fun todoWithDue() {
        val t = assertNotNull(todo.detect("11/6까지 IMRT QA 결과 제출해 주세요"))
        assertEquals("IMRT QA 결과 제출", t.title)
        assertEquals(LocalDate.of(2026, 11, 6), t.dueDate)
    }

    @Test fun todoVerbStems() {
        assertEquals("자료 보내기", assertNotNull(todo.detect("내일 오전까지 자료 보내주세요")).title)
        assertEquals("선량 확인", assertNotNull(todo.detect("선량 확인해 주시기 바랍니다")).title)
    }

    @Test fun todoDueTime() {
        val t = assertNotNull(todo.detect("오늘 5시까지 동의서 서명 받아야 합니다"))
        assertEquals(LocalDate.of(2026, 11, 4), t.dueDate)
        assertEquals(LocalTime.of(17, 0), t.dueTime)
        assertEquals("동의서 서명 받기", t.title)
    }

    @Test fun notTodo() {
        assertNull(todo.detect("감사합니다 확인했습니다"))
        assertNull(todo.detect("점심 뭐 먹을까요"))
    }

    // ---- 연속 메시지 합치기 ---------------------------------------------------------------------

    private var clockMs = 0L
    private fun analyzer() = MessageAnalyzer(parser, todo, clock = { clockMs })

    @Test fun mergesFollowUpTitle() {
        val a = analyzer()
        val first = assertNotNull(a.analyze("kakao|김선생님", "내일 3시에 시간 되세요?"))
        assertEquals(LocalDateTime.of(2026, 11, 5, 15, 0), first.schedule?.start)
        assertFalse(first.updatesPrevious)

        clockMs += 30_000
        val second = assertNotNull(a.analyze("kakao|김선생님", "회의실에서 QA 회의 하려고요"))
        assertTrue(second.updatesPrevious)
        assertEquals(LocalDateTime.of(2026, 11, 5, 15, 0), second.schedule?.start)
        assertEquals("QA 회의", second.schedule?.title)
        assertEquals("회의실", second.schedule?.location)
    }

    @Test fun keywordThenTime() {
        val a = analyzer()
        assertNull(a.analyze("works|팀방", "다음 QA 회의 언제 할까요"))
        clockMs += 60_000
        val d = assertNotNull(a.analyze("works|팀방", "내일 4시 어떠세요"))
        assertEquals(LocalDateTime.of(2026, 11, 5, 16, 0), d.schedule?.start)
        assertTrue(d.schedule!!.title.contains("QA 회의"), d.schedule!!.title)
    }

    @Test fun doesNotMergeAcrossConversationsOrAfterWindow() {
        val a = analyzer()
        a.analyze("kakao|A", "내일 3시 가능하세요?")
        assertNull(a.analyze("kakao|B", "QA 회의 하려고요")?.schedule)

        clockMs += 10 * 60_000
        assertNull(a.analyze("kakao|A", "회의실에서 QA 회의 하려고요")?.schedule)
    }

    @Test fun chatterDoesNotRepeatNotification() {
        val a = analyzer()
        a.analyze("kakao|A", "내일 3시 QA 회의")
        clockMs += 10_000
        assertNull(a.analyze("kakao|A", "넵 알겠습니다"))
    }

    @Test fun todoThroughAnalyzer() {
        val d = assertNotNull(analyzer().analyze("sms|x", "동의서 스캔해서 업로드 부탁드려요"))
        assertNull(d.schedule)
        assertEquals("동의서 스캔해서 업로드", d.todo?.title)
    }
}
