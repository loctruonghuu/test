package com.zek.mictsplus.edge

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.zek.mictsplus.Prefs

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            if (Prefs.isEdgeEnabled(context) && Settings.canDrawOverlays(context)) {
                EdgeSwipeService.start(context)
            }
        }
    }
}
