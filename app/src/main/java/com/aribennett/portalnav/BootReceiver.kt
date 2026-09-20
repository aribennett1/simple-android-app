package com.aribennett.portalnav

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!Prefs.isAutostartEnabled(context)) return

        context.startActivity(Intent(context, SlideshowActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        PhotoSync.request(context, "boot")
    }
}
