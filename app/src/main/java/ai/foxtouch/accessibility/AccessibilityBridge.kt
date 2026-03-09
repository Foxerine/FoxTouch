package ai.foxtouch.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.accessibility.AccessibilityWindowInfo
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import ai.foxtouch.data.preferences.AppSettings
import ai.foxtouch.data.preferences.ScreenshotMode
import ai.foxtouch.ime.FoxTouchIME
import android.view.inputmethod.InputMethodManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream

/**
 * Captured screenshot with original screen dimensions for coordinate mapping.
 */
data class CapturedScreen(
    val bitmap: Bitmap,
    val originalWidth: Int,
    val originalHeight: Int,
)

data class ElementOverlayInfo(
    val id: Int,
    val className: String,
    val text: String?,
    val bounds: android.graphics.Rect,
    val isClickable: Boolean,
    val isScrollable: Boolean,
    val isEditable: Boolean,
)

/**
 * Global bridge between accessibility service and the rest of the app.
 *
 * Thread safety: [readUITree] and all node-based operations are synchronized
 * on [nodeLock] to prevent concurrent modification of [nodeMap].
 */
object AccessibilityBridge {
    private var _service: FoxTouchA11yService? = null

    /**
     * Node ID → AccessibilityNodeInfo mapping, rebuilt each [readUITree] call.
     * Monotonically increasing [treeVersion] tracks freshness — stale node
     * operations are rejected instead of silently acting on recycled nodes.
     */
    private var nodeMap = mutableMapOf<Int, AccessibilityNodeInfo>()
    private var nextNodeId = 1
    private var treeVersion = 0L
    private val nodeLock = Any()

    val isServiceConnected: Boolean get() = _service != null

    fun bind(service: FoxTouchA11yService) { _service = service }
    fun unbind() { _service = null }

    private fun requireService(): FoxTouchA11yService =
        _service ?: error("Accessibility Service is not enabled. Please enable it in Settings.")

    /** Exposed for GestureExecutor within the accessibility package. */
    internal fun requireServiceInternal(): FoxTouchA11yService = requireService()

    private const val TAG = "AccessibilityBridge"
    private const val OWN_PACKAGE = "ai.foxtouch"

    fun readUITree(): String = synchronized(nodeLock) {
        val service = requireService()
        nodeMap.clear()
        nextNodeId = 1
        treeVersion++

        val windows = service.windows
        if (windows.isNullOrEmpty()) {
            val root = service.rootInActiveWindow ?: return "(No active window)"
            return UITreeParser.parse(root, nodeMap, ::allocateNodeId)
        }

        // Sort windows: focused app windows first, then other apps, then system
        val sortedWindows = windows.sortedWith(
            compareByDescending<AccessibilityWindowInfo> { it.isFocused }
                .thenBy { windowTypePriority(it.type) },
        )

        val sb = StringBuilder()
        for (window in sortedWindows) {
            val root = window.root ?: continue
            if (root.packageName?.toString() == OWN_PACKAGE) continue
            sb.append(UITreeParser.parse(root, nodeMap, ::allocateNodeId))
        }

        sb.toString().ifBlank { "(No accessible UI found)" }
    }

    /** Lower value = higher priority. Application windows first. */
    private fun windowTypePriority(type: Int): Int = when (type) {
        AccessibilityWindowInfo.TYPE_APPLICATION -> 0
        AccessibilityWindowInfo.TYPE_INPUT_METHOD -> 1
        AccessibilityWindowInfo.TYPE_SYSTEM -> 2
        AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY -> 3
        else -> 4
    }

    private fun allocateNodeId(): Int = nextNodeId++

    /**
     * Capture a full-display screenshot as base64 JPEG.
     *
     * Supports two capture backends:
     * - **Accessibility** (default): Uses [AccessibilityService.takeScreenshot()].
     *   Some apps (WeChat 8.0.52+) block this for non-system accessibility services.
     * - **MediaProjection** (fallback): Uses [ScreenCaptureManager]. Works at the
     *   system compositor level and cannot be blocked by apps (except FLAG_SECURE).
     *
     * The backend is selected per-app via [AppSettings.getScreenshotMode].
     * All registered FoxTouch overlay views are temporarily hidden via
     * [OverlayController] before capture.
     *
     * @param appSettings Settings to check per-app screenshot mode. If null, uses Accessibility.
     */
    /**
     * Capture a screenshot and return it as a base64 JPEG string.
     * Convenience method that calls [captureScreenBitmap] + [encodeBitmapToBase64].
     */
    suspend fun captureScreenshotBase64(appSettings: AppSettings? = null): String {
        val captured = captureScreenBitmap(appSettings)
        val base64 = encodeBitmapToBase64(captured.bitmap)
        captured.bitmap.recycle()
        return base64
    }

    /**
     * Capture a screenshot and return the scaled bitmap along with original screen dimensions.
     * The bitmap is scaled to max 1080px wide. Caller is responsible for recycling the bitmap.
     */
    suspend fun captureScreenBitmap(appSettings: AppSettings? = null): CapturedScreen {
        val service = requireService()

        // Determine which backend to use based on the foreground app
        val foregroundPackage = getForegroundPackage()
        Log.d(TAG, "Foreground package: $foregroundPackage")

        val useMediaProjection = if (appSettings != null && foregroundPackage != null) {
            val mode = appSettings.getScreenshotMode(foregroundPackage)
            Log.d(TAG, "Screenshot mode for $foregroundPackage: $mode, authorized=${ScreenCaptureManager.isAuthorized}")
            mode == ScreenshotMode.MEDIA_PROJECTION && ScreenCaptureManager.isAuthorized
        } else {
            false
        }

        val rawBitmap = if (useMediaProjection) {
            Log.d(TAG, "Using MediaProjection for screenshot")
            try {
                ScreenCaptureManager.captureFrame(service.applicationContext)
            } catch (e: Exception) {
                Log.e(TAG, "MediaProjection capture failed, falling back to Accessibility", e)
                OverlayController.withOverlaysHidden {
                    service.takeScreenshotSuspend()
                }
            }
        } else {
            Log.d(TAG, "Using Accessibility for screenshot")
            try {
                OverlayController.withOverlaysHidden {
                    service.takeScreenshotSuspend()
                }
            } catch (e: Exception) {
                // Auto-fallback to MediaProjection if Accessibility screenshot fails
                // (e.g. Samsung OneUI display context issue, app blocking, etc.)
                if (ScreenCaptureManager.isAuthorized) {
                    Log.w(TAG, "Accessibility screenshot failed, falling back to MediaProjection", e)
                    ScreenCaptureManager.captureFrame(service.applicationContext)
                } else {
                    throw e
                }
            }
        }

        val originalWidth = rawBitmap.width
        val originalHeight = rawBitmap.height

        val scaled = if (rawBitmap.width > 1080) {
            val ratio = 1080f / rawBitmap.width
            val newHeight = (rawBitmap.height * ratio).toInt()
            Bitmap.createScaledBitmap(rawBitmap, 1080, newHeight, true).also {
                if (it !== rawBitmap) rawBitmap.recycle()
            }
        } else {
            rawBitmap
        }

        // Hardware bitmaps (from wrapHardwareBuffer) cannot be drawn on with Canvas.
        // Convert to software-backed ARGB_8888 so ScreenAnnotator can draw overlays.
        val softBitmap = if (scaled.config == Bitmap.Config.HARDWARE) {
            scaled.copy(Bitmap.Config.ARGB_8888, true).also {
                if (it !== scaled) scaled.recycle()
            } ?: throw IllegalStateException("Failed to convert hardware bitmap to software bitmap")
        } else {
            scaled
        }

        return CapturedScreen(softBitmap, originalWidth, originalHeight)
    }

    /** Public accessor for diagnostic output in ReadScreenTool. */
    fun getForegroundPackageName(): String? = getForegroundPackage()

    /**
     * Get the package name of the current foreground app from the accessibility windows.
     *
     * Tries multiple strategies:
     * 1. Focused application window's root node package
     * 2. Any application window's root node package (if none focused)
     * 3. Root in active window (fallback for older APIs)
     */
    private fun getForegroundPackage(): String? {
        val service = _service ?: return null

        // Strategy 1 & 2: Use accessibility windows
        val windows = service.windows
        if (!windows.isNullOrEmpty()) {
            val appWindows = windows.filter {
                it.type == AccessibilityWindowInfo.TYPE_APPLICATION
            }

            // Try focused window first
            for (w in appWindows) {
                if (w.isFocused) {
                    val pkg = w.root?.packageName?.toString()
                    if (pkg != null && pkg != OWN_PACKAGE) return pkg
                }
            }

            // If no focused window found, try any app window (excluding our own)
            for (w in appWindows) {
                val pkg = w.root?.packageName?.toString()
                if (pkg != null && pkg != OWN_PACKAGE) return pkg
            }
        }

        // Strategy 3: Fallback to rootInActiveWindow
        return service.rootInActiveWindow?.packageName?.toString()
            ?.takeIf { it != OWN_PACKAGE }
    }

    /** Encode a bitmap as base64 JPEG. Does NOT recycle the bitmap — caller manages lifecycle. */
    fun encodeBitmapToBase64(bitmap: Bitmap): String {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 75, stream)
        return Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
    }

    /**
     * Return structured overlay info for all current nodes.
     * Must be called AFTER readUITree() which populates the nodeMap.
     */
    fun getElementOverlayInfos(clickableOnly: Boolean = false): List<ElementOverlayInfo> = synchronized(nodeLock) {
        nodeMap.mapNotNull { (id, node) ->
            val bounds = android.graphics.Rect()
            node.getBoundsInScreen(bounds)
            // Skip zero-size or off-screen elements
            if (bounds.width() <= 0 || bounds.height() <= 0) return@mapNotNull null
            val isClickable = node.isClickable || node.isLongClickable
            val isScrollable = node.isScrollable
            val isEditable = node.isEditable
            if (clickableOnly && !isClickable && !isScrollable && !isEditable) return@mapNotNull null
            ElementOverlayInfo(
                id = id,
                className = node.className?.toString()?.substringAfterLast('.') ?: "",
                text = node.text?.toString()?.take(30) ?: node.contentDescription?.toString()?.take(30),
                bounds = bounds,
                isClickable = isClickable,
                isScrollable = isScrollable,
                isEditable = isEditable,
            )
        }
    }

    /** Get the screen bounds of an element by its ID, or null if not found. */
    fun getElementBounds(elementId: Int): android.graphics.Rect? = synchronized(nodeLock) {
        val node = nodeMap[elementId] ?: return null
        val rect = android.graphics.Rect()
        node.getBoundsInScreen(rect)
        rect
    }

    fun clickNode(elementId: Int): Boolean = synchronized(nodeLock) {
        val node = nodeMap[elementId]
            ?: error("Element [$elementId] not found. Call read_screen first to refresh the UI tree.")

        // Get bounds for touch animation
        val rect = android.graphics.Rect()
        node.getBoundsInScreen(rect)
        TouchAnimationOverlay.showClickRipple(
            rect.centerX().toFloat(),
            rect.centerY().toFloat(),
        )

        // Try clicking the node itself, or walk up to find a clickable parent
        var current: AccessibilityNodeInfo? = node
        while (current != null) {
            if (current.isClickable) {
                return current.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
            current = current.parent
        }
        // Fallback: try clicking even if not marked clickable
        node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    /**
     * Tap at specific screen coordinates using dispatchGesture.
     * Shows ripple animation at the tap point.
     */
    suspend fun clickCoordinate(x: Float, y: Float) {
        TouchAnimationOverlay.showClickRipple(x, y)
        val gesture = GestureExecutor.buildTapGesture(x, y)
        GestureExecutor.dispatch(gesture)
    }

    fun typeText(text: String): Boolean {
        val service = requireService()
        val root = service.rootInActiveWindow ?: return false
        val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return false

        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        return focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    /**
     * Last-resort typing: write text to clipboard, then perform PASTE action
     * on the focused input field. Works for apps that reject ACTION_SET_TEXT
     * but accept standard clipboard paste (e.g., WeChat search).
     */
    fun typeTextViaPaste(text: String): Boolean {
        val service = _service ?: return false
        val clipboard = service.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("FoxTouch", text))

        val root = service.rootInActiveWindow ?: return false
        val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return false
        return focused.performAction(AccessibilityNodeInfo.ACTION_PASTE)
    }

    fun scroll(direction: String, elementId: Int? = null): Boolean = synchronized(nodeLock) {
        val target = if (elementId != null) {
            nodeMap[elementId]
                ?: error("Element [$elementId] not found. Call read_screen first to refresh the UI tree.")
        } else {
            val service = requireService()
            val root = service.rootInActiveWindow ?: return false
            findScrollableNode(root) ?: return false
        }

        val action = when (direction) {
            "down", "right" -> AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
            "up", "left" -> AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
            else -> return false
        }
        target.performAction(action)
    }

    private fun findScrollableNode(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isScrollable) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findScrollableNode(child)
            if (result != null) return result
        }
        return null
    }

    suspend fun swipe(startX: Float, startY: Float, endX: Float, endY: Float, durationMs: Long) {
        TouchAnimationOverlay.showSwipeTrail(startX, startY, endX, endY, durationMs)
        val service = requireService()
        val gesture = service.buildSwipeGesture(startX, startY, endX, endY, durationMs)
        service.dispatchGestureSuspend(gesture)
    }

    suspend fun longPress(x: Float, y: Float, durationMs: Long = 1000) {
        val gesture = GestureExecutor.buildLongPressGesture(x, y, durationMs)
        GestureExecutor.dispatch(gesture)
    }

    suspend fun pinch(centerX: Float, centerY: Float, startDistance: Float, endDistance: Float, durationMs: Long = 500) {
        val gesture = GestureExecutor.buildPinchGesture(centerX, centerY, startDistance, endDistance, durationMs)
        GestureExecutor.dispatch(gesture)
    }

    fun pressBack() {
        requireService().performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
    }

    fun pressHome() {
        requireService().performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
    }

    fun launchApp(packageName: String): Boolean {
        val service = requireService()
        val context = service.applicationContext
        val pm = context.packageManager

        val intent = pm.getLaunchIntentForPackage(packageName) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        return true
    }

    // ── Clipboard ─────────────────────────────────────────────────────

    private const val IME_ID = "ai.foxtouch/.ime.FoxTouchIME"

    /**
     * Read clipboard content reliably, even from an overlay / background context.
     *
     * Strategy (in order):
     * 1. If FoxTouchIME is active → read via IME (IMEs are exempt from clipboard restrictions)
     * 2. Try direct read via accessibility service context
     * 3. Launch a transparent [ClipboardReaderActivity] to gain window focus and read
     *
     * Steps 1–2 are synchronous; step 3 is async (launches an Activity).
     */
    suspend fun readClipboard(): String? {
        // 1. IME path — always works when FoxTouchIME is the active keyboard
        FoxTouchIME.instance?.readClipboard()?.let { return it }

        // 2. Direct read via accessibility service context
        val service = _service
        if (service != null) {
            try {
                val clipboard = service.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = clipboard.primaryClip
                if (clip != null && clip.itemCount > 0) {
                    val text = clip.getItemAt(0).text?.toString()
                    if (text != null) return text
                }
            } catch (_: Exception) {}
        }

        // 3. Transparent Activity — gains window focus to bypass Android 10+ restriction
        return readClipboardViaActivity()
    }

    /**
     * Launch [ClipboardReaderActivity] to gain window focus and read the clipboard.
     * Returns the clipboard text, or null if the read fails or times out.
     */
    private suspend fun readClipboardViaActivity(): String? {
        val service = _service ?: return null
        val deferred = CompletableDeferred<String?>()
        ClipboardReaderActivity.pendingResult = deferred

        val intent = Intent(service, ClipboardReaderActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        service.startActivity(intent)

        return withTimeoutOrNull(2000L) { deferred.await() }
    }

    /**
     * Write text to the system clipboard.
     */
    fun writeClipboard(text: String) {
        val service = requireService()
        val clipboard = service.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("FoxTouch", text))
    }

    // ── IME-based text input ────────────────────────────────────────

    /**
     * Check if FoxTouchIME is enabled in system settings.
     *
     * Uses [InputMethodManager] API instead of reading Settings.Secure directly,
     * which is restricted on Android 14+ (targetSdk 34+).
     */
    fun isImeEnabled(): Boolean {
        val service = _service ?: return false
        return try {
            val imm = service.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.enabledInputMethodList.any { it.packageName == service.packageName }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to check IME enabled status", e)
            false
        }
    }

    /**
     * Check if FoxTouchIME is the currently active IME.
     */
    fun isImeActive(): Boolean {
        // If FoxTouchIME instance exists, it's active
        return FoxTouchIME.instance != null
    }

    /**
     * Type text using FoxTouchIME if available (API 31+).
     *
     * Flow: switch to FoxTouchIME → wait for instance →
     * commitText → switch back to previous IME.
     *
     * Returns false (no-op) if IME is not enabled or API < 31,
     * allowing callers to fall back to ACTION_SET_TEXT.
     */
    suspend fun typeTextViaIme(text: String): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
        if (!isImeEnabled()) return false

        val service = _service ?: return false

        // Already active? Just commit.
        val existingIme = FoxTouchIME.instance
        if (existingIme != null) {
            return existingIme.commitText(text)
        }

        // Switch to FoxTouchIME
        try {
            service.softKeyboardController.switchToInputMethod(IME_ID)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to switch to FoxTouchIME", e)
            return false
        }

        // Wait for FoxTouchIME instance to become available
        val ime = withTimeoutOrNull(1500L) {
            while (FoxTouchIME.instance == null) {
                delay(50)
            }
            // Give InputConnection a moment to bind
            delay(100)
            FoxTouchIME.instance
        }

        if (ime == null) {
            Log.w(TAG, "FoxTouchIME instance not available after switch")
            return false
        }

        val result = ime.commitText(text)

        // Switch back to previous IME
        ime.switchBack()

        return result
    }
}
