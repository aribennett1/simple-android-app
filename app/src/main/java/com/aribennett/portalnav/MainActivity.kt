package com.aribennett.portalnav

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(24), dp(24), dp(24))
        }

        root.addView(TextView(this).apply {
            text = "Portal Nav"
            textSize = 26f
            gravity = Gravity.CENTER
        })

        root.addView(TextView(this).apply {
            textSize = 18f
            text = diagnostics()
            gravity = Gravity.CENTER
        })

        setContentView(root)
        startAppliance()
    }

    override fun onResume() {
        super.onResume()
        startAppliance()
    }

    private fun startAppliance() {
        startActivity(Intent(this, SlideshowActivity::class.java))
    }

    private fun diagnostics(): String {
        return "Cached photos: ${PhotoStore.photoCount(this)}\nLast sync: ${Prefs.lastSyncSummary(this)}"
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
