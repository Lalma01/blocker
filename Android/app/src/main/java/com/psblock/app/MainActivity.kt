package com.psblock.app

import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/** Status dashboard — the Android port of index.html. */
class MainActivity : AppCompatActivity() {

    private val vpnConsent =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) ServiceController.startVpn(this)
            refresh()
        }

    private val notifPerm =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Config.init(this)
        setContentView(R.layout.activity_main)

        findViewById<android.view.View>(R.id.settingsBtn).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        findViewById<android.view.View>(R.id.screenTimeBtn).setOnClickListener {
            startActivity(Intent(this, ScreenTimeActivity::class.java))
        }
        findViewById<android.view.View>(R.id.enableBtn).setOnClickListener { ensureRunning() }

        requestNotifPermission()
        ServiceController.startScreenTime(this)
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun requestNotifPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifPerm.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    /** Walk the user through the permissions the filter needs, then start the services. */
    private fun ensureRunning() {
        // 1) VPN consent for the DNS content filter.
        val prep = VpnService.prepare(this)
        if (prep != null) { vpnConsent.launch(prep); return }
        ServiceController.startVpn(this)

        // 2) Accessibility for the browser-title monitor.
        if (!isAccessibilityEnabled()) {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.perm_accessibility_title)
                .setMessage(R.string.perm_accessibility_msg)
                .setPositiveButton(R.string.perm_open) { _, _ ->
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }
                .setNegativeButton(R.string.st_lock_cancel, null)
                .show()
            return
        }
        // 3) Overlay permission for the lockout / blocked screens.
        if (!Settings.canDrawOverlays(this)) {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.perm_overlay_title)
                .setMessage(R.string.perm_overlay_msg)
                .setPositiveButton(R.string.perm_open) { _, _ ->
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        android.net.Uri.parse("package:$packageName")))
                }
                .setNegativeButton(R.string.st_lock_cancel, null)
                .show()
        }
        refresh()
    }

    private fun isAccessibilityEnabled(): Boolean {
        val flat = Settings.Secure.getString(contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        return flat.contains("$packageName/${MonitorAccessibilityService::class.java.name}")
    }

    private fun refresh() {
        fun setText(id: Int, value: String) { findViewById<android.widget.TextView>(id).text = value }

        setText(R.id.blockedCount, Config.blockedCount.toString())
        setText(R.id.pwStatus, getString(if (Config.passwordProtected) R.string.val_pw_set else R.string.val_pw_none))

        val rem = Config.remaining()
        val st = when {
            Config.screenTimeLimit <= 0 -> getString(R.string.val_unlimited)
            rem == 0 -> getString(R.string.val_expired)
            else -> {
                val h = rem / 3600; val m = (rem % 3600) / 60
                if (h > 0) "$h${getString(R.string.unit_hour)} $m${getString(R.string.unit_min_short)}"
                else "$m${getString(R.string.unit_min_short)}"
            }
        }
        setText(R.id.screenTime, st)
        setText(R.id.autoStart, getString(if (Config.autoStart) R.string.val_on else R.string.val_off))
        setText(R.id.customCount, "${Config.customBlocked.size} ${getString(R.string.unit_items)}")
        setText(R.id.appVersion, BuildConfig.VERSION_NAME)

        val active = VpnService.prepare(this) == null
        setText(R.id.statusValue, getString(if (active) R.string.val_active else R.string.val_off))
        findViewById<android.view.View>(R.id.enableBtn).visibility =
            if (active && isAccessibilityEnabled() && Settings.canDrawOverlays(this))
                android.view.View.GONE else android.view.View.VISIBLE
    }
}
