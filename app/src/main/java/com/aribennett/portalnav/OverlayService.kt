package com.aribennett.portalnav

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.Toast
import kotlin.math.roundToInt

class OverlayService : Service() {
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_SCREEN_ON || intent.action == Intent.ACTION_USER_PRESENT) {
                Log.i(TAG, "Screen woke; restoring slideshow")
                startSlideshow()
                showTemporarily()
            }
        }
    }
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var windowManager: WindowManager
    private var cluster: LinearLayout? = null
    private var params: WindowManager.LayoutParams? = null
    private var downRawX = 0f
    private var downRawY = 0f
    private var startX = 0
    private var startY = 0
    private var dragging = false

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        startForeground(1, notification())
        registerReceiver(screenReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        })
        PhotoSyncScheduler.start(this)
        startSlideshow()
        if (Prefs.isOverlayEnabled(this)) {
            showOverlay()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SHOW_TEMP -> showTemporarily()
            ACTION_HIDE -> removeOverlay()
            else -> if (Prefs.isOverlayEnabled(this) && cluster == null) showTemporarily()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        removeOverlay()
        runCatching { unregisterReceiver(screenReceiver) }
        super.onDestroy()
    }

    private fun showOverlay() {
        if (!Settings.canDrawOverlays(this) || cluster != null) return

        val sizePx = dp(Prefs.buttonSizeDp(this))
        val gapPx = dp(8)
        val view = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(gapPx, gapPx, gapPx, gapPx)
            alpha = 0.94f
            addView(makeButton(ButtonKind.BACK, 0xFF1565C0.toInt(), sizePx) { PortalAccessibilityService.goBack() })
            addView(makeButton(ButtonKind.POWER, 0xFF2E7D32.toInt(), sizePx) { PortalAccessibilityService.lockScreen() })
            setOnTouchListener(clusterTouchListener)
        }

        val saved = Prefs.position(this)
        val display = resources.displayMetrics
        val defaultX = saved.first ?: dp(16)
        val defaultY = saved.second ?: (display.heightPixels - sizePx - dp(140)).coerceAtLeast(dp(16))

        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.LEFT
            x = defaultX
            y = defaultY
        }

        cluster = view
        params = layoutParams
        windowManager.addView(view, layoutParams)
    }

    private fun showTemporarily() {
        if (Prefs.isOverlayEnabled(this) && cluster == null) showOverlay()
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({ removeOverlay() }, 3_000L)
    }

    private fun removeOverlay() {
        cluster?.let { windowManager.removeView(it) }
        cluster = null
        params = null
    }

    private fun startSlideshow() {
        startActivity(Intent(this, SlideshowActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun makeButton(kind: ButtonKind, color: Int, sizePx: Int, action: () -> Boolean): View {
        return IconButtonView(this, kind, color).apply {
            layoutParams = LinearLayout.LayoutParams(sizePx, sizePx).apply {
                marginEnd = dp(8)
            }
            setOnClickListener {
                if (!action()) {
                    Toast.makeText(context, "Enable Portal Nav accessibility", Toast.LENGTH_SHORT).show()
                }
            }
            setOnTouchListener(clusterTouchListener)
        }
    }

    private val clusterTouchListener = View.OnTouchListener { _, event ->
        val p = params ?: return@OnTouchListener false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                dragging = false
                downRawX = event.rawX
                downRawY = event.rawY
                startX = p.x
                startY = p.y
                false
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - downRawX
                val dy = event.rawY - downRawY
                if (!dragging && (kotlin.math.abs(dx) > dp(8) || kotlin.math.abs(dy) > dp(8))) {
                    dragging = true
                }
                if (dragging) {
                    p.x = startX + dx.roundToInt()
                    p.y = startY + dy.roundToInt()
                    windowManager.updateViewLayout(cluster, p)
                    true
                } else {
                    false
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (dragging) {
                    Prefs.setPosition(this, p.x, p.y)
                    true
                } else {
                    false
                }
            }
            else -> false
        }
    }

    private fun notification(): Notification {
        val channelId = "portal_nav_overlay"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Portal Nav", NotificationManager.IMPORTANCE_MIN)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, channelId)
                .setContentTitle("Portal Nav")
                .setContentText("Portal appliance controls are running")
                .setSmallIcon(android.R.drawable.ic_menu_revert)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle("Portal Nav")
                .setContentText("Portal appliance controls are running")
                .setSmallIcon(android.R.drawable.ic_menu_revert)
                .build()
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

    private enum class ButtonKind { BACK, POWER }

    private class IconButtonView(
        context: android.content.Context,
        private val kind: ButtonKind,
        private val color: Int
    ) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            paint.style = Paint.Style.FILL
            paint.color = color
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)

            val cx = width / 2f
            val cy = height / 2f
            textPaint.textSize = height * 0.2f
            val label = when (kind) {
                ButtonKind.BACK -> "BACK"
                ButtonKind.POWER -> "OFF"
            }
            canvas.drawText(label, cx, height * 0.35f, textPaint)

            paint.color = Color.WHITE
            paint.style = Paint.Style.STROKE
            paint.strokeCap = Paint.Cap.ROUND
            paint.strokeJoin = Paint.Join.ROUND
            paint.strokeWidth = height * 0.08f

            when (kind) {
                ButtonKind.BACK -> {
                    val left = width * 0.34f
                    val right = width * 0.66f
                    canvas.drawLine(left, cy + height * 0.14f, right, cy + height * 0.14f, paint)
                    canvas.drawLine(left, cy + height * 0.14f, width * 0.48f, cy - height * 0.04f, paint)
                    canvas.drawLine(left, cy + height * 0.14f, width * 0.48f, cy + height * 0.32f, paint)
                }
                ButtonKind.POWER -> {
                    canvas.drawArc(width * 0.34f, cy - height * 0.02f, width * 0.66f, cy + height * 0.3f, 130f, 280f, false, paint)
                    canvas.drawLine(cx, cy - height * 0.06f, cx, cy + height * 0.16f, paint)
                }
            }
        }
    }

    companion object {
        private const val TAG = "PortalNav"
        const val ACTION_SHOW_TEMP = "com.aribennett.portalnav.SHOW_TEMP"
        const val ACTION_HIDE = "com.aribennett.portalnav.HIDE"
    }
}
