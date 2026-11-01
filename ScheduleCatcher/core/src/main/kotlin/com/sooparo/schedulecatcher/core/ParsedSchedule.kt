package com.sooparo.schedulecatcher.core

import java.time.LocalDateTime

/**
 * 메시지에서 추출한 일정 정보.
 *
 * @property start 시작 시각. [allDay] 가 true 이면 시간 부분은 00:00 이다.
 * @property end   종료 시각. 메시지에 종료 시각이 없으면 null.
 */
data class ParsedSchedule(
    val title: String,
    val start: LocalDateTime,
    val end: LocalDateTime?,
    val allDay: Boolean,
    val location: String?,
    val sourceText: String,
    val matchedKeyword: String?,
    /** "매주 화요일" 같은 반복 표현이 있으면 반복 규칙 */
    val recurrence: Recurrence? = null,
)
