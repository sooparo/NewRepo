package com.sooparo.schedulecatcher.core

import java.time.LocalDate
import java.time.LocalTime

data class ParsedTodo(
    val title: String,
    val dueDate: LocalDate?,
    val dueTime: LocalTime?,
    val sourceText: String,
)

/**
 * "~까지 제출 부탁드립니다", "치료계획 검토해 주세요" 같은 업무 요청을 '할 일'로 감지한다.
 * 조건: 요청 표현(부탁/주세요/바랍니다/해야 …) + 업무 동사(제출/검토/확인/작성 …)가 같은 문장에 있을 것.
 */
class TodoDetector(private val parser: ScheduleParser = ScheduleParser()) {

    fun detect(text: String): ParsedTodo? {
        val sentences = text.split(Regex("(?<=[.!?。])\\s+|\\n+")).map { it.trim() }.filter { it.isNotEmpty() }
        val sentence = sentences.firstOrNull { REQUEST.containsMatchIn(it) && ACTION.containsMatchIn(it) } ?: return null

        val due = parser.extractDue(sentence) ?: parser.extractDue(text)
        val title = cleanTitle(sentence).ifEmpty { return null }
        return ParsedTodo(title, due?.first, due?.second, text)
    }

    private fun cleanTitle(sentence: String): String {
        var s = sentence
            .replace(DATE_LIKE, " ")
            .replace(GREETING, " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        repeat(2) { s = s.replace(REQUEST_ENDING, "").trim() }
        s = s.replace(Regex("\\s*(?:까지|까지는|까지요)(?=\\s|$)"), " ").replace(Regex("\\s+"), " ").trim()

        // 동사 어간 정리: "검토해" → "검토", "보내" → "보내기"
        s = when {
            s.endsWith("보내") -> s + "기"
            s.endsWith("받아") -> s.dropLast(1) + "기"
            s.endsWith("알려") -> s.dropLast(2) + "알리기"
            s.endsWith("해") && s.length > 2 -> s.dropLast(1).trimEnd()
            s.endsWith("하여") -> s.dropLast(2).trimEnd()
            else -> s
        }
        s = s.trim().trim('.', ',', '!', '?', '~', ' ', '-', ':')
        return if (s.length > 40) s.take(40).trimEnd() + "…" else s
    }

    companion object {
        private val REQUEST = Regex(
            "부탁|주세요|주시기|주십시오|바랍니다|요청드|요청합니다|해야|[가-힣]야\\s?(?:합니다|해요|함|돼요|됩니다|해)|필요합니다|해\\s?줘|해\\s?주라|드려야|해\\s?놓|챙겨",
        )
        private val ACTION = Regex(
            "제출|회신|답장|검토|확인|작성|수정|보내|전달|서명|사인|승인|결재|업로드|등록|입력|정리|준비|컨펌|체크|" +
                "출력|공유|발송|신청|예약|연락|처리|보고|알려|챙겨|마무리|완료|반영",
        )
        private val DATE_LIKE = Regex(
            "(?:\\d{4}\\s*[년./-]\\s*)?\\d{1,2}\\s*(?:월|[./])\\s*\\d{1,2}\\s*일?(?:\\s*\\(\\s*[월화수목금토일]\\s*\\))?|" +
                "(?:이번\\s?주|다음\\s?주|담주|차주)?\\s*[월화수목금토일]요일|내일\\s?모레|오늘|내일|모레|금일|명일|" +
                "(?:오전|오후|아침|저녁|밤)?\\s*\\d{1,2}\\s*시(?!간)(?:\\s*\\d{1,2}\\s*분|\\s*반)?|\\d{1,2}:\\d{2}|" +
                "(?<![0-9])\\d{1,2}\\s*일(?=\\s|까지)|(?:오전|오후|아침|저녁|점심|밤)(?=\\s*까지)",
        )
        private val GREETING = Regex("안녕하세요[.!~]?|안녕하십니까[.!]?|수고하십니다[.!]?|선생님[,.]?(?=\\s)|감사합니다[.!]?")
        private val REQUEST_ENDING = Regex(
            "\\s*(?:해\\s?주시면\\s?감사하겠습니다|해\\s?주시길\\s?바랍니다|해\\s?주시기\\s?바랍니다|주시기\\s?바랍니다|" +
                "부탁\\s?드립니다|부탁\\s?드려요|부탁드리겠습니다|부탁해요|부탁합니다|부탁|" +
                "해\\s?주세요|해\\s?주십시오|주세요|주십시오|바랍니다|요청\\s?드립니다|요청합니다|" +
                "해야\\s?합니다|해야\\s?해요|해야\\s?함|해야\\s?돼요|해야\\s?됩니다|해야\\s?해|" +
                "야\\s?합니다|야\\s?해요|야\\s?함|야\\s?돼요|야\\s?됩니다|필요합니다|해\\s?줘|해\\s?주라)" +
                "[.!~^ㅎㅋ\\s]*$",
        )
    }
}
