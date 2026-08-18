package com.psblock.app

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/** "Content blocked" notice — Android port of blocked.html / the toast notification. */
class BlockedActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Config.init(this)
        setContentView(R.layout.activity_blocked)
        val reason = intent.getStringExtra("reason")
        if (!reason.isNullOrBlank()) {
            findViewById<TextView>(R.id.reason).text = getString(R.string.nt_reason) + reason
        }
        findViewById<android.view.View>(R.id.okBtn).setOnClickListener { finish() }
        // Auto-dismiss after 7s like the Windows notification countdown.
        Handler(Looper.getMainLooper()).postDelayed({ if (!isFinishing) finish() }, 7000)
    }
}
