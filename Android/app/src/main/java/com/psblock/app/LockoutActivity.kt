package com.psblock.app

import android.os.Bundle

/**
 * Full-screen lockout shown when the daily limit is reached — Android equivalent of the
 * kiosk screen-time window + cover windows + lockout enforcer on Windows. It reuses the
 * screen-time UI but cannot be dismissed (back is swallowed) until time is added.
 */
class LockoutActivity : ScreenTimeActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Hide the close button entirely in lockout mode.
        findViewById<android.view.View?>(R.id.closeBtn)?.visibility = android.view.View.GONE
        findViewById<android.view.View>(R.id.addTimeBtn).setOnClickListener { addTime() }
    }

    override fun onTimeAdded() {
        if (Config.remaining() != 0) finish()
    }

    @Deprecated("Blocked during lockout")
    override fun onBackPressed() {
        // Swallow back while time is up; allow leaving once time has been added.
        if (Config.remaining() != 0) {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }

    override fun onUserLeaveHint() {
        // If the user tries Home while locked out, the ScreenTimeService re-raises us.
        super.onUserLeaveHint()
    }
}
