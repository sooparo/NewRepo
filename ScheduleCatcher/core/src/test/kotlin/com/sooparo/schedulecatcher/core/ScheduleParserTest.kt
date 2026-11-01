package com.sooparo.schedulecatcher.core

import org.junit.Test
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ScheduleParserTest {

    // 2026-11-04 (수) 10:00 기준
    private val now = LocalDateTime.of(2026, 11, 4, 10, 0)
    private val parser = ScheduleParser(now = { now })

    private fun parse(text: String) = assertNotNull(parser.parse(text), "일정으로 감지되어야 함: $text")

    @Test fun tomorrowAfternoonWithLocation() {
        val s = parse("내일 오후 3시 회의실 A에서 QA 회의 있습니다")
        assertEquals(LocalDateTime.of(2026, 11, 5, 15, 0), s.start)
        assertFalse(s.allDay)
        assertEquals("QA 회의", s.title)
        assertEquals("회의실 A", s.location)
    }

    @Test fun slashDateWithRange() {
        val s = parse("11/12(목) 14:00~16:00 방사선종양학과 집담회")
        assertEquals(LocalDateTime.of(2026, 11, 12, 14, 0), s.start)
        assertEquals(LocalDateTime.of(2026, 11, 12, 16, 0), s.end)
        assertEquals("방사선종양학과 집담회", s.title)
    }

    @Test fun nextWeekWeekdayMorning() {
        val s = parse("다음주 화요일 7시 30분 아침 컨퍼런스")
        assertEquals(LocalDateTime.of(2026, 11, 10, 7, 30), s.start)
        assertEquals("아침 컨퍼런스", s.title)
    }

    @Test fun weekdayEvening() {
        val s = parse("금요일 저녁 7시에 회식 어때요?")
        assertEquals(LocalDateTime.of(2026, 11, 6, 19, 0), s.start)
        assertEquals("회식", s.title)
    }

    @Test fun timeOnlyWithKeywordIsToday() {
        val s = parse("3시 회의")
        assertEquals(LocalDateTime.of(2026, 11, 4, 15, 0), s.start)
        assertEquals("회의", s.title)
    }

    @Test fun timeOnlyAlreadyPassedIsTomorrow() {
        val s = parse("9시 회의")
        assertEquals(LocalDateTime.of(2026, 11, 5, 9, 0), s.start)
    }

    @Test fun absoluteDateAllDay() {
        val s = parse("2026년 12월 24일 송년회")
        assertTrue(s.allDay)
        assertEquals(LocalDateTime.of(2026, 12, 24, 0, 0), s.start)
        assertEquals("송년회", s.title)
    }

    @Test fun koreanMonthDayHalfHour() {
        val s = parse("12월 3일 오전 10시 반 진료 예약")
        assertEquals(LocalDateTime.of(2026, 12, 3, 10, 30), s.start)
        assertEquals("진료 예약", s.title)
    }

    @Test fun noon() {
        val s = parse("모레 정오에 점심 약속")
        assertEquals(LocalDateTime.of(2026, 11, 6, 12, 0), s.start)
        assertEquals("점심 약속", s.title)
    }

    @Test fun relativeDaysAndPm() {
        val s = parse("3일 후 PM 2:30 화상회의")
        assertEquals(LocalDateTime.of(2026, 11, 7, 14, 30), s.start)
        assertEquals("화상회의", s.title)
    }

    @Test fun afternoonRangeInheritsMeridiem() {
        val s = parse("오후 2시~4시 교육")
        assertEquals(LocalDateTime.of(2026, 11, 4, 14, 0), s.start)
        assertEquals(LocalDateTime.of(2026, 11, 4, 16, 0), s.end)
    }

    @Test fun fromToKorean() {
        val s = parse("내일 2시부터 4시까지 세미나")
        assertEquals(LocalDateTime.of(2026, 11, 5, 14, 0), s.start)
        assertEquals(LocalDateTime.of(2026, 11, 5, 16, 0), s.end)
        assertEquals("세미나", s.title)
    }

    @Test fun durationIsNotATime() {
        val s = parse("내일 2시간 동안 회의")
        assertTrue(s.allDay)
        assertEquals(LocalDateTime.of(2026, 11, 5, 0, 0), s.start)
    }

    @Test fun pastMonthDayRollsToNextYear() {
        val s = parse("11월 3일 학회")
        assertEquals(LocalDateTime.of(2027, 11, 3, 0, 0), s.start)
    }

    @Test fun dayAfterTomorrowCompound() {
        val s = parse("내일모레 9시 미팅")
        assertEquals(LocalDateTime.of(2026, 11, 6, 9, 0), s.start)
    }

    @Test fun thisWeekWeekday() {
        val s = parse("이번주 금요일 오전 11시 QA")
        assertEquals(LocalDateTime.of(2026, 11, 6, 11, 0), s.start)
    }

    @Test fun nightTime() {
        val s = parse("오늘 밤 9시에 통화 가능하세요?")
        assertEquals(LocalDateTime.of(2026, 11, 4, 21, 0), s.start)
        assertEquals("통화", s.title)
    }

    @Test fun greetingAndMultipleSentences() {
        val s = parse("안녕하세요. 내일 오후 4시 IMRT QA 일정 잡혔습니다. 참고 부탁드립니다.")
        assertEquals(LocalDateTime.of(2026, 11, 5, 16, 0), s.start)
        assertEquals("IMRT QA 일정", s.title)
    }

    @Test fun locationLabel() {
        val s = parse("[교육 안내]\n일시: 11월 20일 13:30\n장소: 본관 3층 대강당")
        assertEquals(LocalDateTime.of(2026, 11, 20, 13, 30), s.start)
        assertEquals("본관 3층 대강당", s.location)
        assertEquals("교육 안내", s.title)
    }

    @Test fun ymdDashed() {
        val s = parse("2026-11-30 09:00 인증 평가")
        assertEquals(LocalDateTime.of(2026, 11, 30, 9, 0), s.start)
    }

    @Test fun subjectParticleRemoved() {
        assertEquals("미팅", parse("내일 3시에 미팅이 있습니다").title)
        assertEquals("휴가", parse("내일 휴가입니다").title)
    }

    // ---- 감지하지 않아야 하는 경우 ----------------------------------------------------------------

    @Test fun ignoresChitChat() {
        assertNull(parser.parse("오늘 너무 피곤하네요"))
        assertNull(parser.parse("ㅋㅋㅋ 알겠어"))
        assertNull(parser.parse(""))
    }

    @Test fun ignoresRatios() {
        assertNull(parser.parse("1:1 미팅 요청드립니다"))
    }

    @Test fun ignoresDoseFractions() {
        assertNull(parser.parse("1.8Gy 25회 치료 계획 확인 부탁드립니다"))
        assertNull(parser.parse("2.0 Gy x 30fx 치료 계획"))
    }

    @Test fun ignoresTimeWithoutDateOrKeyword() {
        assertNull(parser.parse("3시쯤 도착할 것 같아"))
    }

    @Test fun ignoresPastToday() {
        assertNull(parser.parse("오늘 8시 회의 잘 끝났습니다"))
    }
}
