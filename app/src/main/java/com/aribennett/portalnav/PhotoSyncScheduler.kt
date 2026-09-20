package com.aribennett.portalnav

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log

object PhotoSyncScheduler {
    private const val TAG = "PortalNavPhotos"
    private val handler = Handler(Looper.getMainLooper())
    private var scheduled = false

    fun start(context: Context) {
        if (scheduled) return
        scheduled = true
        val appContext = context.applicationContext
        Log.i(TAG, "Periodic sync scheduler started")
        scheduleNext(appContext)
    }

    private fun scheduleNext(context: Context) {
        handler.postDelayed({
            PhotoSync.request(context, "scheduled")
            scheduleNext(context)
        }, AppConfig.SYNC_INTERVAL_MS)
    }
}
