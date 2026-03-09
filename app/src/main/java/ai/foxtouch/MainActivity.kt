package ai.foxtouch

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import ai.foxtouch.accessibility.ScreenCaptureManager
import ai.foxtouch.accessibility.ScreenCaptureService
import ai.foxtouch.data.preferences.AppSettings
import ai.foxtouch.ui.navigation.FoxTouchNavHost
import ai.foxtouch.ui.overlay.FloatingBubbleService
import ai.foxtouch.ui.theme.FoxTouchTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject lateinit var appSettings: AppSettings

    /**
     * Launcher for requesting runtime permissions.
     * Must be registered before onCreate returns (Activity Result API contract).
     * Results are informational — features degrade gracefully on denial.
     */
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { /* granted/denied map — no action needed, checked at point of use */ }

    /**
     * Launcher for MediaProjection screen capture permission.
     * On success, stores the result in [ScreenCaptureManager] for later use.
     * Also starts [ScreenCaptureService] to hold the projection (Android 14+ requirement).
     */
    val mediaProjectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            ScreenCaptureService.start(this)
            ScreenCaptureManager.init(this, result.resultCode, result.data!!)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestEssentialPermissions()
        startOverlayIfAllowed()
        enableEdgeToEdge()
        setContent {
            FoxTouchTheme {
                FoxTouchNavHost()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Re-check overlay permission when returning from settings
        startOverlayIfAllowed()
    }

    @Suppress("DEPRECATION")
    override fun recreate() {
        // Smooth crossfade for language switching instead of jarring black-screen restart
        window.decorView.animate()
            .alpha(0f)
            .setDuration(200)
            .withEndAction {
                super.recreate()
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            }
            .start()
    }

    /**
     * Launch the system MediaProjection permission dialog.
     * Called from Settings when the user enables enhanced screenshot mode for an app.
     */
    fun requestScreenCapturePermission() {
        val pm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjectionLauncher.launch(pm.createScreenCaptureIntent())
    }

    /**
     * Start the floating bubble overlay if the user has granted
     * SYSTEM_ALERT_WINDOW permission and overlay is enabled in settings.
     */
    private fun startOverlayIfAllowed() {
        if (Settings.canDrawOverlays(this)) {
            val overlayEnabled = runBlocking { appSettings.getOverlayEnabledOnce() }
            if (overlayEnabled) {
                FloatingBubbleService.start(this)
            }
        }
    }

    /**
     * Request runtime permissions needed by core features.
     *
     * - **POST_NOTIFICATIONS** (API 33+): Required for foreground service notifications
     *   (AgentForegroundService tool approval buttons, FloatingBubbleService status).
     * - **RECORD_AUDIO**: Required for SpeechRecognizer (voice input via mic button).
     *
     * Only requests permissions that haven't been granted yet.
     */
    private fun requestEssentialPermissions() {
        val needed = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
            add(Manifest.permission.RECORD_AUDIO)
        }.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (needed.isNotEmpty()) {
            permissionLauncher.launch(needed.toTypedArray())
        }
    }
}
