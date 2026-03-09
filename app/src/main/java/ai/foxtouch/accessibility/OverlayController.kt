package ai.foxtouch.accessibility

import android.os.Looper
import android.view.View
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Controls visibility of FoxTouch overlay views during screenshot capture.
 *
 * Overlay providers ([ai.foxtouch.ui.overlay.FloatingBubbleService],
 * [ai.foxtouch.assistant.FoxTouchAssistantSession]) register their root views
 * here. [withOverlaysHidden] temporarily hides all registered views so that
 * [AccessibilityBridge.captureScreenshotBase64] captures only the underlying
 * app content without FoxTouch UI.
 *
 * Thread safety: [register] and [unregister] must be called on the main thread.
 * The view set is only mutated and read on the main thread.
 */
object OverlayController {

    private val overlayViews = mutableSetOf<View>()

    /**
     * Time to wait after hiding overlays for the compositor to render a clean frame.
     * At 60fps one frame is ~16ms; 50ms provides a comfortable margin.
     */
    private const val FRAME_SETTLE_MS = 50L

    fun register(view: View) {
        assertMainThread()
        overlayViews.add(view)
    }

    fun unregister(view: View) {
        assertMainThread()
        overlayViews.remove(view)
    }

    /**
     * Execute [block] with all registered overlay views temporarily hidden.
     *
     * Switches to the main thread to set [View.INVISIBLE], waits for the
     * compositor to render a frame without overlays, runs [block], then
     * restores visibility. Views that were unregistered during [block]
     * (e.g., dismissed approval card) are not restored.
     */
    suspend fun <T> withOverlaysHidden(block: suspend () -> T): T {
        val hidden = mutableListOf<View>()
        withContext(Dispatchers.Main.immediate) {
            for (view in overlayViews) {
                if (view.visibility == View.VISIBLE) {
                    view.visibility = View.INVISIBLE
                    hidden.add(view)
                }
            }
        }
        if (hidden.isNotEmpty()) {
            delay(FRAME_SETTLE_MS)
        }
        return try {
            block()
        } finally {
            if (hidden.isNotEmpty()) {
                withContext(Dispatchers.Main.immediate) {
                    for (view in hidden) {
                        if (view in overlayViews) {
                            view.visibility = View.VISIBLE
                        }
                    }
                }
            }
        }
    }

    private fun assertMainThread() {
        check(Looper.myLooper() == Looper.getMainLooper()) {
            "OverlayController must be called on the main thread"
        }
    }
}
