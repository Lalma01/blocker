package com.psblock.app

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/** Screen-time view — Android port of screentime.html (ring, used/limit, +15 min with password). */
open class ScreenTimeActivity : AppCompatActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private val refresh = object : Runnable {
        override fun run() { render(); handler.postDelayed(this, 1000) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Config.init(this)
        setContentView(R.layout.activity_screentime)
        findViewById<Button>(R.id.addTimeBtn).setOnClickListener { addTime() }
        findViewById<Button?>(R.id.closeBtn)?.setOnClickListener { finish() }
    }

    override fun onResume() { super.onResume(); handler.post(refresh) }
    override fun onPause() { super.onPause(); handler.removeCallbacks(refresh) }

    protected fun addTime() {
        if (Config.passwordProtected) {
            val input = EditText(this).apply {
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            }
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.st_lock_h2)
                .setMessage(R.string.st_lock_p)
                .setView(input)
                .setPositiveButton(R.string.lock_btn) { _, _ ->
                    if (Config.checkPassword(input.text.toString())) doAdd()
                    else Toast.makeText(this, R.string.err_pw_wrong, Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton(R.string.st_lock_cancel, null)
                .show()
        } else doAdd()
    }

    /** Add 15 minutes (capped at 120, like add-time in main.js). */
    private fun doAdd() {
        val add = 15
        Config.usedSeconds = (Config.usedSeconds - add * 60).coerceAtLeast(0)
        render()
        onTimeAdded()
    }

    /** Hook so the lockout screen can close itself once time is available again. */
    protected open fun onTimeAdded() {}

    private fun render() {
        Config.rolloverDay()
        val limit = Config.screenTimeLimit
        val used = Config.usedSeconds
        val rem = Config.remaining()

        val ring = findViewById<com.google.android.material.progressindicator.CircularProgressIndicator>(R.id.ring)
        val total = limit * 60
        ring.progress = if (total > 0) (used.toDouble() / total * 100).coerceIn(0.0, 100.0).toInt() else 0

        findViewById<TextView>(R.id.ringTime).text = if (rem < 0) "∞" else fmtClock(rem)
        findViewById<TextView>(R.id.ringLabel).text =
            getString(if (rem < 0) R.string.ring_unlimited else R.string.ring_left)
        findViewById<TextView>(R.id.usedVal).text = fmtMin(used)
        findViewById<TextView>(R.id.limitVal).text =
            if (limit > 0) "$limit ${getString(R.string.unit_min)}" else getString(R.string.val_unlimited)

        val limitMsg = findViewById<View>(R.id.limitMsg)
        val closeBtn = findViewById<View?>(R.id.closeBtn)
        if (rem == 0 && limit > 0) {
            limitMsg.visibility = View.VISIBLE
            closeBtn?.visibility = View.GONE
        } else {
            limitMsg.visibility = View.GONE
            closeBtn?.visibility = View.VISIBLE
        }
    }

    private fun fmtClock(sec: Int): String {
        val h = sec / 3600; val m = (sec % 3600) / 60; val s = sec % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }

    private fun fmtMin(sec: Int): String {
        if (sec < 0) return getString(R.string.val_unlimited)
        val h = sec / 3600; val m = (sec % 3600) / 60
        return if (h > 0) "$h${getString(R.string.unit_hour)} $m${getString(R.string.unit_min_short)}"
        else "$m${getString(R.string.unit_min_short)}"
    }
}
