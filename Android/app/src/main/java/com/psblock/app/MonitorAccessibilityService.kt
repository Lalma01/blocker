package com.psblock.app

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Reads browser page titles / URL bars and blocks NSFW or NSFW-AI content by keyword.
 * Android replacement for the PowerShell browser-title monitor + SendKeys redirect in main.js.
 *
 * On a hit it leaves the page (global BACK) and shows the in-app blocked notice, mirroring the
 * Windows behaviour of redirecting the tab to blocked.html.
 */
class MonitorAccessibilityService : AccessibilityService() {

    private val browsers = Browsers.PACKAGES

    private val urlBarIds = listOf(
        "com.android.chrome:id/url_bar",
        "com.microsoft.emmx:id/url_bar",
        "com.brave.browser:id/url_bar",
        "com.sec.android.app.sbrowser:id/location_bar_edit_text",
        "org.mozilla.firefox:id/mozac_browser_toolbar_url_view"
    )

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        val pkg = event.packageName?.toString() ?: return
        if (pkg !in browsers) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        ) return

        val text = collectText(event)
        if (text.isBlank()) return

        val reason = TitleEvaluator.evaluate(text, Config.customBlocked) ?: return
        if (!Notifications.shouldTrigger(reason)) return

        Config.blockedCount = Config.blockedCount + 1
        performGlobalAction(GLOBAL_ACTION_BACK)

        val i = Intent(this, BlockedActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra("reason", reason)
        startActivity(i)
    }

    /** Gather candidate text: event text, the active window title, and the URL bar contents. */
    private fun collectText(event: AccessibilityEvent): String {
        val sb = StringBuilder()
        event.text.forEach { sb.append(it).append(' ') }
        try {
            val root = rootInActiveWindow
            if (root != null) {
                for (id in urlBarIds) {
                    root.findAccessibilityNodeInfosByViewId(id)?.forEach { node ->
                        node.text?.let { sb.append(it).append(' ') }
                        node.contentDescription?.let { sb.append(it).append(' ') }
                    }
                }
                collectTitles(root, sb, 0)
                root.recycle()
            }
        } catch (_: Exception) {}
        return sb.toString().trim()
    }

    /** Shallow walk to capture address-bar / title text without scanning the whole tree. */
    private fun collectTitles(node: AccessibilityNodeInfo?, sb: StringBuilder, depth: Int) {
        node ?: return
        if (depth > 4) return
        if (node.className?.contains("EditText") == true || node.className?.contains("TextView") == true) {
            node.text?.let { if (it.length in 3..200) sb.append(it).append(' ') }
        }
        for (i in 0 until node.childCount) {
            collectTitles(node.getChild(i), sb, depth + 1)
        }
    }

    override fun onInterrupt() {}
}
