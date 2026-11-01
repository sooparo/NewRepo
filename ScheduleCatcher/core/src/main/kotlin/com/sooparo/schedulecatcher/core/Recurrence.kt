package com.sooparo.schedulecatcher.core

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/**
 * 반복 규칙 (iCalendar RRULE 의 일부: DAILY / WEEKLY / MONTHLY, INTERVAL, BYDAY, BYMONTHDAY).
 */
data class Recurrence(
    val freq: Freq,
    val interval: Int = 1,
    /** WEEKLY: 반복 요일 (비어 있으면 시작일의 요일). DAILY: 비어있지 않으면 해당 요일만 (예: 평일) */
    val byDays: Set<DayOfWeek> = emptySet(),
    /** MONTHLY: 반복 날짜 (null 이면 시작일의 날짜) */
    val byMonthDay: Int? = null,
) {
    enum class Freq { DAILY, WEEKLY, MONTHLY }

    /** "FREQ=WEEKLY;INTERVAL=2;BYDAY=TU" */
    fun toRRule(): String = buildList {
        add("FREQ=${freq.name}")
        if (interval > 1) add("INTERVAL=$interval")
        if (byDays.isNotEmpty()) add("BYDAY=" + byDays.sorted().joinToString(",") { RRULE_DAYS.getValue(it) })
        byMonthDay?.let { add("BYMONTHDAY=$it") }
    }.joinToString(";")

    /** "매주 화요일", "격주 월·수요일", "평일 매일", "매월 5일" */
    fun label(): String = when (freq) {
        Freq.DAILY -> when {
            byDays == WEEKDAYS -> "평일 매일"
            byDays.isNotEmpty() -> "매주 ${dayNames(byDays)}요일"
            interval > 1 -> "${interval}일마다"
            else -> "매일"
        }
        Freq.WEEKLY -> {
            val prefix = when (interval) {
                1 -> "매주"
                2 -> "격주"
                else -> "${interval}주마다"
            }
            if (byDays.isEmpty()) prefix else "$prefix ${dayNames(byDays)}요일"
        }
        Freq.MONTHLY -> {
            val prefix = if (interval > 1) "${interval}개월마다" else "매월"
            byMonthDay?.let { "$prefix ${it}일" } ?: prefix
        }
    }

    /** [anchor](첫 일정)를 기준으로 [date] 가 반복에 해당하는지 */
    fun matches(anchor: LocalDate, date: LocalDate): Boolean {
        if (date.isBefore(anchor)) return false
        return when (freq) {
            Freq.DAILY -> {
                val days = ChronoUnit.DAYS.between(anchor, date)
                days % interval == 0L && (byDays.isEmpty() || date.dayOfWeek in byDays)
            }
            Freq.WEEKLY -> {
                val weeks = ChronoUnit.WEEKS.between(monday(anchor), monday(date))
                val days = byDays.ifEmpty { setOf(anchor.dayOfWeek) }
                weeks % interval == 0L && date.dayOfWeek in days
            }
            Freq.MONTHLY -> {
                val months = ChronoUnit.MONTHS.between(anchor.withDayOfMonth(1), date.withDayOfMonth(1))
                months % interval == 0L && date.dayOfMonth == (byMonthDay ?: anchor.dayOfMonth)
            }
        }
    }

    /** [from] 이상인 첫 반복 날짜 (최대 2년 탐색) */
    fun firstOnOrAfter(anchor: LocalDate, from: LocalDate): LocalDate? {
        var d = if (from.isBefore(anchor)) anchor else from
        repeat(MAX_SEARCH_DAYS) {
            if (matches(anchor, d)) return d
            d = d.plusDays(1)
        }
        return null
    }

    /** [after] 보다 뒤에 오는 다음 일정 시각 (시작 시각의 시:분 유지) */
    fun nextAfter(anchor: LocalDateTime, after: LocalDateTime): LocalDateTime? {
        val time = anchor.toLocalTime()
        var d = after.toLocalDate()
        repeat(MAX_SEARCH_DAYS) {
            val candidate = d.atTime(time)
            if (candidate.isAfter(after) && matches(anchor.toLocalDate(), d)) return candidate
            d = d.plusDays(1)
        }
        return null
    }

    companion object {
        val WEEKDAYS: Set<DayOfWeek> = setOf(
            DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY,
        )

        private const val MAX_SEARCH_DAYS = 800
        private const val KOREAN_DAYS = "월화수목금토일"

        private val RRULE_DAYS = mapOf(
            DayOfWeek.MONDAY to "MO", DayOfWeek.TUESDAY to "TU", DayOfWeek.WEDNESDAY to "WE",
            DayOfWeek.THURSDAY to "TH", DayOfWeek.FRIDAY to "FR", DayOfWeek.SATURDAY to "SA", DayOfWeek.SUNDAY to "SU",
        )

        private fun monday(d: LocalDate) = d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

        private fun dayNames(days: Set<DayOfWeek>) =
            days.sorted().joinToString("·") { KOREAN_DAYS[it.value - 1].toString() }

        fun koreanDay(c: Char): DayOfWeek? = KOREAN_DAYS.indexOf(c).takeIf { it >= 0 }?.let { DayOfWeek.of(it + 1) }

        /** [toRRule] 결과를 다시 읽는다. 지원하지 않는 규칙이면 null. */
        fun fromRRule(rrule: String?): Recurrence? {
            if (rrule.isNullOrBlank()) return null
            val parts = rrule.removePrefix("RRULE:").split(';').mapNotNull {
                val kv = it.split('=', limit = 2)
                if (kv.size == 2) kv[0].uppercase() to kv[1] else null
            }.toMap()
            val freq = parts["FREQ"]?.let { f -> runCatching { Freq.valueOf(f.uppercase()) }.getOrNull() } ?: return null
            val reverse = RRULE_DAYS.entries.associate { (k, v) -> v to k }
            return Recurrence(
                freq = freq,
                interval = parts["INTERVAL"]?.toIntOrNull()?.coerceAtLeast(1) ?: 1,
                byDays = parts["BYDAY"]?.split(',')?.mapNotNull { reverse[it.trim().uppercase().takeLast(2)] }?.toSet().orEmpty(),
                byMonthDay = parts["BYMONTHDAY"]?.toIntOrNull(),
            )
        }
    }
}
