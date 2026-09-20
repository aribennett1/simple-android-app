package com.aribennett.portalnav

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityEvent

class PortalAccessibilityService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private var state = CallState.IDLE
    private var lastIncomingAt = 0L
    private var answerStartedAt = 0L

    override fun onServiceConnected() {
        instance = this
        startService(Intent(this, OverlayService::class.java))
        Log.i(TAG, "Accessibility service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.packageName?.toString() != AppConfig.WHATSAPP_PACKAGE) return
        inspectWhatsApp()
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        if (instance === this) {
            instance = null
        }
        super.onDestroy()
    }

    private fun inspectWhatsApp() {
        val root = rootInActiveWindow ?: return
        val incoming = WhatsAppUiDetector.detectIncoming(root)
        if (incoming != null) {
            handleIncoming(incoming)
            return
        }

        val active = WhatsAppUiDetector.isActiveCall(root)
        if (active && state != CallState.ACTIVE) {
            state = CallState.ACTIVE
            Log.i(TAG, "WhatsApp call active")
            return
        }

        if (!active && state == CallState.ACTIVE) {
            state = CallState.ENDING
            Log.i(TAG, "WhatsApp call ended; scheduling slideshow resume")
            handler.postDelayed({
                state = CallState.IDLE
                startSlideshow()
            }, 5_000L)
        }
    }

    private fun handleIncoming(incoming: IncomingCallUi) {
        val now = System.currentTimeMillis()
        if (state == CallState.ANSWERING && now - answerStartedAt < 8_000L) return
        if (state == CallState.ACTIVE || now - lastIncomingAt < 5_000L) return
        lastIncomingAt = now
        state = CallState.INCOMING
        Log.i(TAG, "Incoming WhatsApp call detected: ${incoming.caller}")

        if (!ContactsMatcher.isSavedContact(this, incoming.caller)) {
            Log.i(TAG, "Incoming caller not verified as contact; not answering")
            return
        }

        state = CallState.ANSWERING
        answerStartedAt = now
        val swiped = swipeUp(incoming.answerNode)
        Log.i(TAG, "Answer swipe result: $swiped")
        if (swiped) {
            handler.postDelayed({ verifyAnswerProgress() }, 4_000L)
        } else {
            state = CallState.IDLE
        }
    }

    private fun verifyAnswerProgress() {
        val root = rootInActiveWindow
        when {
            WhatsAppUiDetector.isActiveCall(root) -> {
                state = CallState.ACTIVE
                Log.i(TAG, "WhatsApp call active after answer gesture")
            }
            WhatsAppUiDetector.detectIncoming(root) != null -> {
                state = CallState.IDLE
                Log.w(TAG, "WhatsApp still incoming after answer gesture; reset for retry")
            }
            state == CallState.ANSWERING -> {
                state = CallState.IDLE
                Log.i(TAG, "Answer gesture completed without active-call signal; reset for next call")
            }
        }
    }

    private fun swipeUp(node: AccessibilityNodeInfo): Boolean {
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        if (bounds.isEmpty) return false

        val centerX = bounds.centerX().toFloat()
        val startY = bounds.centerY().toFloat()
        val distance = maxOf(bounds.height() * 2.2f, 260f)
        val endY = (startY - distance).coerceAtLeast(80f)
        val path = Path().apply {
            moveTo(centerX, startY)
            lineTo(centerX, endY)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, 450L))
            .build()
        return dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                Log.i(TAG, "Answer swipe completed")
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                Log.w(TAG, "Answer swipe cancelled")
            }
        }, handler)
    }

    private fun startSlideshow() {
        Log.i(TAG, "Resuming slideshow")
        startActivity(Intent(this, SlideshowActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private enum class CallState { IDLE, INCOMING, ANSWERING, ACTIVE, ENDING }

    companion object {
        private const val TAG = "PortalNavWA"

        @Volatile
        private var instance: PortalAccessibilityService? = null

        fun goBack(): Boolean {
            return instance?.performGlobalAction(GLOBAL_ACTION_BACK) == true
        }

        fun lockScreen(): Boolean {
            val service = instance ?: return false
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                service.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
            } else {
                false
            }
        }
    }
}
