package ai.foxtouch

import android.app.Activity
import android.app.Application
import android.os.Bundle
import ai.foxtouch.data.preferences.AppSettings
import ai.foxtouch.tools.ToolDisplayRegistry
import com.google.crypto.tink.aead.AeadConfig
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class FoxTouchApp : Application() {

    @Inject lateinit var appSettings: AppSettings

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        AeadConfig.register()
        ToolDisplayRegistry.init(this)
        registerActivityLifecycleCallbacks(ForegroundTracker)

        // Migrate legacy global base_url/proxy to per-provider keys
        appScope.launch { appSettings.migrateGlobalNetworkSettings() }
    }

    /**
     * Tracks whether any FoxTouch Activity is currently in the foreground.
     * Used by FloatingBubbleService to hide the overlay when the user is inside the app.
     */
    object ForegroundTracker : ActivityLifecycleCallbacks {
        private val _isForeground = MutableStateFlow(false)
        val isForeground: StateFlow<Boolean> = _isForeground.asStateFlow()

        private var activeCount = 0

        override fun onActivityResumed(activity: Activity) {
            activeCount++
            _isForeground.value = true
        }

        override fun onActivityPaused(activity: Activity) {
            activeCount = (activeCount - 1).coerceAtLeast(0)
            if (activeCount == 0) {
                _isForeground.value = false
            }
        }

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
        override fun onActivityStarted(activity: Activity) {}
        override fun onActivityStopped(activity: Activity) {}
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
        override fun onActivityDestroyed(activity: Activity) {}
    }
}
