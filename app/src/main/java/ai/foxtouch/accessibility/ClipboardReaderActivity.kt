package ai.foxtouch.accessibility

import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import kotlinx.coroutines.CompletableDeferred

/**
 * Fully transparent Activity that exists solely to gain window focus
 * and read the system clipboard.
 *
 * Android 10+ blocks getPrimaryClip() for apps without window focus.
 * This Activity briefly appears (invisibly), reads the clipboard in
 * onWindowFocusChanged(true), delivers the result via [pendingResult],
 * and finishes immediately.
 *
 * Requires Theme.FoxTouch.Transparent (translucent, no-animation theme).
 */
class ClipboardReaderActivity : ComponentActivity() {

    companion object {
        private const val TAG = "ClipboardReader"

        /**
         * Pending result deferred. Set by [AccessibilityBridge] before launching
         * this Activity, completed here, then nulled out.
         */
        @Volatile
        var pendingResult: CompletableDeferred<String?>? = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Completely empty — no UI
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            val text = try {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = clipboard.primaryClip
                if (clip != null && clip.itemCount > 0) {
                    clip.getItemAt(0).text?.toString()
                } else {
                    null
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to read clipboard", e)
                null
            }
            Log.d(TAG, "Clipboard read: ${text?.take(30) ?: "(null)"}")
            pendingResult?.complete(text)
            pendingResult = null
            finish()
        }
    }

    override fun onPause() {
        super.onPause()
        // Safety: if we lose focus before reading, complete with null
        pendingResult?.complete(null)
        pendingResult = null
        finish()
    }
}
