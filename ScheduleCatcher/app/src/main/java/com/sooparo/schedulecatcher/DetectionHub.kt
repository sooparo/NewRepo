package com.sooparo.schedulecatcher

import android.content.Context
import com.sooparo.schedulecatcher.core.MessageAnalyzer

/**
 * 메시지 감지 창구.
 * - 분석 전에 개인정보를 가린다 (PrivacyFilter)
 * - 같은 메시지 알림이 여러 번 갱신되어도 한 번만 알린다
 * - 대화방별로 연속 메시지를 합친다
 */
object DetectionHub {

    private val analyzer = MessageAnalyzer()
    private val lastNotified = HashMap<String, String>()
    private val recent = object : LinkedHashMap<String, Long>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>?) = size > 300
    }
    private const val DEDUPE_MS = 10 * 60 * 1000L

    /** @return 감지 알림을 띄웠으면 true */
    @Synchronized
    fun onMessage(context: Context, packageName: String, conversationKey: String, sender: String?, rawText: String): Boolean {
        val prefs = Prefs(context)
        val text = prefs.privacyFilter().redact(rawText).trim()
        if (text.length < 2) return false

        val now = System.currentTimeMillis()
        val dedupeKey = packageName + "|" + text.replace(Regex("\\s+"), "")
        val last = recent[dedupeKey]
        if (last != null && now - last < DEDUPE_MS) return false
        recent[dedupeKey] = now

        val detection = runCatching { analyzer.analyze(conversationKey, text) }.getOrNull() ?: return false
        val app = AppLabels.label(context, packageName)
        val shownSender = if (prefs.hideSender) null else sender?.let { prefs.privacyFilter().redact(it) }
        val previousId = lastNotified[conversationKey]?.takeIf { detection.updatesPrevious }
        val data = (
            if (previousId != null) {
                ScheduleData.from(detection, app, shownSender, id = previousId)
            } else {
                ScheduleData.from(detection, app, shownSender)
            }
            ) ?: return false

        if (detection.schedule != null) lastNotified[conversationKey] = data.id
        Notifications.showDetected(context, data)
        return true
    }

    /** 대화방을 옮겼을 때 이전 대화방 메시지와 합쳐지지 않도록 */
    @Synchronized
    fun forget(conversationKey: String) {
        analyzer.forget(conversationKey)
        lastNotified.remove(conversationKey)
    }
}
