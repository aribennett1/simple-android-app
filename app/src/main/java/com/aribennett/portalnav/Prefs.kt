package com.aribennett.portalnav

import android.content.Context

object Prefs {
    private const val NAME = "portal_nav"
    private const val KEY_X = "cluster_x"
    private const val KEY_Y = "cluster_y"
    private const val KEY_SIZE = "button_size_dp"
    private const val KEY_AUTOSTART = "autostart"
    private const val KEY_OVERLAY = "overlay"
    private const val KEY_LAST_SYNC_TIME = "last_sync_time"
    private const val KEY_LAST_SYNC_SUMMARY = "last_sync_summary"
    private const val KEY_BRIGHTNESS_DIMMED = "brightness_dimmed"

    fun buttonSizeDp(context: Context): Int {
        return prefs(context).getInt(KEY_SIZE, 88)
    }

    fun setButtonSizeDp(context: Context, sizeDp: Int) {
        prefs(context).edit().putInt(KEY_SIZE, sizeDp.coerceIn(64, 128)).apply()
    }

    fun position(context: Context): Pair<Int?, Int?> {
        val p = prefs(context)
        val hasX = p.contains(KEY_X)
        val hasY = p.contains(KEY_Y)
        return Pair(if (hasX) p.getInt(KEY_X, 0) else null, if (hasY) p.getInt(KEY_Y, 0) else null)
    }

    fun setPosition(context: Context, x: Int, y: Int) {
        prefs(context).edit().putInt(KEY_X, x).putInt(KEY_Y, y).apply()
    }

    fun resetPosition(context: Context) {
        prefs(context).edit().remove(KEY_X).remove(KEY_Y).apply()
    }

    fun isAutostartEnabled(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_AUTOSTART, true)
    }

    fun setAutostartEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_AUTOSTART, enabled).apply()
    }

    fun isOverlayEnabled(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_OVERLAY, true)
    }

    fun setOverlayEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_OVERLAY, enabled).apply()
    }

    fun setLastSync(context: Context, timeMillis: Long, summary: String) {
        prefs(context).edit()
            .putLong(KEY_LAST_SYNC_TIME, timeMillis)
            .putString(KEY_LAST_SYNC_SUMMARY, summary)
            .apply()
    }

    fun lastSyncSummary(context: Context): String {
        val p = prefs(context)
        val time = p.getLong(KEY_LAST_SYNC_TIME, 0L)
        val summary = p.getString(KEY_LAST_SYNC_SUMMARY, "never") ?: "never"
        return if (time == 0L) summary else "$time $summary"
    }

    fun isBrightnessDimmed(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_BRIGHTNESS_DIMMED, false)
    }

    fun setBrightnessDimmed(context: Context, dimmed: Boolean) {
        prefs(context).edit().putBoolean(KEY_BRIGHTNESS_DIMMED, dimmed).apply()
    }

    private fun prefs(context: Context) = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
}
