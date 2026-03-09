package ai.foxtouch.accessibility

import android.accessibilityservice.GestureDescription
import android.graphics.Path

/**
 * Helper for building and dispatching accessibility gestures.
 */
object GestureExecutor {

    fun buildTapGesture(x: Float, y: Float): GestureDescription {
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0, 50)
        return GestureDescription.Builder().addStroke(stroke).build()
    }

    fun buildSwipeGesture(
        startX: Float, startY: Float,
        endX: Float, endY: Float,
        durationMs: Long = 300,
    ): GestureDescription {
        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
        return GestureDescription.Builder().addStroke(stroke).build()
    }

    fun buildLongPressGesture(x: Float, y: Float, durationMs: Long = 1000): GestureDescription {
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
        return GestureDescription.Builder().addStroke(stroke).build()
    }

    fun buildPinchGesture(
        centerX: Float, centerY: Float,
        startDistance: Float, endDistance: Float,
        durationMs: Long = 500,
    ): GestureDescription {
        val builder = GestureDescription.Builder()

        // Finger 1: moves from center-left to further left (or closer)
        val path1 = Path().apply {
            moveTo(centerX - startDistance / 2, centerY)
            lineTo(centerX - endDistance / 2, centerY)
        }
        builder.addStroke(GestureDescription.StrokeDescription(path1, 0, durationMs))

        // Finger 2: moves from center-right to further right (or closer)
        val path2 = Path().apply {
            moveTo(centerX + startDistance / 2, centerY)
            lineTo(centerX + endDistance / 2, centerY)
        }
        builder.addStroke(GestureDescription.StrokeDescription(path2, 0, durationMs))

        return builder.build()
    }

    /**
     * Dispatch a gesture via the accessibility service and suspend until complete.
     */
    suspend fun dispatch(gesture: GestureDescription): Boolean {
        val service = AccessibilityBridge.requireServiceInternal()
        return service.dispatchGestureSuspend(gesture)
    }
}
