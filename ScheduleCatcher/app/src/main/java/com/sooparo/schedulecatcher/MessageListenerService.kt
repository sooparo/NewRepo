package com.sooparo.schedulecatcher

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat

/**
 * 문자/카카오톡/네이버 웍스 등의 알림 내용을 읽어 일정·할 일이 있으면 확인 알림을 띄운다.
 * 알림 내용은 기기 안에서만 분석되고 어디에도 전송/저장되지 않는다.
 */
class MessageListenerService : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) return
        val n = sbn.notification ?: return
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        if (n.flags and Notification.FLAG_ONGOING_EVENT != 0) return

        val message = extractMessage(n) ?: return
        val prefs = Prefs(this)
        if (message.isConversation) prefs.recordSeenPackage(sbn.packageName)
        if (sbn.packageName !in prefs.monitoredPackages) return

        val conversationKey = sbn.packageName + "|" + (message.conversation ?: message.sender ?: "")
        DetectionHub.onMessage(this, sbn.packageName, conversationKey, message.sender, message.text)
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

}
