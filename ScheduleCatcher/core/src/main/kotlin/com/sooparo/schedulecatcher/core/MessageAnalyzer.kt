package com.sooparo.schedulecatcher.core

/**
 * 대화방 단위로 최근 메시지를 기억해서, 여러 메시지로 나뉘어 온 일정을 합쳐 인식한다.
 *
 *   "내일 3시"            → 일정 감지 (제목: 새 일정)
 *   "회의실에서 QA 회의"   → 앞 메시지와 합쳐 같은 일정의 제목을 "QA 회의"로 갱신 (updatesPrevious = true)
 *
 * 스레드 안전하지 않으므로 한 스레드에서만 호출할 것.
 */
class MessageAnalyzer(
    private val parser: ScheduleParser = ScheduleParser(),
    private val todoDetector: TodoDetector = TodoDetector(parser),
    private val mergeWindowMs: Long = 5 * 60 * 1000L,
    private val maxBuffered: Int = 4,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {

    data class Detection(
        val schedule: ParsedSchedule?,
        val todo: ParsedTodo?,
        /** true 면 같은 대화방에서 직전에 감지한 일정을 보완한 결과 (알림을 새로 띄우지 말고 갱신) */
        val updatesPrevious: Boolean,
    )

    private data class Msg(val time: Long, val text: String)

    private class Conversation {
        val messages = ArrayDeque<Msg>()
        var lastSchedule: ParsedSchedule? = null
        var lastTime: Long = 0
    }

    private val conversations = LinkedHashMap<String, Conversation>()

    fun analyze(conversationKey: String, text: String): Detection? {
        val now = clock()
        val conv = conversations.getOrPut(conversationKey) { Conversation() }
        while (conv.messages.isNotEmpty() && now - conv.messages.first().time > mergeWindowMs) conv.messages.removeFirst()
        if (now - conv.lastTime > mergeWindowMs) conv.lastSchedule = null
        val previous = conv.messages.map { it.text }

        conv.messages.addLast(Msg(now, text))
        while (conv.messages.size > maxBuffered) conv.messages.removeFirst()
        trimConversations()

        val joined = if (previous.isEmpty()) null else (previous + text).joinToString("\n")
        val single = parser.parse(text)
        val todo = todoDetector.detect(text)

        if (single != null) {
            // 단독으로도 일정이지만 제목이 빈약하면 앞 메시지로 보완 ("QA 회의 잡혔어요" → "내일 3시")
            val improved = if (single.title == WEAK_TITLE && joined != null) {
                parser.parse(joined)?.takeIf { it.start == single.start && it.title != WEAK_TITLE }
            } else {
                null
            }
            val result = (improved ?: single).copy(sourceText = text)
            remember(conv, result, now)
            return Detection(result, todo, updatesPrevious = false)
        }

        if (joined != null) {
            val merged = parser.parse(joined)
            val last = conv.lastSchedule
            if (merged != null) {
                if (last != null && merged.start == last.start) {
                    // 같은 일정에 대한 추가 정보: 제목/장소가 실제로 좋아졌을 때만 갱신
                    val better = merged.copy(
                        title = if (merged.title != WEAK_TITLE) merged.title else last.title,
                        location = merged.location ?: last.location,
                        sourceText = joined,
                    )
                    if (better.title != last.title || better.location != last.location) {
                        remember(conv, better, now)
                        return Detection(better, todo, updatesPrevious = true)
                    }
                } else if (last == null) {
                    val result = merged.copy(sourceText = joined)
                    remember(conv, result, now)
                    return Detection(result, todo, updatesPrevious = false)
                }
            }
        }

        if (todo != null) return Detection(null, todo, updatesPrevious = false)
        return null
    }

    /** 대화방을 잊는다 (예: 사용자가 알림을 처리한 뒤) */
    fun forget(conversationKey: String) {
        conversations.remove(conversationKey)
    }

    private fun remember(conv: Conversation, schedule: ParsedSchedule, now: Long) {
        conv.lastSchedule = schedule
        conv.lastTime = now
    }

    private fun trimConversations() {
        while (conversations.size > MAX_CONVERSATIONS) {
            conversations.remove(conversations.keys.first())
        }
    }

    companion object {
        const val WEAK_TITLE = "새 일정"
        private const val MAX_CONVERSATIONS = 50
    }
}
