package com.aribennett.portalnav

import android.app.Activity
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import java.io.File

class SlideshowActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var image: ImageView
    private lateinit var emptyText: TextView
    private var photos: List<File> = emptyList()
    private var index = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
        enterImmersive()

        image = ImageView(this).apply {
            setBackgroundColor(Color.BLACK)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        emptyText = TextView(this).apply {
            text = "No cached photos"
            setTextColor(Color.WHITE)
            textSize = 24f
            gravity = android.view.Gravity.CENTER
            setBackgroundColor(Color.BLACK)
        }
        setContentView(FrameLayout(this).apply {
            addView(image, FrameLayout.LayoutParams(-1, -1))
            addView(emptyText, FrameLayout.LayoutParams(-1, -1))
        })
        current = this
        Log.i(TAG, "Slideshow started")
        rescan()
        showMaintenanceButtonsTemporarily()
    }

    override fun onResume() {
        super.onResume()
        current = this
        enterImmersive()
        scheduleNext()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) enterImmersive()
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        enterImmersive()
        showMaintenanceButtonsTemporarily()
    }

    override fun onPause() {
        handler.removeCallbacksAndMessages(null)
        if (current === this) current = null
        super.onPause()
    }

    override fun onDestroy() {
        if (current === this) current = null
        super.onDestroy()
    }

    fun rescan() {
        photos = PhotoStore.photos(this)
        index = 0
        Log.i(TAG, "Slideshow cache count: ${photos.size}")
        showCurrent()
    }

    private fun showCurrent() {
        if (photos.isEmpty()) {
            image.setImageDrawable(null)
            emptyText.visibility = View.VISIBLE
            return
        }
        emptyText.visibility = View.GONE
        var attempts = 0
        while (attempts < photos.size) {
            val file = photos[index % photos.size]
            val bitmap = BitmapFactory.decodeFile(file.absolutePath)
            index = (index + 1) % photos.size
            if (bitmap != null) {
                image.setImageBitmap(bitmap)
                return
            }
            Log.w(TAG, "Skipping broken image: ${file.name}")
            attempts++
        }
        image.setImageDrawable(null)
        emptyText.visibility = View.VISIBLE
    }

    private fun scheduleNext() {
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({
            showCurrent()
            scheduleNext()
        }, AppConfig.SLIDESHOW_INTERVAL_MS)
    }

    private fun enterImmersive() {
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
    }

    private fun showMaintenanceButtonsTemporarily() {
        startService(Intent(this, OverlayService::class.java).setAction(OverlayService.ACTION_SHOW_TEMP))
    }

    companion object {
        private const val TAG = "PortalNavPhotos"
        @Volatile private var current: SlideshowActivity? = null

        fun rescanIfVisible() {
            current?.runOnUiThread { current?.rescan() }
        }
    }
}
