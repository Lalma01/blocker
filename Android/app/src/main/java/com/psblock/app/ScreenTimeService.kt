package com.psblock.app

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import androidx.core.app.NotificationCompat

/**
 * Counts daily screen time and enforces the limit. Foreground + sticky so the OS keeps it alive,
 * mirroring the Windows always-running timer + watchdog.
 *
 * Counting pauses when the screen is off (the Android analogue of the lock/suspend/idle pause).
 * When the limit is hit it raises the full-screen LockoutActivity and keeps it on top until the
 * user adds time (with the password, if set).
 */
class ScreenTimeService : android.app.Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var lastWarn15 = false
    private var lastWarn5 = false
    private var lastWarn1 = false
    private var limitReached = false
    private var lastLockoutPush = 0L
    @Volatile private var screenOn = true

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> screenOn = true
                Intent.ACTION_SCREEN_OFF -> { screenOn = false; persist() }
            }
        }
    }

    private val tick = object : Runnable {
        override fun run() {
            step()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate() {
        super.onCreate()
        Config.init(this)
        val pm = getSystemService(PowerManager::class.java)
        screenOn = pm?.isInteractive ?: true
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        registerReceiver(screenReceiver, filter)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIF_ID, buildNotification())
        handler.removeCallbacks(tick)
        handler.post(tick)
        return START_STICKY
    }

    private fun step() {
        Config.rolloverDay()

        // Count only while the screen is interactive (idle/lock pause equivalent).
        if (screenOn) Config.usedSeconds = Config.usedSeconds + 1

        val rem = Config.remaining()
        if (rem < 0) return // unlimited

        // One-shot warnings at 15 / 5 / 1 minutes left; reset once time is added.
        if (rem > 15 * 60) { lastWarn15 = false; lastWarn5 = false; lastWarn1 = false }
        if (rem in 1..(15 * 60) && !lastWarn15) { Notifications.timeWarning(this, getString(R.string.notif_15)); lastWarn15 = true }
        if (rem in 1..(5 * 60) && !lastWarn5) { Notifications.timeWarning(this, getString(R.string.notif_5)); lastWarn5 = true }
        if (rem in 1..60 && !lastWarn1) { Notifications.timeWarning(this, getString(R.string.notif_1)); lastWarn1 = true }

        if (rem == 0) {
            if (!limitReached) {
                limitReached = true
                Notifications.timeWarning(this, getString(R.string.notif_limit))
            }
            pushLockout()
        } else {
            limitReached = false
        }
    }

    /** Keep the lockout screen in front while time is up (lockout-enforcer equivalent). */
    private fun pushLockout() {
        val now = System.currentTimeMillis()
        if (now - lastLockoutPush < 2000) return
        lastLockoutPush = now
        val i = Intent(this, LockoutActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        startActivity(i)
    }

    private fun persist() { /* usedSeconds is already written through on each change */ }

    private fun buildNotification() =
        NotificationCompat.Builder(this, PsBlockApp.CH_SERVICE)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(tooltip())
            .setSmallIcon(R.drawable.ic_shield)
            .setOngoing(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    this, 0, Intent(this, ScreenTimeActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
            .build()

    private fun tooltip(): String {
        val rem = Config.remaining()
        return if (rem < 0) getString(R.string.tray_unlimited)
        else Util.fmtTime(this, rem) + " " + getString(R.string.tray_left)
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        try { unregisterReceiver(screenReceiver) } catch (_: Exception) {}
        // Sticky restart: ask the system to recreate us.
        super.onDestroy()
    }

    override fun onBind(intent: Intent?) = null

    companion object {
        private const val NOTIF_ID = 1002
    }
}
