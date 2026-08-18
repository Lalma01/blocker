package com.psblock.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Auto-start on boot / after update — the Windows scheduled-task / Run-key equivalent. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        Config.init(context)
        DevicePolicy.apply(context)
        if (!Config.autoStart) return
        ServiceController.startAll(context)
    }
}
