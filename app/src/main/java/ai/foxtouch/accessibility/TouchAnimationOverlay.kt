package ai.foxtouch.accessibility

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator

/**
 * Full-screen, non-interactive overlay that renders visual feedback
 * when the agent performs click (ripple) and swipe (trail) gestures.
 *
 * Initialized when the accessibility service connects and overlay
 * permission is granted. Falls back silently if permission is missing.
 */
object TouchAnimationOverlay {

    private var windowManager: WindowManager? = null
    private var animView: AnimationView? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    fun init(context: Context) {
        if (!Settings.canDrawOverlays(context)) return
        if (animView != null) return

        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val view = AnimationView(context)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        )

        wm.addView(view, params)
        windowManager = wm
        animView = view
        OverlayController.register(view)
    }

    fun showClickRipple(x: Float, y: Float) {
        mainHandler.post { animView?.startRipple(x, y) }
    }

    fun showSwipeTrail(startX: Float, startY: Float, endX: Float, endY: Float, durationMs: Long) {
        mainHandler.post { animView?.startSwipeTrail(startX, startY, endX, endY, durationMs) }
    }

    /** Show a persistent highlight border around a target element (for approval preview). */
    fun showHighlight(left: Int, top: Int, right: Int, bottom: Int) {
        mainHandler.post { animView?.setHighlight(left, top, right, bottom) }
    }

    /** Hide the highlight border. */
    fun hideHighlight() {
        mainHandler.post { animView?.clearHighlight() }
    }

    /**
     * Toggle FLAG_KEEP_SCREEN_ON on the overlay window.
     * When enabled, the screen stays on as long as this overlay is visible.
     * Used by AgentForegroundService to keep the screen on while the agent works.
     */
    fun setKeepScreenOn(keepOn: Boolean) {
        mainHandler.post {
            val view = animView ?: return@post
            val wm = windowManager ?: return@post
            val params = view.layoutParams as? WindowManager.LayoutParams ?: return@post
            if (keepOn) {
                params.flags = params.flags or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            } else {
                params.flags = params.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON.inv()
            }
            try { wm.updateViewLayout(view, params) } catch (_: Exception) {}
        }
    }

    fun destroy() {
        mainHandler.post {
            animView?.let {
                OverlayController.unregister(it)
                try { windowManager?.removeView(it) } catch (_: Exception) {}
            }
            animView = null
            windowManager = null
        }
    }

    private class AnimationView(context: Context) : View(context) {

        // ── Ripple state ──────────────────────────────────────────
        private var rippleX = 0f
        private var rippleY = 0f
        private var rippleProgress = 0f
        private var rippleActive = false
        private var rippleAnimator: ValueAnimator? = null

        private val rippleStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF2196F3.toInt() // Material Blue
            style = Paint.Style.STROKE
        }
        private val rippleFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF2196F3.toInt()
            style = Paint.Style.FILL
        }
        // Shadow paints for visibility on both light and dark backgrounds
        private val rippleShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF000000.toInt()
            style = Paint.Style.STROKE
        }

        // ── Highlight state (approval preview) ────────────────────
        private var highlightRect: android.graphics.Rect? = null
        private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF2196F3.toInt() // Material Blue
            style = Paint.Style.STROKE
        }

        fun setHighlight(left: Int, top: Int, right: Int, bottom: Int) {
            highlightRect = android.graphics.Rect(left, top, right, bottom)
            invalidate()
        }

        fun clearHighlight() {
            highlightRect = null
            invalidate()
        }

        // ── Swipe state ───────────────────────────────────────────
        private var swipeStartX = 0f
        private var swipeStartY = 0f
        private var swipeEndX = 0f
        private var swipeEndY = 0f
        private var swipeProgress = 0f
        private var swipeActive = false
        private var swipeAnimator: ValueAnimator? = null

        private val swipeLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF4CAF50.toInt() // Material Green
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }
        private val swipeDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF4CAF50.toInt()
            style = Paint.Style.FILL
        }
        private val swipeShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF000000.toInt()
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }

        companion object {
            private const val RIPPLE_MAX_RADIUS_DP = 120f
            private const val RIPPLE_DURATION_MS = 700L
            private const val SWIPE_DOT_RADIUS_DP = 16f
            private const val SWIPE_LINE_WIDTH_DP = 6f
            private const val RIPPLE_STROKE_WIDTH_DP = 4f
            private const val RIPPLE_DOT_RADIUS_DP = 10f
        }

        fun startRipple(x: Float, y: Float) {
            rippleAnimator?.cancel()
            rippleX = x
            rippleY = y
            rippleActive = true
            rippleAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = RIPPLE_DURATION_MS
                interpolator = DecelerateInterpolator()
                addUpdateListener {
                    rippleProgress = it.animatedValue as Float
                    invalidate()
                }
                addListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: android.animation.Animator) {
                        rippleActive = false
                        invalidate()
                    }
                })
                start()
            }
        }

        fun startSwipeTrail(sx: Float, sy: Float, ex: Float, ey: Float, durationMs: Long) {
            swipeAnimator?.cancel()
            swipeStartX = sx
            swipeStartY = sy
            swipeEndX = ex
            swipeEndY = ey
            swipeActive = true
            // Extend animation slightly past the gesture for a fade-out effect
            swipeAnimator = ValueAnimator.ofFloat(0f, 1.3f).apply {
                duration = durationMs + 300
                interpolator = DecelerateInterpolator(1.5f)
                addUpdateListener {
                    swipeProgress = it.animatedValue as Float
                    invalidate()
                }
                addListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: android.animation.Animator) {
                        swipeActive = false
                        invalidate()
                    }
                })
                start()
            }
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            highlightRect?.let { drawHighlight(canvas, it) }
            if (rippleActive) drawRipple(canvas)
            if (swipeActive) drawSwipeTrail(canvas)
        }

        private fun drawHighlight(canvas: Canvas, rect: android.graphics.Rect) {
            val density = resources.displayMetrics.density
            val padding = (4 * density).toInt()
            val cornerRadius = 8 * density
            highlightPaint.strokeWidth = 3 * density
            highlightPaint.alpha = 200
            val rf = android.graphics.RectF(
                (rect.left - padding).toFloat(),
                (rect.top - padding).toFloat(),
                (rect.right + padding).toFloat(),
                (rect.bottom + padding).toFloat(),
            )
            canvas.drawRoundRect(rf, cornerRadius, cornerRadius, highlightPaint)
        }

        private fun drawRipple(canvas: Canvas) {
            val density = resources.displayMetrics.density
            val maxRadius = RIPPLE_MAX_RADIUS_DP * density
            val radius = maxRadius * rippleProgress
            val alpha = ((1f - rippleProgress) * 220).toInt().coerceIn(0, 255)

            // Shadow ring for dark-background visibility
            rippleShadowPaint.alpha = (alpha * 0.5f).toInt()
            rippleShadowPaint.strokeWidth = (RIPPLE_STROKE_WIDTH_DP + 2) * density
            canvas.drawCircle(rippleX, rippleY, radius, rippleShadowPaint)

            // Inner filled circle
            rippleFillPaint.alpha = (alpha * 0.4f).toInt()
            canvas.drawCircle(rippleX, rippleY, radius * 0.5f, rippleFillPaint)

            // Outer expanding ring
            rippleStrokePaint.alpha = alpha
            rippleStrokePaint.strokeWidth = RIPPLE_STROKE_WIDTH_DP * density
            canvas.drawCircle(rippleX, rippleY, radius, rippleStrokePaint)

            // Center dot — solid white core + colored ring for visibility on any background
            val dotAlpha = ((1f - rippleProgress * 0.7f) * 255).toInt().coerceIn(0, 255)
            rippleShadowPaint.alpha = dotAlpha
            rippleShadowPaint.style = Paint.Style.FILL
            canvas.drawCircle(rippleX, rippleY, (RIPPLE_DOT_RADIUS_DP + 2) * density, rippleShadowPaint)
            rippleShadowPaint.style = Paint.Style.STROKE
            rippleFillPaint.alpha = dotAlpha
            canvas.drawCircle(rippleX, rippleY, RIPPLE_DOT_RADIUS_DP * density, rippleFillPaint)
        }

        private fun drawSwipeTrail(canvas: Canvas) {
            val density = resources.displayMetrics.density
            val drawProgress = swipeProgress.coerceAtMost(1f)
            val fadeProgress = ((swipeProgress - 0.8f) / 0.5f).coerceIn(0f, 1f)
            val alpha = ((1f - fadeProgress) * 240).toInt().coerceIn(0, 255)

            val currentX = swipeStartX + (swipeEndX - swipeStartX) * drawProgress
            val currentY = swipeStartY + (swipeEndY - swipeStartY) * drawProgress

            // Shadow trail for visibility on light/dark backgrounds
            swipeShadowPaint.alpha = (alpha * 0.4f).toInt()
            swipeShadowPaint.strokeWidth = (SWIPE_LINE_WIDTH_DP + 3) * density
            canvas.drawLine(swipeStartX, swipeStartY, currentX, currentY, swipeShadowPaint)

            // Trail line from start to current position
            swipeLinePaint.alpha = alpha
            swipeLinePaint.strokeWidth = SWIPE_LINE_WIDTH_DP * density
            canvas.drawLine(swipeStartX, swipeStartY, currentX, currentY, swipeLinePaint)

            // Moving dot at head of trail (shadow + fill)
            swipeShadowPaint.style = Paint.Style.FILL
            swipeShadowPaint.alpha = (alpha * 0.4f).toInt()
            canvas.drawCircle(currentX, currentY, (SWIPE_DOT_RADIUS_DP + 2) * density, swipeShadowPaint)
            swipeShadowPaint.style = Paint.Style.STROKE
            swipeDotPaint.alpha = alpha
            canvas.drawCircle(currentX, currentY, SWIPE_DOT_RADIUS_DP * density, swipeDotPaint)

            // Start point indicator
            swipeDotPaint.alpha = (alpha * 0.5f).toInt()
            canvas.drawCircle(swipeStartX, swipeStartY, RIPPLE_DOT_RADIUS_DP * density, swipeDotPaint)
        }
    }
}
