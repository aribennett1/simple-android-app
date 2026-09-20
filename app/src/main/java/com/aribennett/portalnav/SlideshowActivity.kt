package com.aribennett.portalnav

import android.app.Activity
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import java.io.File

class SlideshowActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private val controlsHandler = Handler(Looper.getMainLooper())
    private lateinit var image: ImageView
    private lateinit var emptyText: TextView
    private lateinit var controls: LinearLayout
    private lateinit var countText: TextView
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
        controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(6), dp(10), dp(6))
            setBackgroundColor(0x99000000.toInt())
        }
        controls.addView(Button(this).apply {
            text = "SYNC"
            textSize = 12f
            setOnClickListener {
                PhotoSync.request(this@SlideshowActivity, "manual-button", toast = true)
            }
        }, LinearLayout.LayoutParams(dp(86), dp(44)))
        countText = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 13f
            setPadding(dp(10), 0, 0, 0)
        }
        controls.addView(countText, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        setContentView(FrameLayout(this).apply {
            addView(image, FrameLayout.LayoutParams(-1, -1))
            addView(emptyText, FrameLayout.LayoutParams(-1, -1))
            addView(controls, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                android.view.Gravity.BOTTOM or android.view.Gravity.START
            ).apply {
                leftMargin = dp(12)
                bottomMargin = dp(12)
            })
        })
        current = this
        Log.i(TAG, "Slideshow started")
        PhotoSyncScheduler.start(this)
        rescan()
        showControlsTemporarily()
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
        showControlsTemporarily()
    }

    override fun onPause() {
        handler.removeCallbacksAndMessages(null)
        controlsHandler.removeCallbacksAndMessages(null)
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
        updateCount()
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

    private fun updateCount() {
        countText.text = "Saved: ${PhotoStore.photoCount(this)}"
    }

    private fun showControlsTemporarily() {
        controls.visibility = View.VISIBLE
        controlsHandler.removeCallbacksAndMessages(null)
        controlsHandler.postDelayed({
            controls.visibility = View.GONE
        }, 3_000L)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val TAG = "PortalNavPhotos"
        @Volatile private var current: SlideshowActivity? = null

        fun rescanIfVisible() {
            current?.runOnUiThread { current?.rescan() }
        }

        fun updateCountIfVisible() {
            current?.runOnUiThread { current?.updateCount() }
        }
    }
}
