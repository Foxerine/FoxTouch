package ai.foxtouch.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Path
import android.hardware.display.DisplayManager
import android.os.Build
import android.util.Log
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/**
 * Android Accessibility Service that provides screen reading and UI automation.
 * Bound/unbound via AccessibilityBridge singleton.
 */
class FoxTouchA11yService : AccessibilityService() {

    companion object {
        private const val TAG = "FoxTouchA11y"
    }

    /**
     * Wrap base context with a display-associated context on Android 11+.
     *
     * Some OEM framework code (notably Samsung OneUI on Android 14/15)
     * internally calls getDisplay() on the service context during
     * takeScreenshot(), which throws UnsupportedOperationException because
     * AccessibilityService is not a visual context.
     *
     * Only applied on Samsung devices to avoid unintended side effects
     * (createDisplayContext changes resource configuration) on OEMs where
     * the stock implementation works fine.
     */
    override fun attachBaseContext(newBase: Context) {
        val isSamsung = Build.MANUFACTURER.equals("samsung", ignoreCase = true)
        if (isSamsung && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val dm = newBase.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
                val display = dm.getDisplay(Display.DEFAULT_DISPLAY)
                if (display != null) {
                    super.attachBaseContext(newBase.createDisplayContext(display))
                    Log.d(TAG, "Samsung device: wrapped base context with display context")
                    return
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to create display context, using default", e)
            }
        }
        super.attachBaseContext(newBase)
    }

    /**
     * Safety net for getDisplay() on OEMs where the base context wrapping
     * is insufficient. Samsung's framework may call getDisplay() on the
     * service reference itself; this override catches the
     * UnsupportedOperationException and returns the default display via
     * DisplayManager instead.
     */
    override fun getDisplay(): Display {
        return try {
            super.getDisplay()
        } catch (e: UnsupportedOperationException) {
            Log.w(TAG, "getDisplay() threw, using DisplayManager fallback")
            val dm = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
            dm.getDisplay(Display.DEFAULT_DISPLAY)
                ?: throw e
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        AccessibilityBridge.bind(this)
        TouchAnimationOverlay.init(this)
    }

    override fun onDestroy() {
        AccessibilityBridge.unbind()
        TouchAnimationOverlay.destroy()
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // We use on-demand UI tree reading rather than event-driven
    }

    override fun onInterrupt() {}

    /**
     * Take a screenshot using the API 30+ method.
     */
    /**
     * Take a screenshot using the API 30+ method.
     * Returns the bitmap on success, throws on failure with a descriptive error code.
     */
    suspend fun takeScreenshotSuspend(): Bitmap = suspendCoroutine { cont ->
        try {
            takeScreenshot(
                Display.DEFAULT_DISPLAY,
                mainExecutor,
                object : TakeScreenshotCallback {
                    override fun onSuccess(screenshot: ScreenshotResult) {
                        val bitmap = Bitmap.wrapHardwareBuffer(
                            screenshot.hardwareBuffer,
                            screenshot.colorSpace,
                        )
                        screenshot.hardwareBuffer.close()
                        if (bitmap != null) {
                            cont.resume(bitmap)
                        } else {
                            cont.resumeWithException(
                                RuntimeException("takeScreenshot: wrapHardwareBuffer returned null"),
                            )
                        }
                    }

                    override fun onFailure(errorCode: Int) {
                        val reason = when (errorCode) {
                            1 -> "INTERNAL_ERROR"
                            2 -> "NO_ACCESSIBILITY_ACCESS"
                            3 -> "INTERVAL_TIME_SHORT"
                            4 -> "INVALID_DISPLAY"
                            5 -> "INVALID_WINDOW"
                            else -> "UNKNOWN($errorCode)"
                        }
                        cont.resumeWithException(
                            RuntimeException("takeScreenshot failed: $reason (code=$errorCode)"),
                        )
                    }
                },
            )
        } catch (e: Exception) {
            cont.resumeWithException(
                RuntimeException("takeScreenshot call threw: ${e.message}", e),
            )
        }
    }

    /**
     * Dispatch a gesture (swipe, etc.) and wait for completion.
     */
    suspend fun dispatchGestureSuspend(gesture: GestureDescription): Boolean =
        suspendCoroutine { cont ->
            dispatchGesture(
                gesture,
                object : GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) {
                        cont.resume(true)
                    }

                    override fun onCancelled(gestureDescription: GestureDescription?) {
                        cont.resume(false)
                    }
                },
                null,
            )
        }

    /**
     * Build a swipe gesture from point A to point B.
     */
    fun buildSwipeGesture(
        startX: Float, startY: Float,
        endX: Float, endY: Float,
        durationMs: Long,
    ): GestureDescription {
        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
        return GestureDescription.Builder().addStroke(stroke).build()
    }
}
