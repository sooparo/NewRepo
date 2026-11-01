package com.sooparo.schedulecatcher.core

import org.junit.Test
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PrivacyFilterTest {

    private val filter = PrivacyFilter()

    @Test fun registrationNumber() {
        assertEquals("******** CT sim 내일 3시", filter.redact("12345678 CT sim 내일 3시"))
        // 9자리 이상은 등록번호가 아님
        assertEquals("123456789", filter.redact("123456789"))
    }

    @Test fun rrnAndPhone() {
        assertEquals("주민 ******-*******", filter.redact("주민 900101-1234567"))
        assertEquals("연락처 ***-****-****", filter.redact("연락처 010-1234-5678"))
        assertEquals("***-****-****", filter.redact("02-123-4567"))
        assertEquals("***-****-****", filter.redact("01012345678"))
    }

    @Test fun namesWithTitles() {
        assertEquals("OOO 환자 내일 9시 CT sim", filter.redact("홍길동 환자 내일 9시 CT sim"))
        assertEquals("OOO님 동의서 확인", filter.redact("김OO님 동의서 확인"))
        assertEquals("OOO씨 연락 부탁", filter.redact("이영희씨 연락 부탁"))
        assertEquals("OOO환자분 예약", filter.redact("박민수환자분 예약"))
    }

    @Test fun keepsTitlesThatAreNotNames() {
        assertEquals("선생님 내일 회의", filter.redact("선생님 내일 회의"))
        assertEquals("외래 환자 많음", filter.redact("외래 환자 많음"))
        assertEquals("QA 담당자님 확인", filter.redact("QA 담당자님 확인"))
        assertEquals("과장님 결재", filter.redact("과장님 결재"))
    }

    @Test fun doesNotBreakDatesAndTimes() {
        val text = "2026-11-05 14:00~16:00 11/12(목) 오후 3시 10분"
        assertEquals(text, filter.redact(text))
    }

    @Test fun parserStillWorksAfterRedaction() {
        val now = LocalDateTime.of(2026, 11, 4, 10, 0)
        val parser = ScheduleParser(now = { now })
        val redacted = filter.redact("홍길동 환자(12345678) 내일 오전 9시 CT sim 예약")
        assertFalse("홍길동" in redacted)
        assertFalse("12345678" in redacted)
        val s = assertNotNull(parser.parse(redacted))
        assertEquals(LocalDateTime.of(2026, 11, 5, 9, 0), s.start)
        assertTrue("CT sim" in s.title, s.title)
        assertFalse("홍길동" in s.title)
    }
}
