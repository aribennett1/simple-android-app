package com.aribennett.portalnav

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.exifinterface.media.ExifInterface
import java.io.File

class SlideshowActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private val controlsHandler = Handler(Looper.getMainLooper())
    private val topBarHandler = Handler(Looper.getMainLooper())
    private lateinit var image: ImageView
    private lateinit var emptyText: TextView
    private lateinit var topBar: LinearLayout
    private lateinit var countText: TextView
    private var photos: List<File> = emptyList()
    private var index = 0
    private var currentPhotoName: String? = null
    private var touchStartY = 0f

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
        topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(6), dp(10), dp(6))
            setBackgroundColor(0x99000000.toInt())
            visibility = View.GONE
        }
        topBar.addView(Button(this).apply {
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
        topBar.addView(countText, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        topBar.addView(Button(this).apply {
            text = "⚙"
            textSize = 22f
            setOnClickListener {
                openSettings()
            }
        }, LinearLayout.LayoutParams(dp(56), dp(48)))
        setContentView(FrameLayout(this).apply {
            addView(image, FrameLayout.LayoutParams(-1, -1))
            addView(emptyText, FrameLayout.LayoutParams(-1, -1))
            addView(topBar, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                android.view.Gravity.TOP
            ))
        })
        current = this
        Log.i(TAG, "Slideshow started")
        PhotoSyncScheduler.start(this)
        rescan()
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
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> touchStartY = event.rawY
            MotionEvent.ACTION_UP -> {
                val edge = dp(48)
                val distance = event.rawY - touchStartY
                if (touchStartY <= edge && distance >= dp(56)) {
                    showTopBarTemporarily()
                }
            }
        }
        return super.dispatchTouchEvent(event)
    }

    override fun onPause() {
        handler.removeCallbacksAndMessages(null)
        topBarHandler.removeCallbacksAndMessages(null)
        if (current === this) current = null
        super.onPause()
    }

    override fun onDestroy() {
        if (current === this) current = null
        super.onDestroy()
    }

    fun rescan() {
        val previousPhotoName = currentPhotoName
        photos = PhotoStore.photos(this)
        index = previousPhotoName
            ?.let { name -> photos.indexOfFirst { it.name == name } }
            ?.takeIf { it >= 0 }
            ?: 0
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
            val bitmap = decodeOrientedBitmap(file)
            index = (index + 1) % photos.size
            if (bitmap != null) {
                currentPhotoName = file.name
                image.setImageBitmap(bitmap)
                return
            }
            Log.w(TAG, "Skipping broken image: ${file.name}")
            attempts++
        }
        image.setImageDrawable(null)
        emptyText.visibility = View.VISIBLE
    }

    private fun decodeOrientedBitmap(file: File): Bitmap? {
        val orientation = exifOrientation(file)
        val bitmap = decodeSampledBitmap(file, orientation) ?: return null
        return orientBitmap(file, bitmap, orientation)
    }

    private fun decodeSampledBitmap(file: File, orientation: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return BitmapFactory.decodeFile(file.absolutePath)

        val rotated = orientation == ExifInterface.ORIENTATION_ROTATE_90 ||
            orientation == ExifInterface.ORIENTATION_ROTATE_270 ||
            orientation == ExifInterface.ORIENTATION_TRANSPOSE ||
            orientation == ExifInterface.ORIENTATION_TRANSVERSE
        val sourceWidth = if (rotated) bounds.outHeight else bounds.outWidth
        val sourceHeight = if (rotated) bounds.outWidth else bounds.outHeight
        val targetWidth = image.width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels
        val targetHeight = image.height.takeIf { it > 0 } ?: resources.displayMetrics.heightPixels

        return BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSize(sourceWidth, sourceHeight, targetWidth, targetHeight)
        })
    }

    private fun calculateInSampleSize(sourceWidth: Int, sourceHeight: Int, targetWidth: Int, targetHeight: Int): Int {
        var sample = 1
        while (sourceWidth / (sample * 2) >= targetWidth && sourceHeight / (sample * 2) >= targetHeight) {
            sample *= 2
        }
        return sample
    }

    private fun exifOrientation(file: File): Int {
        return runCatching {
            ExifInterface(file.absolutePath).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
    }

    private fun orientBitmap(file: File, bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.setScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.setRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.setRotate(-90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(-90f)
            else -> return bitmap
        }

        return runCatching {
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true).also {
                if (it !== bitmap) bitmap.recycle()
            }
        }.getOrElse {
            Log.w(TAG, "Could not apply EXIF orientation for ${file.name}", it)
            bitmap
        }
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

    private fun updateSyncProgress(done: Int, total: Int) {
        countText.text = "Sync: $done / $total"
        showTopBarTemporarily()
    }

    private fun openSettings() {
        runCatching {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        }.onFailure {
            Log.w(TAG, "Could not open settings", it)
        }
    }

    private fun showTopBarTemporarily() {
        topBar.visibility = View.VISIBLE
        topBarHandler.removeCallbacksAndMessages(null)
        topBarHandler.postDelayed({
            topBar.visibility = View.GONE
        }, 5_000L)
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

        fun updateSyncProgressIfVisible(done: Int, total: Int) {
            current?.runOnUiThread { current?.updateSyncProgress(done, total) }
        }
    }
}
