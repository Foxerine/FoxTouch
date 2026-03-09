package ai.foxtouch.agent

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceContext @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    suspend fun gather(): String = withContext(Dispatchers.IO) {
        val sb = StringBuilder()
        sb.appendLine("## Device Context")
        sb.appendLine()

        // Date/Time
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss (EEEE)", Locale.getDefault())
        sb.appendLine("- **Current Time**: ${dateFormat.format(Date())}")
        sb.appendLine("- **Timezone**: ${java.util.TimeZone.getDefault().id}")

        // Device info
        sb.appendLine("- **Device**: ${Build.MANUFACTURER} ${Build.MODEL}")
        sb.appendLine("- **Android Version**: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")

        // Screen resolution
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(metrics)
        sb.appendLine("- **Screen**: ${metrics.widthPixels}x${metrics.heightPixels} (${metrics.densityDpi}dpi)")

        // Battery
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val batteryLevel = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val isCharging = bm.isCharging
        sb.appendLine("- **Battery**: $batteryLevel%${if (isCharging) " (charging)" else ""}")

        // Network — requires ACCESS_NETWORK_STATE; some OEMs may restrict even normal permissions
        sb.appendLine("- **Network**: ${getNetworkType()}")

        // Language & Locale
        val systemLocale = Locale.getDefault()
        sb.appendLine("- **System Language**: ${systemLocale.displayLanguage} (${systemLocale.toLanguageTag()})")
        sb.appendLine("- **System Country**: ${systemLocale.displayCountry} (${systemLocale.country})")
        val config = context.resources.configuration
        val appLocales = config.locales
        if (appLocales.size() > 0) {
            val appLocale = appLocales[0]
            sb.appendLine("- **App Locale**: ${appLocale.displayLanguage} (${appLocale.toLanguageTag()})")
        }
        sb.appendLine("- **24h Time Format**: ${android.text.format.DateFormat.is24HourFormat(context)}")

        // Installed apps
        sb.appendLine()
        sb.appendLine("## Installed Apps")
        sb.appendLine()
        val pm = context.packageManager
        val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { pm.getLaunchIntentForPackage(it.packageName) != null }
            .map { appInfo ->
                val label = pm.getApplicationLabel(appInfo).toString()
                "$label (${appInfo.packageName})"
            }
            .sorted()

        for (app in apps) {
            sb.appendLine("- $app")
        }

        sb.toString()
    }

    private fun getNetworkType(): String {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_NETWORK_STATE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return "Unknown (permission denied)"
        }
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val caps = cm.getNetworkCapabilities(cm.activeNetwork)
            when {
                caps == null -> "Offline"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WiFi"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Mobile Data"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
                else -> "Connected"
            }
        } catch (_: SecurityException) {
            "Unknown (restricted)"
        }
    }
}
