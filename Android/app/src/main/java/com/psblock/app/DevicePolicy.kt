package com.psblock.app

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.UserManager

/**
 * Device-admin / device-owner policy helper (anti-tamper).
 *
 * - As a plain **device admin**, uninstalling requires deactivating the admin first (friction).
 * - As a **device owner** (provisioned via `adb shell dpm set-device-owner
 *   com.psblock.app/.AdminReceiver` on a fresh device), PS-BLOCK can:
 *     • block its own uninstall outright while a password is set,
 *     • force its filtering VPN to be always-on (no consent prompt, auto-restart),
 *     • lock Private DNS to opportunistic so the user can't route lookups through an
 *       encrypted resolver that bypasses the filter (defeats user-set DoT/DoH),
 *     • stop the user from reconfiguring VPNs in Settings.
 */
object DevicePolicy {

    fun admin(ctx: Context) = ComponentName(ctx, AdminReceiver::class.java)
    private fun dpm(ctx: Context) = ctx.getSystemService(DevicePolicyManager::class.java)

    fun isOwner(ctx: Context): Boolean = dpm(ctx)?.isDeviceOwnerApp(ctx.packageName) == true
    fun isAdminActive(ctx: Context): Boolean = dpm(ctx)?.isAdminActive(admin(ctx)) == true

    /** Apply every policy we are allowed to, based on current privilege level. */
    fun apply(ctx: Context) {
        val d = dpm(ctx) ?: return
        val a = admin(ctx)
        if (!d.isDeviceOwnerApp(ctx.packageName)) return

        try { d.setUninstallBlocked(a, ctx.packageName, Config.passwordProtected) } catch (_: Exception) {}

        // Force our content-filter VPN to always-on so it survives reboots and can't be left off.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try { d.setAlwaysOnVpnPackage(a, ctx.packageName, false) } catch (_: Exception) {}
        }

        // Pin Private DNS to opportunistic. Combined with our VPN (whose DNS server speaks only
        // plain DNS) this neutralises system DNS-over-TLS and blocks user-chosen DoH providers.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try { d.setGlobalPrivateDnsModeOpportunistic(a) } catch (_: Exception) {}
        }

        // Stop the user from adding/altering VPN config in Settings.
        try { d.addUserRestriction(a, UserManager.DISALLOW_CONFIG_VPN) } catch (_: Exception) {}
    }

    /** Relax owner policies (e.g. when the password is removed) so the app can be removed again. */
    fun relax(ctx: Context) {
        val d = dpm(ctx) ?: return
        val a = admin(ctx)
        if (!d.isDeviceOwnerApp(ctx.packageName)) return
        try { d.setUninstallBlocked(a, ctx.packageName, false) } catch (_: Exception) {}
        try { d.clearUserRestriction(a, UserManager.DISALLOW_CONFIG_VPN) } catch (_: Exception) {}
    }
}
