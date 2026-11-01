package com.sooparo.schedulecatcher

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import com.sooparo.schedulecatcher.core.MessageAnalyzer

/**
 * 문자/카카오톡/네이버 웍스 등의 알림 내용을 읽어 일정·할 일이 있으면 확인 알림을 띄운다.
 * 알림 내용은 기기 안에서만 분석되고 어디에도 전송/저장되지 않는다.
 */
class MessageListenerService : NotificationListenerService() {

    /** 대화방별 최근 메시지를 기억해 나뉘어 온 일정을 합친다 */
    private val analyzer = MessageAnalyzer()

    /** 대화방 → 마지막으로 띄운 감지 알림의 일정 id (내용 보완 시 같은 알림을 갱신) */
    private val lastNotified = HashMap<String, String>()

    /** 같은 메시지 알림이 여러 번 갱신되어도 한 번만 처리 */
    private val recent = object : LinkedHashMap<String, Long>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>?) = size > 200
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) return
        val n = sbn.notification ?: return
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        if (n.flags and Notification.FLAG_ONGOING_EVENT != 0) return

        val message = extractMessage(n) ?: return
        val prefs = Prefs(this)
        if (message.isConversation) prefs.recordSeenPackage(sbn.packageName)
        if (sbn.packageName !in prefs.monitoredPackages) return

        val now = System.currentTimeMillis()
        val dedupeKey = sbn.packageName + "|" + message.text
        val conversationKey = sbn.packageName + "|" + (message.conversation ?: message.sender ?: "")

        synchronized(this) {
            val last = recent[dedupeKey]
            if (last != null && now - last < DEDUPE_MS) return
            recent[dedupeKey] = now

            val detection = runCatching { analyzer.analyze(conversationKey, message.text) }.getOrNull() ?: return
            val previousId = lastNotified[conversationKey]?.takeIf { detection.updatesPrevious }
            val app = AppLabels.label(this, sbn.packageName)
            val data = (
                if (previousId != null) {
                    ScheduleData.from(detection, app, message.sender, id = previousId)
                } else {
                    ScheduleData.from(detection, app, message.sender)
                }
                ) ?: return

            if (detection.schedule != null) lastNotified[conversationKey] = data.id
            Notifications.showDetected(this, data)
        }
    }

    private data class Message(val sender: String?, val conversation: String?, val text: String, val isConversation: Boolean)

    private fun extractMessage(n: Notification): Message? {
        val style = runCatching { NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(n) }.getOrNull()
        val last = style?.messages?.lastOrNull()
        if (style != null && last != null) {
            val text = last.text?.toString()?.trim().orEmpty()
            if (text.isEmpty()) return null
            val person = last.person?.name?.toString()
            val conversation = style.conversationTitle?.toString()
            val sender = when {
                conversation != null && person != null && conversation != person -> "$conversation · $person"
                else -> person ?: conversation
            }
            return Message(sender, conversation ?: person, text, isConversation = true)
        }

        val extras = n.extras ?: return null
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: extras.getCharSequence(Notification.EXTRA_TEXT))
            ?.toString()?.trim().orEmpty()
        if (text.isEmpty()) return null
        return Message(title, title, text, isConversation = n.category == Notification.CATEGORY_MESSAGE)
    }

    companion object {
        private const val DEDUPE_MS = 10 * 60 * 1000L
    }
}
