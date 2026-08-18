package com.psblock.app

import android.content.Context
import androidx.core.app.NotificationManagerCompat

/** Helper for the user-facing alerts (content blocked, screen-time warnings). */
object Notifications {

    private var lastReason = ""
    private var lastReasonTime = 0L

    /** A DNS-level block bumps the counter shown on the dashboard. */
    fun bumpDnsBlock() {
        Config.blockedCount = Config.blockedCount + 1
    }

    fun timeWarning(ctx: Context, body: String) {
        notify(ctx, ctx.getString(R.string.notif_time_title), body)
    }

    private fun notify(ctx: Context, title: String, body: String) {
        if (!NotificationManagerCompat.from(ctx).areNotificationsEnabled()) return
        val n = androidx.core.app.NotificationCompat.Builder(ctx, PsBlockApp.CH_ALERT)
            .setSmallIcon(R.drawable.ic_shield)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
            .build()
        try { NotificationManagerCompat.from(ctx).notify((System.currentTimeMillis() and 0xFFFF).toInt(), n) } catch (_: SecurityException) {}
    }

    /** Debounce duplicate keyword hits (3s window — mirrors triggerBlock in main.js). */
    fun shouldTrigger(reason: String): Boolean {
        val now = System.currentTimeMillis()
        if (reason == lastReason && now - lastReasonTime < 3000) return false
        lastReason = reason
        lastReasonTime = now
        return true
    }
}
