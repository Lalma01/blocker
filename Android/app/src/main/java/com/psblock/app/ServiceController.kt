package com.psblock.app

import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build

/** Central place to start the background services (filter VPN + screen-time guard). */
object ServiceController {

    fun startScreenTime(ctx: Context) {
        val i = Intent(ctx, ScreenTimeService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ctx.startForegroundService(i)
        else ctx.startService(i)
    }

    fun startVpn(ctx: Context) {
        val i = Intent(ctx, FilterVpnService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ctx.startForegroundService(i)
        else ctx.startService(i)
    }

    fun stopVpn(ctx: Context) {
        ctx.startService(Intent(ctx, FilterVpnService::class.java).setAction(FilterVpnService.ACTION_STOP))
    }

    /** Start everything that can run without an interactive consent prompt. */
    fun startAll(ctx: Context) {
        DevicePolicy.apply(ctx)
        startScreenTime(ctx)
        // VPN starts if consent was already granted, or if we're device owner (always-on/no prompt).
        if (VpnService.prepare(ctx) == null || DevicePolicy.isOwner(ctx)) startVpn(ctx)
    }
}
