package ai.foxtouch.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

enum class AgentMode { NORMAL, PLAN, YOLO }
enum class ScreenshotMode { ACCESSIBILITY, MEDIA_PROJECTION }

data class LanguageOption(val code: String, val displayName: String)

val SUPPORTED_LANGUAGES = listOf(
    LanguageOption("", "System Default"),
    LanguageOption("en", "English"),
    LanguageOption("zh-CN", "\u4E2D\u6587(\u7B80\u4F53)"),
    LanguageOption("zh-TW", "\u4E2D\u6587(\u7E41\u9AD4)"),
    LanguageOption("ja", "\u65E5\u672C\u8A9E"),
    LanguageOption("ko", "\uD55C\uAD6D\uC5B4"),
    LanguageOption("es", "Espa\u00F1ol"),
    LanguageOption("ms", "Bahasa Melayu"),
)

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class AppSettings @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val dataStore get() = context.settingsDataStore

    companion object {
        private val KEY_PROVIDER = stringPreferencesKey("llm_provider")
        private val KEY_MODEL = stringPreferencesKey("llm_model")
        private val KEY_SETUP_COMPLETE = booleanPreferencesKey("setup_complete")
        private val KEY_AGENT_MODE = stringPreferencesKey("agent_mode")
        private val KEY_TTS_ENABLED = booleanPreferencesKey("tts_enabled")
        private val KEY_TTS_SPEECH_RATE = floatPreferencesKey("tts_speech_rate")
        // Legacy global keys — kept for migration, new code uses per-provider keys
        private val KEY_BASE_URL = stringPreferencesKey("base_url")
        private val KEY_HTTP_PROXY = stringPreferencesKey("http_proxy")
        private val KEY_STREAMING = booleanPreferencesKey("streaming_enabled")
        private val KEY_MAX_ITERATIONS = intPreferencesKey("max_iterations")
        private val KEY_DEBUG_MODE = booleanPreferencesKey("debug_mode")
        private val KEY_DEBUG_JSON_DISPLAY = booleanPreferencesKey("debug_json_display")
        private val KEY_OVERLAY_ENABLED = booleanPreferencesKey("overlay_enabled")
        private val KEY_APP_LANGUAGE = stringPreferencesKey("app_language")
        private val KEY_THINKING_ENABLED = booleanPreferencesKey("thinking_enabled")
        private val KEY_COMPACT_THRESHOLD = intPreferencesKey("compact_threshold")
        private val KEY_COMPACT_MODEL = stringPreferencesKey("compact_model")
        private val KEY_CUSTOM_CONTEXT_WINDOW = intPreferencesKey("custom_context_window")
        private val KEY_AUTO_DELETE_TASKS = booleanPreferencesKey("auto_delete_tasks_on_completion")

        // Per-provider key patterns
        private fun providerBaseUrlKey(providerId: String) =
            stringPreferencesKey("provider_base_url_$providerId")

        private fun providerProxyKey(providerId: String) =
            stringPreferencesKey("provider_proxy_$providerId")

        private fun providerModelKey(providerId: String) =
            stringPreferencesKey("provider_model_$providerId")
    }

    val provider: Flow<String> = dataStore.data.map { it[KEY_PROVIDER] ?: "gemini" }
    val model: Flow<String> = dataStore.data.map { it[KEY_MODEL] ?: "gemini-2.5-flash" }
    val isSetupComplete: Flow<Boolean> = dataStore.data.map { it[KEY_SETUP_COMPLETE] ?: false }
    val agentMode: Flow<AgentMode> = dataStore.data.map {
        try { AgentMode.valueOf(it[KEY_AGENT_MODE] ?: "NORMAL") } catch (_: Exception) { AgentMode.NORMAL }
    }
    val isTtsEnabled: Flow<Boolean> = dataStore.data.map { it[KEY_TTS_ENABLED] ?: true }
    val ttsSpeechRate: Flow<Float> = dataStore.data.map { it[KEY_TTS_SPEECH_RATE] ?: 1.0f }
    /** @deprecated Use [getProviderBaseUrl] instead. Kept for legacy migration. */
    val baseUrl: Flow<String> = dataStore.data.map { it[KEY_BASE_URL] ?: "" }
    /** @deprecated Use [getProviderProxy] instead. Kept for legacy migration. */
    val httpProxy: Flow<String> = dataStore.data.map { it[KEY_HTTP_PROXY] ?: "" }
    val isStreamingEnabled: Flow<Boolean> = dataStore.data.map { it[KEY_STREAMING] ?: true }
    val maxIterations: Flow<Int> = dataStore.data.map { it[KEY_MAX_ITERATIONS] ?: 150 }
    val isDebugMode: Flow<Boolean> = dataStore.data.map { it[KEY_DEBUG_MODE] ?: false }
    val isDebugJsonDisplay: Flow<Boolean> = dataStore.data.map { it[KEY_DEBUG_JSON_DISPLAY] ?: false }
    val isOverlayEnabled: Flow<Boolean> = dataStore.data.map { it[KEY_OVERLAY_ENABLED] ?: true }
    val appLanguage: Flow<String> = dataStore.data.map { it[KEY_APP_LANGUAGE] ?: "" }
    val isThinkingEnabled: Flow<Boolean> = dataStore.data.map { it[KEY_THINKING_ENABLED] ?: true }
    /** Compact threshold in tokens. 0 = auto (based on model). */
    val compactThreshold: Flow<Int> = dataStore.data.map { it[KEY_COMPACT_THRESHOLD] ?: 0 }
    /** Model to use for compaction. Empty = use current model. */
    val compactModel: Flow<String> = dataStore.data.map { it[KEY_COMPACT_MODEL] ?: "" }
    /** Custom context window in tokens. 0 = auto (use model default from ModelTokenLimits). */
    val customContextWindow: Flow<Int> = dataStore.data.map { it[KEY_CUSTOM_CONTEXT_WINDOW] ?: 0 }
    val isAutoDeleteTasksOnCompletion: Flow<Boolean> = dataStore.data.map { it[KEY_AUTO_DELETE_TASKS] ?: false }

    suspend fun setProvider(provider: String) {
        dataStore.edit { it[KEY_PROVIDER] = provider }
    }

    suspend fun setModel(model: String) {
        dataStore.edit { it[KEY_MODEL] = model }
    }

    suspend fun setSetupComplete(complete: Boolean) {
        dataStore.edit { it[KEY_SETUP_COMPLETE] = complete }
    }

    suspend fun setAgentMode(mode: AgentMode) {
        dataStore.edit { it[KEY_AGENT_MODE] = mode.name }
    }

    suspend fun setTtsEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_TTS_ENABLED] = enabled }
    }

    suspend fun setTtsSpeechRate(rate: Float) {
        dataStore.edit { it[KEY_TTS_SPEECH_RATE] = rate.coerceIn(0.25f, 4.0f) }
    }

    /** @deprecated Use [setProviderBaseUrl] instead. */
    suspend fun setBaseUrl(url: String) {
        dataStore.edit { it[KEY_BASE_URL] = url.trim() }
    }

    /** @deprecated Use [setProviderProxy] instead. */
    suspend fun setHttpProxy(proxy: String) {
        dataStore.edit { it[KEY_HTTP_PROXY] = proxy.trim() }
    }

    // ── Per-provider network settings ──────────────────────────────────

    fun getProviderBaseUrl(providerId: String): Flow<String> = dataStore.data.map {
        it[providerBaseUrlKey(providerId)] ?: ""
    }

    suspend fun getProviderBaseUrlOnce(providerId: String): String =
        getProviderBaseUrl(providerId).first()

    suspend fun setProviderBaseUrl(providerId: String, url: String) {
        dataStore.edit { it[providerBaseUrlKey(providerId)] = url.trim() }
    }

    fun getProviderProxy(providerId: String): Flow<String> = dataStore.data.map {
        it[providerProxyKey(providerId)] ?: ""
    }

    suspend fun getProviderProxyOnce(providerId: String): String =
        getProviderProxy(providerId).first()

    suspend fun setProviderProxy(providerId: String, proxy: String) {
        dataStore.edit { it[providerProxyKey(providerId)] = proxy.trim() }
    }

    fun getProviderModel(providerId: String): Flow<String> = dataStore.data.map {
        it[providerModelKey(providerId)] ?: ""
    }

    suspend fun getProviderModelOnce(providerId: String): String =
        getProviderModel(providerId).first()

    suspend fun setProviderModel(providerId: String, modelId: String) {
        dataStore.edit { it[providerModelKey(providerId)] = modelId }
    }

    /**
     * Migrate legacy global base_url/proxy to the current provider's per-provider keys.
     * Call once during app init. No-op if legacy keys are empty or already migrated.
     */
    suspend fun migrateGlobalNetworkSettings() {
        val prefs = dataStore.data.first()
        val legacyUrl = prefs[KEY_BASE_URL] ?: ""
        val legacyProxy = prefs[KEY_HTTP_PROXY] ?: ""
        if (legacyUrl.isBlank() && legacyProxy.isBlank()) return

        val currentProvider = prefs[KEY_PROVIDER] ?: "gemini"
        dataStore.edit {
            if (legacyUrl.isNotBlank()) {
                val existing = it[providerBaseUrlKey(currentProvider)] ?: ""
                if (existing.isBlank()) {
                    it[providerBaseUrlKey(currentProvider)] = legacyUrl
                }
                it.remove(KEY_BASE_URL)
            }
            if (legacyProxy.isNotBlank()) {
                val existing = it[providerProxyKey(currentProvider)] ?: ""
                if (existing.isBlank()) {
                    it[providerProxyKey(currentProvider)] = legacyProxy
                }
                it.remove(KEY_HTTP_PROXY)
            }
        }
    }

    suspend fun setStreamingEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_STREAMING] = enabled }
    }

    suspend fun setMaxIterations(max: Int) {
        dataStore.edit { it[KEY_MAX_ITERATIONS] = max.coerceIn(10, 500) }
    }

    suspend fun setDebugMode(enabled: Boolean) {
        dataStore.edit { it[KEY_DEBUG_MODE] = enabled }
    }

    suspend fun setDebugJsonDisplay(enabled: Boolean) {
        dataStore.edit { it[KEY_DEBUG_JSON_DISPLAY] = enabled }
    }

    suspend fun setOverlayEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_OVERLAY_ENABLED] = enabled }
    }

    suspend fun setAppLanguage(language: String) {
        dataStore.edit { it[KEY_APP_LANGUAGE] = language }
    }

    suspend fun setThinkingEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_THINKING_ENABLED] = enabled }
    }

    suspend fun setCompactThreshold(threshold: Int) {
        dataStore.edit { it[KEY_COMPACT_THRESHOLD] = threshold.coerceAtLeast(0) }
    }

    suspend fun setCompactModel(model: String) {
        dataStore.edit { it[KEY_COMPACT_MODEL] = model.trim() }
    }

    suspend fun setCustomContextWindow(tokens: Int) {
        dataStore.edit { it[KEY_CUSTOM_CONTEXT_WINDOW] = tokens.coerceAtLeast(0) }
    }

    suspend fun setAutoDeleteTasksOnCompletion(enabled: Boolean) {
        dataStore.edit { it[KEY_AUTO_DELETE_TASKS] = enabled }
    }

    suspend fun getProviderOnce(): String = provider.first()
    suspend fun getModelOnce(): String = model.first()
    suspend fun getAgentModeOnce(): AgentMode = agentMode.first()
    /** @deprecated Use [getProviderBaseUrlOnce] instead. */
    suspend fun getBaseUrlOnce(): String = baseUrl.first()
    /** @deprecated Use [getProviderProxyOnce] instead. */
    suspend fun getHttpProxyOnce(): String = httpProxy.first()
    suspend fun getStreamingEnabledOnce(): Boolean = isStreamingEnabled.first()
    suspend fun getMaxIterationsOnce(): Int = maxIterations.first()
    suspend fun getOverlayEnabledOnce(): Boolean = isOverlayEnabled.first()
    suspend fun getThinkingEnabledOnce(): Boolean = isThinkingEnabled.first()
    suspend fun getCompactThresholdOnce(): Int = compactThreshold.first()
    suspend fun getCompactModelOnce(): String = compactModel.first()
    suspend fun getCustomContextWindowOnce(): Int = customContextWindow.first()
    suspend fun getAutoDeleteTasksOnCompletionOnce(): Boolean = isAutoDeleteTasksOnCompletion.first()

    // --- Per-app screenshot mode (MediaProjection fallback for apps that block A11y screenshots) ---

    private fun screenshotModeKey(packageName: String) =
        stringPreferencesKey("screenshot_mode_$packageName")

    /** Get the screenshot mode for a specific app. Defaults to ACCESSIBILITY. */
    suspend fun getScreenshotMode(packageName: String): ScreenshotMode {
        val value = dataStore.data.first()[screenshotModeKey(packageName)]
        return try {
            if (value != null) ScreenshotMode.valueOf(value) else ScreenshotMode.ACCESSIBILITY
        } catch (_: Exception) {
            ScreenshotMode.ACCESSIBILITY
        }
    }

    /** Set the screenshot mode for a specific app. */
    suspend fun setScreenshotMode(packageName: String, mode: ScreenshotMode) {
        dataStore.edit { it[screenshotModeKey(packageName)] = mode.name }
    }

    /** Get all apps that have MediaProjection screenshot mode enabled. */
    fun getMediaProjectionApps(): Flow<Set<String>> = dataStore.data.map { prefs ->
        prefs.asMap().entries
            .filter { (key, value) ->
                key.name.startsWith("screenshot_mode_") &&
                    value == ScreenshotMode.MEDIA_PROJECTION.name
            }
            .map { it.key.name.removePrefix("screenshot_mode_") }
            .toSet()
    }
}
