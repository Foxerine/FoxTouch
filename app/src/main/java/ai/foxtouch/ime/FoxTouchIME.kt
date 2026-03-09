package ai.foxtouch.ime

import android.content.ClipboardManager
import android.content.Context
import android.inputmethodservice.InputMethodService
import android.util.Log
import android.view.View

/**
 * Minimal, invisible InputMethodService for programmatic text input.
 *
 * Why an IME?
 * 1. IMEs are exempt from Android 10+ clipboard read restrictions — they can
 *    always call getPrimaryClip() regardless of window focus.
 * 2. InputConnection.commitText() is more reliable than ACTION_SET_TEXT for
 *    apps with custom text rendering (WebView, Flutter, games).
 *
 * This IME shows no keyboard UI. It is activated temporarily by the
 * accessibility service when FoxTouch needs to type or read the clipboard,
 * then automatically switches back to the user's previous IME.
 */
class FoxTouchIME : InputMethodService() {

    companion object {
        private const val TAG = "FoxTouchIME"

        /** In-process reference. Safe because the IME runs in the same process. */
        @Volatile
        var instance: FoxTouchIME? = null
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        Log.d(TAG, "FoxTouchIME created")
    }

    override fun onDestroy() {
        instance = null
        Log.d(TAG, "FoxTouchIME destroyed")
        super.onDestroy()
    }

    /** No visible keyboard. */
    override fun onCreateInputView(): View? = null

    /** Never show the input area — keep the keyboard completely invisible. */
    override fun onEvaluateInputViewShown(): Boolean = false

    // ── Public API (called from AccessibilityBridge) ────────────────

    /**
     * Commit text via InputConnection. Returns true if successful.
     */
    fun commitText(text: String): Boolean {
        val ic = currentInputConnection ?: run {
            Log.w(TAG, "commitText: no InputConnection")
            return false
        }
        ic.beginBatchEdit()
        val ok = ic.commitText(text, 1)
        ic.endBatchEdit()
        Log.d(TAG, "commitText(${text.take(30)}...): $ok")
        return ok
    }

    /**
     * Read clipboard content. IMEs are exempt from Android 10+ restrictions.
     */
    fun readClipboard(): String? {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboard.primaryClip ?: return null
        if (clip.itemCount == 0) return null
        return clip.getItemAt(0).text?.toString()
    }

    /**
     * Switch back to the user's previous IME.
     * Uses switchToPreviousInputMethod() (API 28+).
     */
    fun switchBack() {
        try {
            switchToPreviousInputMethod()
            Log.d(TAG, "Switched back to previous IME")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to switch back to previous IME", e)
        }
    }
}
