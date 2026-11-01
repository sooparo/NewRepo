package com.sooparo.schedulecatcher.core

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters

/**
 * 한국어 메시지(문자, 카카오톡, 네이버 웍스 등)에서 일정(날짜/시간/제목/장소)을 추출한다.
 *
 * 모든 처리는 기기 안에서만 이루어지며 외부로 메시지 원문을 보내지 않는다.
 *
 * 감지 규칙
 *  - 시간 + (날짜 또는 일정 키워드)  → 일정으로 판단
 *  - 날짜 + 일정 키워드 (시간 없음)   → 종일 일정으로 판단
 *  - 그 외                           → null (일정 아님)
 *
 * 오전/오후 표기가 없는 1~6시는 오후로 간주한다(예: "3시 회의" → 15:00).
 * 7시 이후는 표기 그대로 사용한다(병원 아침 컨퍼런스 "7시 30분" → 07:30).
 */
class ScheduleParser(
    private val now: () -> LocalDateTime = { LocalDateTime.now() },
    private val keywords: List<String> = DEFAULT_KEYWORDS,
) {

    private data class Span(val start: Int, val end: Int) {
        fun overlaps(other: Span) = start < other.end && other.start < end
        val length get() = end - start
    }

    private data class DateHit(val span: Span, val date: LocalDate)

    private data class TimeHit(val span: Span, val hour: Int, val minute: Int, val meridiem: String?)

    fun parse(text: String): ParsedSchedule? {
        val src = normalize(text)
        if (src.isBlank()) return null

        val current = now()
        val today = current.toLocalDate()

        val dateHit = findDates(src, today).firstOrNull()
        val times = findTimes(src).filter { t -> dateHit == null || !t.span.overlaps(dateHit.span) }
        val keyword = findKeyword(src)

        val startTime = times.firstOrNull()
        when {
            startTime != null && (dateHit != null || keyword != null) -> Unit
            startTime == null && dateHit != null && keyword != null -> Unit
            else -> return null
        }

        val allDay = startTime == null
        val startHour = startTime?.let { to24(it.hour, it.meridiem) }
        if (startTime != null && startHour == null) return null

        val startDateTime: LocalDateTime = if (allDay) {
            dateHit!!.date.atStartOfDay()
        } else {
            val lt = LocalTime.of(startHour!!, startTime!!.minute)
            when {
                dateHit != null -> dateHit.date.atTime(lt)
                // 날짜 없이 시간만 있으면 오늘, 이미 지났으면 내일
                lt.isAfter(current.toLocalTime()) -> today.atTime(lt)
                else -> today.plusDays(1).atTime(lt)
            }
        }

        // 이미 지나간 일정은 저장 대상이 아니다.
        if (!allDay && startDateTime.isBefore(current.minusMinutes(30))) return null
        if (allDay && startDateTime.toLocalDate().isBefore(today)) return null

        val endDateTime = if (startTime != null) {
            findEndTime(src, startTime, times, startHour!!)?.let { startDateTime.toLocalDate().atTime(it) }
        } else {
            null
        }

        val removeSpans = buildList {
            dateHit?.let { add(it.span) }
            addAll(times.map { it.span })
        }
        val location = findLocation(src, removeSpans)
        val anchor = dateHit?.span?.start ?: startTime!!.span.start
        val title = buildTitle(src, anchor, removeSpans + listOfNotNull(location?.second), keyword)

        return ParsedSchedule(
            title = title,
            start = startDateTime,
            end = endDateTime,
            allDay = allDay,
            location = location?.first,
            sourceText = text,
            matchedKeyword = keyword,
        )
    }

    // ---------------------------------------------------------------------------------------------
    // 정규화
    // ---------------------------------------------------------------------------------------------

    private fun normalize(text: String): String =
        text.replace('：', ':')
            .replace('～', '~')
            .replace('〜', '~')
            .replace('\r', '\n')
            .replace(Regex("[\\t\\u00A0\\u3000]"), " ")
            .replace(Regex(" {2,}"), " ")

    // ---------------------------------------------------------------------------------------------
    // 날짜
    // ---------------------------------------------------------------------------------------------

    private fun findDates(src: String, today: LocalDate): List<DateHit> {
        val hits = mutableListOf<DateHit>()

        YMD.findAll(src).forEach { m ->
            val (y, mo, d) = m.destructured
            safeDate(y.toInt(), mo.toInt(), d.toInt())?.let { hits += DateHit(m.span(), it) }
        }
        MD_KO.findAll(src).forEach { m ->
            val (mo, d) = m.destructured
            inferYear(mo.toInt(), d.toInt(), today)?.let { hits += DateHit(m.span(), it) }
        }
        MD_NUM.findAll(src).forEach { m ->
            val (mo, d) = m.destructured
            inferYear(mo.toInt(), d.toInt(), today)?.let { hits += DateHit(m.span(), it) }
        }
        DAY_ONLY.findAll(src).forEach { m ->
            val d = m.groupValues[1].toInt()
            inferMonth(d, today)?.let { hits += DateHit(m.span(), it) }
        }
        AFTER_N.findAll(src).forEach { m ->
            val n = m.groupValues[1].toLong()
            val date = if (m.groupValues[2] == "주") today.plusWeeks(n) else today.plusDays(n)
            hits += DateHit(m.span(), date)
        }
        REL_DAY.findAll(src).forEach { m ->
            val word = m.value.replace(" ", "")
            val offset = when (word) {
                "오늘", "금일" -> 0L
                "내일", "명일" -> 1L
                "모레", "내일모레" -> 2L
                "글피" -> 3L
                else -> return@forEach
            }
            hits += DateHit(m.span(), today.plusDays(offset))
        }
        WEEKDAY.findAll(src).forEach { m ->
            val prefix = m.groupValues[1].replace(" ", "")
            val idx = WEEKDAY_CHARS.indexOf(m.groupValues[2])
            if (idx < 0) return@forEach
            val dow = DayOfWeek.of(idx + 1)
            val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            val date = when (prefix) {
                "이번주", "금주" -> monday.plusDays(idx.toLong()).let { if (it.isBefore(today)) it.plusWeeks(1) else it }
                "다음주", "담주", "차주" -> monday.plusWeeks(1).plusDays(idx.toLong())
                "다다음주" -> monday.plusWeeks(2).plusDays(idx.toLong())
                else -> today.with(TemporalAdjusters.nextOrSame(dow))
            }
            hits += DateHit(m.span(), date)
        }

        return pickNonOverlapping(hits) { it.span }
    }

    private fun safeDate(y: Int, m: Int, d: Int): LocalDate? =
        runCatching { LocalDate.of(y, m, d) }.getOrNull()

    /** 연도가 없는 월/일: 올해 기준으로 이미 지났으면 내년. */
    private fun inferYear(m: Int, d: Int, today: LocalDate): LocalDate? {
        val thisYear = safeDate(today.year, m, d) ?: return safeDate(today.year + 1, m, d)
        return if (thisYear.isBefore(today)) safeDate(today.year + 1, m, d) else thisYear
    }

    /** 일(日)만 있는 경우: 이번 달 기준으로 이미 지났으면 다음 달. */
    private fun inferMonth(d: Int, today: LocalDate): LocalDate? {
        if (d !in 1..31) return null
        val thisMonth = safeDate(today.year, today.monthValue, d)
        if (thisMonth != null && !thisMonth.isBefore(today)) return thisMonth
        var probe = today.withDayOfMonth(1).plusMonths(1)
        repeat(2) {
            safeDate(probe.year, probe.monthValue, d)?.let { return it }
            probe = probe.plusMonths(1)
        }
        return null
    }

    // ---------------------------------------------------------------------------------------------
    // 시간
    // ---------------------------------------------------------------------------------------------

    private fun findTimes(src: String): List<TimeHit> {
        val hits = mutableListOf<TimeHit>()

        TIME_KO.findAll(src).forEach { m ->
            val meridiem = m.groupValues[1].ifEmpty { null }
            val hour = m.groupValues[2].toInt()
            val minute = when {
                m.groupValues[3].isNotEmpty() -> m.groupValues[3].toInt()
                m.groupValues[4].isNotEmpty() -> 30
                else -> 0
            }
            if (hour in 0..24 && minute in 0..59) hits += TimeHit(m.span(), hour, minute, meridiem)
        }
        TIME_COLON.findAll(src).forEach { m ->
            val meridiem = m.groupValues[1].ifEmpty { null } ?: m.groupValues[4].ifEmpty { null }
            val hour = m.groupValues[2].toInt()
            val minute = m.groupValues[3].toInt()
            if (hour in 0..24 && minute in 0..59) hits += TimeHit(m.span(), hour, minute, meridiem)
        }
        TIME_AMPM.findAll(src).forEach { m ->
            val hour = m.groupValues[1].toInt()
            if (hour in 1..12) hits += TimeHit(m.span(), hour, 0, m.groupValues[2])
        }
        NOON.findAll(src).forEach { m -> hits += TimeHit(m.span(), 12, 0, "정오") }

        return pickNonOverlapping(hits) { it.span }
    }

    /** 오전/오후 등을 반영해 0~23 시로 변환한다. */
    private fun to24(hour: Int, meridiem: String?): Int? {
        val h = when (meridiem?.lowercase()) {
            "오후", "저녁", "밤", "pm" -> if (hour in 1..11) hour + 12 else hour
            "낮", "점심" -> if (hour in 1..5) hour + 12 else hour
            "오전", "아침", "새벽", "am" -> if (hour == 12) 0 else hour
            "정오" -> 12
            else -> if (hour in 1..6) hour + 12 else hour
        }
        return when {
            h == 24 -> 0
            h in 0..23 -> h
            else -> null
        }
    }

    /** "14:00~16:00", "오후 2시~4시", "2시부터 4시까지" 형태의 종료 시각. */
    private fun findEndTime(src: String, start: TimeHit, times: List<TimeHit>, startHour: Int): LocalTime? {
        val next = times.firstOrNull { it.span.start >= start.span.end } ?: return null
        val gap = src.substring(start.span.end, next.span.start)
        if (!RANGE_GAP.matches(gap)) return null

        var endHour = to24(next.hour, next.meridiem ?: start.meridiem) ?: return null
        if (next.meridiem == null && endHour <= startHour && endHour + 12 <= 23) endHour += 12
        val end = LocalTime.of(endHour, next.minute)
        return if (end.isAfter(LocalTime.of(startHour, start.minute))) end else null
    }

    // ---------------------------------------------------------------------------------------------
    // 키워드 / 장소 / 제목
    // ---------------------------------------------------------------------------------------------

    private fun findKeyword(src: String): String? {
        val lower = src.lowercase()
        return keywords
            .map { it to lower.indexOf(it.lowercase()) }
            .filter { it.second >= 0 }
            .minByOrNull { it.second }
            ?.first
    }

    /** @return (장소 문자열, 제목에서 지울 범위) */
    private fun findLocation(src: String, excluded: List<Span>): Pair<String, Span>? {
        LOC_LABEL.find(src)?.let { m ->
            val value = m.groupValues[1].trim().take(40)
            if (value.isNotEmpty()) return value to m.span()
        }
        LOC_AT.findAll(src).forEach { m ->
            val value = m.groupValues[1].trim()
            val span = m.span()
            if (value.isEmpty() || value.all { it.isDigit() }) return@forEach
            if (excluded.any { it.overlaps(span) }) return@forEach
            return value to span
        }
        return null
    }

    private fun buildTitle(src: String, anchor: Int, removeSpans: List<Span>, keyword: String?): String {
        val segment = sentenceAround(src, anchor)
        val fromAnchor = cleanTitle(src, segment, removeSpans)
        if (fromAnchor.isNotEmpty()) return fromAnchor

        // 날짜/시간만 있는 줄이면, 키워드가 들어있는 다른 줄을 제목으로 사용
        if (keyword != null) {
            val idx = src.lowercase().indexOf(keyword.lowercase())
            if (idx >= 0) {
                val alt = cleanTitle(src, sentenceAround(src, idx), removeSpans)
                if (alt.isNotEmpty()) return alt
            }
        }
        return keyword ?: "새 일정"
    }

    private fun sentenceAround(src: String, index: Int): Span {
        var start = 0
        var end = src.length
        SENTENCE_BOUNDARY.findAll(src).forEach { m ->
            if (m.range.last < index) start = m.range.last + 1
            else if (m.range.first >= index && end == src.length) end = m.range.first + if (m.value == "\n") 0 else 1
        }
        return Span(start, end)
    }

    private fun cleanTitle(src: String, segment: Span, removeSpans: List<Span>): String {
        val sb = StringBuilder()
        for (i in segment.start until segment.end) {
            sb.append(if (removeSpans.any { i >= it.start && i < it.end }) ' ' else src[i])
        }
        var s = sb.toString()
            .replace(WEEKDAY_PAREN, " ")
            .replace(GREETING, " ")
            .replace(FIELD_LABEL, " ")

        s = s.split(Regex("\\s+"))
            .filter { it.isNotEmpty() && it !in PARTICLE_TOKENS }
            .joinToString(" ")

        var strippedEnding = false
        repeat(3) {
            val m = ENDINGS.find(s)
            if (m != null && m.range.first > 0) {
                s = s.substring(0, m.range.first)
                strippedEnding = true
            }
        }
        if (strippedEnding) {
            // "회의가 있습니다" → "회의" (단, "휴가" 처럼 두 글자 단어는 유지)
            val m = SUBJECT_PARTICLE.find(s)
            if (m != null) s = s.substring(0, m.range.first + m.groupValues[1].length)
        }

        s = s.trim().trim('.', ',', '!', '?', '~', '·', ':', ';', '-', ' ')
        if (s.startsWith("[") && s.endsWith("]")) s = s.substring(1, s.length - 1).trim()
        return if (s.length > 40) s.take(40).trimEnd() + "…" else s
    }

    private fun MatchResult.span() = Span(range.first, range.last + 1)

    /** 겹치는 매치 중 먼저 시작하고(같으면 더 긴) 것을 남긴다. 결과는 위치 순. */
    private fun <T> pickNonOverlapping(items: List<T>, span: (T) -> Span): List<T> {
        val sorted = items.sortedWith(compareBy<T> { span(it).start }.thenByDescending { span(it).length })
        val picked = mutableListOf<T>()
        for (item in sorted) {
            if (picked.none { span(it).overlaps(span(item)) }) picked += item
        }
        return picked
    }

    companion object {
        val DEFAULT_KEYWORDS = listOf(
            "회의", "미팅", "약속", "일정", "예약", "교육", "세미나", "컨퍼런스", "콘퍼런스", "컨퍼",
            "학회", "심포지엄", "워크샵", "워크숍", "집담회", "저널", "회식", "모임", "송년회", "신년회",
            "면접", "진료", "검진", "상담", "발표", "마감", "제출", "출장", "방문", "행사", "강의",
            "수업", "시험", "결혼식", "돌잔치", "생일", "장례", "참석", "접종", "수술", "치료",
            "QA", "점검", "인증", "회진", "당직", "리허설", "통화", "화상", "zoom", "teams", "팀즈",
            "webex", "식사", "점심", "저녁", "만나", "만남", "보자", "봬요", "뵙", "뵐", "휴가",
            "연차", "반차", "외근",
        )

        private const val WEEKDAY_CHARS = "월화수목금토일"

        private const val MERIDIEM = "(오전|오후|아침|저녁|밤|낮|새벽|점심|AM|PM)"

        private val YMD = Regex("(?<![0-9])(\\d{4})\\s*(?:년\\s*|[./-]\\s*)(\\d{1,2})\\s*(?:월\\s*|[./-]\\s*)(\\d{1,2})(?:\\s*일)?(?![0-9])")
        private val MD_KO = Regex("(?<![0-9])(\\d{1,2})\\s*월\\s*(\\d{1,2})\\s*일")
        private val MD_NUM = Regex(
            "(?<![0-9A-Za-z./:-])(\\d{1,2})[/.](\\d{1,2})(?![0-9]|[./:][0-9]|[A-Za-z%])(?!\\s*(?:c?Gy|cm|mm|MU|배|시간|개|명|%))",
            RegexOption.IGNORE_CASE,
        )
        private val DAY_ONLY = Regex("(?<![0-9월./-])(\\d{1,2})\\s*일(?!\\s*(?:후|뒤|간|동안|째|이내|안에|마다|씩|치|분|회))")
        private val AFTER_N = Regex("(?<![0-9])(\\d{1,2})\\s*(일|주)\\s*(?:후|뒤)")
        private val REL_DAY = Regex("내일\\s?모레|글피|모레|내일|명일|오늘|금일")
        private val WEEKDAY = Regex("(?:(이번\\s?주|금주|다다음\\s?주|다음\\s?주|담주|차주)\\s*)?([월화수목금토일])요일")

        private val TIME_KO = Regex(
            "(?<![0-9])(?:$MERIDIEM\\s*)?(\\d{1,2})\\s*시(?!간)(?:\\s*(\\d{1,2})\\s*분|\\s*(반))?",
            RegexOption.IGNORE_CASE,
        )
        private val TIME_COLON = Regex(
            "(?<![0-9:])(?:$MERIDIEM\\s*)?([01]?\\d|2[0-4]):([0-5]\\d)(?![0-9:])(?:\\s*(AM|PM)(?![A-Za-z]))?",
            RegexOption.IGNORE_CASE,
        )
        private val TIME_AMPM = Regex("(?<![0-9:])(\\d{1,2})\\s*(AM|PM)(?![A-Za-z])", RegexOption.IGNORE_CASE)
        private val NOON = Regex("정오")
        private val RANGE_GAP = Regex("\\s*(?:~|-|–|부터|에서)\\s*")

        private val LOC_LABEL = Regex("(?:장소|위치)\\s*[:：]\\s*([^\\n,]+)")
        private val LOC_AT = Regex("([가-힣A-Za-z0-9]+(?:\\s[A-Za-z0-9]{1,3})?)\\s?에서(?![가-힣])")

        private val SENTENCE_BOUNDARY = Regex("[.!?。](?=\\s)|\\n")
        private val WEEKDAY_PAREN = Regex("\\(\\s*[월화수목금토일]\\s*\\)")
        private val GREETING = Regex("안녕하세요|안녕하십니까|수고하십니다|수고하세요|감사합니다")
        private val FIELD_LABEL = Regex("(?:일시|일자|날짜|시간|시각|언제)\\s*[:：]")
        private val PARTICLE_TOKENS = setOf(
            "에", "에는", "부터", "까지", "에서", "의", "은", "는", "쯤", "쯤에", "경", "경에", "정도",
            "~", "-", "–", ",", "/", "까지는", "부터는", "요",
        )
        private val ENDINGS = Regex(
            "\\s*(?:있습니다|있어요|있어|있음|있네요|입니다|이에요|예요|예정입니다|예정이에요|예정|합니다|해요|할게요|할께요|" +
                "하겠습니다|어때요|어때|어떠세요|어떠신가요|가능하세요|가능할까요|가능하신가요|진행합니다|진행됩니다|" +
                "잡혔습니다|잡혔어요|잡았어요|입니당|이요)\\s*[.!?~ㅎㅋ^]*\\s*$",
        )
        private val SUBJECT_PARTICLE = Regex("([가-힣A-Za-z]{2,})[이가]\\s*$")
    }
}
