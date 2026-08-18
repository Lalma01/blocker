package com.psblock.app

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent

/**
 * Device-admin / device-owner receiver. Acts as the admin component for anti-tamper:
 * see [DevicePolicy]. Provision as device owner with:
 *   adb shell dpm set-device-owner com.psblock.app/.AdminReceiver
 */
class AdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        Config.init(context)
        DevicePolicy.apply(context)
    }

    override fun onProfileProvisioningComplete(context: Context, intent: Intent) {
        Config.init(context)
        DevicePolicy.apply(context)
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        return context.getString(R.string.admin_disable_warning)
    }
}
