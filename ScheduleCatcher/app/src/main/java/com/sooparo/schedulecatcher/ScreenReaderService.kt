package com.sooparo.schedulecatcher

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * (선택 기능) 대화방을 보고 있어서 알림이 뜨지 않을 때도 새 메시지를 감지한다.
 *
 * 개인정보 보호
 * - 안드로이드 설정상 '감지할 앱'(카톡·웍스·문자)의 화면만 전달받는다. 다른 앱 화면은 받지 않는다.
 * - 화면 글자는 메모리에서 분석만 하고 저장하지 않는다. 분석 전에 등록번호·주민번호·전화번호·호칭 이름을 가린다.
 * - 대화방에 들어갈 때 이미 보이는 메시지는 '기존 메시지'로 기억하고 감지하지 않는다.
 *   이후 화면 아래쪽에 새로 나타난 글자만 새 메시지로 본다 (위로 스크롤해서 보이는 옛 메시지는 제외).
 */
class ScreenReaderService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private var currentPackage: String? = null
    private var currentWindowClass: String? = null
    private var baselined = false
    private val seen = LinkedHashSet<String>()
    private val scan = Runnable { scanScreen() }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        applyPackages()
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        handler.removeCallbacks(scan)
        super.onDestroy()
    }

    override fun onInterrupt() = Unit

    /** 감지할 앱 목록이 바뀌면 시스템에 전달받을 앱을 다시 지정 */
    fun applyPackages() {
        val info = serviceInfo ?: return
        val packages = Prefs(this).monitoredPackages.toTypedArray()
        info.packageNames = if (packages.isEmpty()) arrayOf(packageName) else packages
        serviceInfo = info
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName || pkg !in Prefs(this).monitoredPackages) return

        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val cls = event.className?.toString()
            if (pkg != currentPackage || cls != currentWindowClass) {
                // 다른 앱/화면으로 이동 → 기존 메시지 기준을 새로 잡는다
                currentPackage = pkg
                currentWindowClass = cls
                resetBaseline(pkg)
            }
        }
        if (currentPackage != pkg) {
            currentPackage = pkg
            resetBaseline(pkg)
        }
        handler.removeCallbacks(scan)
        handler.postDelayed(scan, SCAN_DELAY_MS)
    }

    private fun resetBaseline(pkg: String) {
        baselined = false
        seen.clear()
        DetectionHub.forget(conversationKey(pkg))
    }

    private fun conversationKey(pkg: String) = "screen|$pkg"

    private data class TextNode(val text: String, val top: Int)

    private fun scanScreen() {
        val pkg = currentPackage ?: return
        val root = rootInActiveWindow ?: return
        if (root.packageName?.toString() != pkg) return

        val screen = Rect().also { root.getBoundsInScreen(it) }
        val nodes = mutableListOf<TextNode>()
        collect(root, nodes, depth = 0)

        if (!baselined) {
            nodes.forEach { remember(it.text) }
            baselined = true
            return
        }

        val fresh = nodes.filter { it.text !in seen }
        fresh.forEach { remember(it.text) }

        // 한 번에 많은 글자가 새로 보이면 스크롤/화면 전환으로 보고 감지하지 않는다
        if (fresh.isEmpty() || fresh.size > MAX_NEW_PER_SCAN) return

        // 새 메시지는 화면 아래쪽에 나타난다 (아래 40% 영역)
        val bottomZone = screen.top + (screen.height() * 0.6).toInt()
        fresh.filter { it.top >= bottomZone }
            .sortedBy { it.top }
            .forEach { DetectionHub.onMessage(this, pkg, conversationKey(pkg), null, it.text) }
    }

    private fun remember(text: String) {
        seen += text
        while (seen.size > MAX_SEEN) seen.remove(seen.first())
    }

    private fun collect(node: AccessibilityNodeInfo, out: MutableList<TextNode>, depth: Int) {
        if (depth > MAX_DEPTH || out.size > MAX_NODES) return
        // 입력창(내가 쓰는 중인 글)은 제외
        if (node.isEditable) return
        val text = node.text?.toString()?.trim()
        if (!text.isNullOrEmpty() && text.length >= MIN_TEXT && node.isVisibleToUser) {
            val r = Rect()
            node.getBoundsInScreen(r)
            out += TextNode(text, r.top)
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collect(child, out, depth + 1)
        }
    }

    companion object {
        private const val SCAN_DELAY_MS = 700L
        private const val MAX_NEW_PER_SCAN = 3
        private const val MAX_SEEN = 600
        private const val MAX_DEPTH = 40
        private const val MAX_NODES = 400
        private const val MIN_TEXT = 4

        @Volatile
        private var instance: ScreenReaderService? = null

        @Suppress("UNUSED_PARAMETER")
        fun applyPackages(context: Context) {
            instance?.let { svc -> Handler(Looper.getMainLooper()).post { svc.applyPackages() } }
        }

        fun isEnabled(context: Context): Boolean {
            val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
                ?: return false
            val me = ComponentName(context, ScreenReaderService::class.java)
            return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
        }
    }
}
