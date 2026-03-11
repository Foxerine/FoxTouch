package ai.foxtouch.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ai.foxtouch.agent.AgentDocsManager
import ai.foxtouch.agent.ContextCompactor
import ai.foxtouch.agent.DeviceContext
import ai.foxtouch.agent.ModelInfo
import ai.foxtouch.agent.SkillSummary
import ai.foxtouch.agent.SkillsManager
import android.util.Log
import ai.foxtouch.agent.AVAILABLE_PROVIDERS
import ai.foxtouch.agent.ModelTokenLimits
import ai.foxtouch.agent.classifyClaudeModel
import ai.foxtouch.agent.classifyGeminiModel
import ai.foxtouch.agent.classifyOpenAIModel
import ai.foxtouch.agent.getProviderInfo
import ai.foxtouch.agent.isOpenAIChatModel
import ai.foxtouch.data.preferences.ApiKeyStore
import ai.foxtouch.data.preferences.AppSettings
import ai.foxtouch.data.preferences.ScreenshotMode
import ai.foxtouch.permission.PermissionStore
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import dagger.hilt.android.lifecycle.HiltViewModel
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import javax.inject.Inject

/** Model list fetch result — sealed state for clear UI rendering. */
sealed class ModelListState {
    data object Idle : ModelListState()
    data object Loading : ModelListState()
    data class Success(val models: List<ModelInfo>) : ModelListState()
    data class Error(val message: String, val fallbackModels: List<ModelInfo>) : ModelListState()
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val appSettings: AppSettings,
    private val apiKeyStore: ApiKeyStore,
    private val httpClient: HttpClient,
    private val json: Json,
    private val deviceContext: DeviceContext,
    val permissionStore: PermissionStore,
    private val skillsManager: SkillsManager,
    private val agentDocsManager: AgentDocsManager,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    companion object {
        private const val TAG = "SettingsViewModel"
        private const val LITELLM_URL =
            "https://raw.githubusercontent.com/BerriAI/litellm/main/model_prices_and_context_window.json"
    }

    val provider: StateFlow<String> = appSettings.provider
        .stateIn(viewModelScope, SharingStarted.Eagerly, "gemini")

    val model: StateFlow<String> = appSettings.model
        .stateIn(viewModelScope, SharingStarted.Eagerly, "gemini-2.5-flash")

    private val _modelListState = MutableStateFlow<ModelListState>(ModelListState.Idle)
    val modelListState: StateFlow<ModelListState> = _modelListState.asStateFlow()

    private val _providerKeyStatus = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val providerKeyStatus: StateFlow<Map<String, Boolean>> = _providerKeyStatus.asStateFlow()

    init {
        refreshProviderKeyStatus()
        refreshAllProviderConfigs()
    }

    val ttsEnabled: StateFlow<Boolean> = appSettings.isTtsEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val ttsSpeechRate: StateFlow<Float> = appSettings.ttsSpeechRate
        .stateIn(viewModelScope, SharingStarted.Eagerly, 1.0f)

    /** Per-provider base URL for the currently selected provider. */
    private val _providerBaseUrl = MutableStateFlow("")
    val providerBaseUrl: StateFlow<String> = _providerBaseUrl.asStateFlow()

    /** Per-provider proxy for the currently selected provider. */
    private val _providerProxy = MutableStateFlow("")
    val providerProxy: StateFlow<String> = _providerProxy.asStateFlow()

    /** All per-provider configs: providerId -> ProviderNetworkConfig */
    data class ProviderNetworkConfig(val baseUrl: String = "", val proxy: String = "")
    private val _allProviderConfigs = MutableStateFlow<Map<String, ProviderNetworkConfig>>(emptyMap())
    val allProviderConfigs: StateFlow<Map<String, ProviderNetworkConfig>> = _allProviderConfigs.asStateFlow()

    val streamingEnabled: StateFlow<Boolean> = appSettings.isStreamingEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val maxIterations: StateFlow<Int> = appSettings.maxIterations
        .stateIn(viewModelScope, SharingStarted.Eagerly, 150)

    val debugMode: StateFlow<Boolean> = appSettings.isDebugMode
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val debugJsonDisplay: StateFlow<Boolean> = appSettings.isDebugJsonDisplay
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val overlayEnabled: StateFlow<Boolean> = appSettings.isOverlayEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val appLanguage: StateFlow<String> = appSettings.appLanguage
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    val thinkingEnabled: StateFlow<Boolean> = appSettings.isThinkingEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val mediaProjectionApps: StateFlow<Set<String>> = appSettings.getMediaProjectionApps()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    // ── Compact settings ────────────────────────────────────────────
    val compactThreshold: StateFlow<Int> = appSettings.compactThreshold
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    val compactModel: StateFlow<String> = appSettings.compactModel
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    val customContextWindow: StateFlow<Int> = appSettings.customContextWindow
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    val autoDeleteTasks: StateFlow<Boolean> = appSettings.isAutoDeleteTasksOnCompletion
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _compactPrompt = MutableStateFlow("")
    val compactPrompt: StateFlow<String> = _compactPrompt.asStateFlow()

    fun loadCompactPrompt() {
        _compactPrompt.value = agentDocsManager.readCompactPrompt()
    }

    fun saveCompactPrompt(content: String) {
        agentDocsManager.writeCompactPrompt(content)
        _compactPrompt.value = content
    }

    fun resetCompactPrompt() {
        val defaultPrompt = ContextCompactor.DEFAULT_COMPACT_PROMPT
        agentDocsManager.writeCompactPrompt(defaultPrompt)
        _compactPrompt.value = defaultPrompt
    }

    suspend fun setCompactThreshold(threshold: Int) {
        appSettings.setCompactThreshold(threshold)
    }

    suspend fun setCompactModel(model: String) {
        appSettings.setCompactModel(model)
    }

    suspend fun setCustomContextWindow(tokens: Int) {
        appSettings.setCustomContextWindow(tokens)
    }

    suspend fun setAutoDeleteTasks(enabled: Boolean) {
        appSettings.setAutoDeleteTasksOnCompletion(enabled)
    }

    // ── Skills management ───────────────────────────────────────────
    private val _skills = MutableStateFlow<List<SkillSummary>>(emptyList())
    val skills: StateFlow<List<SkillSummary>> = _skills.asStateFlow()

    fun loadSkills() {
        _skills.value = skillsManager.listSkills()
    }

    fun readSkill(id: String): String? = skillsManager.readSkill(id)

    fun createSkill(title: String, content: String) {
        skillsManager.saveSkill(title, content)
        loadSkills()
    }

    fun updateSkill(id: String, title: String, content: String) {
        skillsManager.updateSkill(id, title, content)
        loadSkills()
    }

    fun deleteSkill(id: String) {
        skillsManager.deleteSkill(id)
        loadSkills()
    }

    suspend fun setProvider(provider: String) {
        _modelListState.value = ModelListState.Idle
        appSettings.setProvider(provider)
    }

    suspend fun setModel(model: String) {
        appSettings.setModel(model)
    }

    suspend fun saveApiKey(provider: String, apiKey: String) {
        apiKeyStore.saveApiKey(provider, apiKey)
        val status = _providerKeyStatus.value.toMutableMap()
        status[provider] = apiKey.isNotBlank()
        _providerKeyStatus.value = status
    }

    private fun refreshProviderKeyStatus() {
        viewModelScope.launch {
            val status = mutableMapOf<String, Boolean>()
            for (p in AVAILABLE_PROVIDERS) {
                val key = apiKeyStore.getApiKey(p.id).first()
                status[p.id] = !key.isNullOrBlank()
            }
            _providerKeyStatus.value = status
        }
    }

    suspend fun setTtsEnabled(enabled: Boolean) {
        appSettings.setTtsEnabled(enabled)
    }

    suspend fun setTtsSpeechRate(rate: Float) {
        appSettings.setTtsSpeechRate(rate)
    }

    suspend fun setProviderBaseUrl(providerId: String, url: String) {
        appSettings.setProviderBaseUrl(providerId, url)
        refreshProviderConfig(providerId)
    }

    suspend fun setProviderProxy(providerId: String, proxy: String) {
        appSettings.setProviderProxy(providerId, proxy)
        refreshProviderConfig(providerId)
    }

    private fun refreshAllProviderConfigs() {
        viewModelScope.launch {
            val configs = mutableMapOf<String, ProviderNetworkConfig>()
            for (p in AVAILABLE_PROVIDERS) {
                configs[p.id] = ProviderNetworkConfig(
                    baseUrl = appSettings.getProviderBaseUrlOnce(p.id),
                    proxy = appSettings.getProviderProxyOnce(p.id),
                )
            }
            _allProviderConfigs.value = configs
        }
    }

    private fun refreshProviderConfig(providerId: String) {
        viewModelScope.launch {
            val configs = _allProviderConfigs.value.toMutableMap()
            configs[providerId] = ProviderNetworkConfig(
                baseUrl = appSettings.getProviderBaseUrlOnce(providerId),
                proxy = appSettings.getProviderProxyOnce(providerId),
            )
            _allProviderConfigs.value = configs
        }
    }

    suspend fun setStreamingEnabled(enabled: Boolean) {
        appSettings.setStreamingEnabled(enabled)
    }

    suspend fun setMaxIterations(max: Int) {
        appSettings.setMaxIterations(max)
    }

    suspend fun setDebugMode(enabled: Boolean) {
        appSettings.setDebugMode(enabled)
    }

    suspend fun setDebugJsonDisplay(enabled: Boolean) {
        appSettings.setDebugJsonDisplay(enabled)
    }

    suspend fun setOverlayEnabled(enabled: Boolean) {
        appSettings.setOverlayEnabled(enabled)
    }

    suspend fun setThinkingEnabled(enabled: Boolean) {
        appSettings.setThinkingEnabled(enabled)
    }

    suspend fun setScreenshotMode(packageName: String, mode: ScreenshotMode) {
        appSettings.setScreenshotMode(packageName, mode)
    }

    private val _deviceContextPreview = MutableStateFlow<String?>(null)
    val deviceContextPreview: StateFlow<String?> = _deviceContextPreview.asStateFlow()

    private val _isGatheringContext = MutableStateFlow(false)
    val isGatheringContext: StateFlow<Boolean> = _isGatheringContext.asStateFlow()

    fun gatherDeviceContext() {
        if (_isGatheringContext.value) return
        viewModelScope.launch {
            _isGatheringContext.value = true
            _deviceContextPreview.value = deviceContext.gather()
            _isGatheringContext.value = false
        }
    }

    fun setAppLanguage(langCode: String) {
        viewModelScope.launch {
            appSettings.setAppLanguage(langCode)
        }
        val localeList = if (langCode.isBlank()) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(langCode)
        }
        AppCompatDelegate.setApplicationLocales(localeList)
    }

    suspend fun markSetupComplete() {
        appSettings.setSetupComplete(true)
    }

    // ── Model fetching ──────────────────────────────────────────────────

    /**
     * Build an HttpClient that respects the provider's proxy setting.
     */
    private suspend fun buildModelFetchClient(forProvider: String): HttpClient {
        val proxyStr = appSettings.getProviderProxyOnce(forProvider)
        if (proxyStr.isBlank()) return httpClient // reuse the injected client

        return HttpClient(OkHttp) {
            install(ContentNegotiation) { json(json) }
            engine {
                config {
                    val parts = proxyStr.replace("http://", "").replace("https://", "").split(":")
                    val host = parts.getOrNull(0) ?: return@config
                    val port = parts.getOrNull(1)?.toIntOrNull() ?: 7890
                    proxy(java.net.Proxy(java.net.Proxy.Type.HTTP, java.net.InetSocketAddress(host, port)))
                }
            }
        }
    }

    /**
     * Fetch available models from the provider's API.
     * @param forProvider Explicit provider ID. If null, uses the current provider.
     */
    fun fetchModels(forProvider: String? = null) {
        if (_modelListState.value is ModelListState.Loading) return
        val targetProvider = forProvider ?: provider.value
        val fallback = getProviderInfo(targetProvider)?.models ?: emptyList()

        viewModelScope.launch {
            _modelListState.value = ModelListState.Loading
            try {
                val apiKey = apiKeyStore.getApiKey(targetProvider).first()
                if (apiKey.isNullOrBlank()) {
                    Log.d(TAG, "No API key for $targetProvider, showing hardcoded models")
                    _modelListState.value = ModelListState.Success(fallback)
                    return@launch
                }
                val customBaseUrl = appSettings.getProviderBaseUrlOnce(targetProvider)
                Log.d(TAG, "Fetching models for $targetProvider (baseUrl=${customBaseUrl.ifBlank { "(default)" }})")

                val client = buildModelFetchClient(targetProvider)
                val isOwnedClient = client !== httpClient

                val models = try {
                    when (targetProvider) {
                        "gemini" -> fetchGeminiModels(client, apiKey, customBaseUrl)
                        "openai" -> fetchOpenAIModels(client, apiKey, customBaseUrl)
                        "claude" -> fetchAnthropicModels(client, apiKey, customBaseUrl)
                        else -> fetchOpenAIModels(client, apiKey, customBaseUrl)
                    }
                } finally {
                    if (isOwnedClient) client.close()
                }

                Log.d(TAG, "Fetched ${models.size} models for $targetProvider")
                // Persist any runtime-discovered context windows (e.g. Gemini inputTokenLimit)
                if (targetProvider == "gemini") saveRuntimeModelData()
                _modelListState.value = if (models.isNotEmpty()) {
                    ModelListState.Success(models)
                } else {
                    ModelListState.Success(fallback)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch models for $targetProvider", e)
                _modelListState.value = ModelListState.Error(
                    message = e.message ?: "Unknown error",
                    fallbackModels = fallback,
                )
            }
        }
    }

    /**
     * Validate HTTP response status and return body text.
     * Throws on non-2xx to surface errors instead of silently falling back.
     */
    private suspend fun requireOkResponse(
        response: io.ktor.client.statement.HttpResponse,
        providerLabel: String,
    ): String {
        val body = response.bodyAsText()
        val status = response.status.value
        if (status !in 200..299) {
            // Extract API error message if available
            val errorDetail = try {
                val root = json.parseToJsonElement(body).jsonObject
                root["error"]?.jsonObject?.get("message")?.jsonPrimitive?.content
                    ?: root["error"]?.jsonPrimitive?.content
                    ?: body.take(200)
            } catch (_: Exception) {
                body.take(200)
            }
            throw RuntimeException("$providerLabel HTTP $status: $errorDetail")
        }
        return body
    }

    private suspend fun fetchGeminiModels(client: HttpClient, apiKey: String, customBaseUrl: String): List<ModelInfo> {
        val baseUrl = customBaseUrl.ifBlank { "https://generativelanguage.googleapis.com/v1beta" }
        val url = "${baseUrl.trimEnd('/')}/models?key=$apiKey"
        Log.d(TAG, "Gemini models URL: ${url.take(80)}...")

        val response = client.get(url)
        val body = requireOkResponse(response, "Gemini")

        Log.d(TAG, "Gemini response length: ${body.length}")
        val root = json.parseToJsonElement(body).jsonObject
        val models = root["models"]?.jsonArray
            ?: throw RuntimeException("Gemini response missing 'models' key: ${root.keys}")

        return models.mapNotNull { element ->
            val obj = element.jsonObject
            val name = obj["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val displayName = obj["displayName"]?.jsonPrimitive?.content ?: name
            val methods = obj["supportedGenerationMethods"]?.jsonArray
                ?.map { it.jsonPrimitive.content } ?: emptyList()
            if ("generateContent" !in methods) return@mapNotNull null
            // Extract context window from API response
            val inputTokenLimit = obj["inputTokenLimit"]?.jsonPrimitive?.int
            val modelId = name.removePrefix("models/")
            if (inputTokenLimit != null && inputTokenLimit > 0) {
                ModelTokenLimits.putRuntime(modelId, inputTokenLimit)
            }
            ModelInfo(id = modelId, displayName = displayName)
        }.map { classifyGeminiModel(it) }.sortedBy { it.displayName }
    }

    private suspend fun fetchOpenAIModels(client: HttpClient, apiKey: String, customBaseUrl: String): List<ModelInfo> {
        val baseUrl = customBaseUrl.ifBlank { "https://api.openai.com/v1" }
        val response = client.get("${baseUrl.trimEnd('/')}/models") {
            header("Authorization", "Bearer $apiKey")
        }
        val body = requireOkResponse(response, "OpenAI")

        val root = json.parseToJsonElement(body).jsonObject
        val models = root["data"]?.jsonArray
            ?: throw RuntimeException("OpenAI response missing 'data' key: ${root.keys}")

        return models.mapNotNull { element ->
            val obj = element.jsonObject
            val id = obj["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
            if (!isOpenAIChatModel(id)) return@mapNotNull null
            ModelInfo(id = id, displayName = id)
        }.map { classifyOpenAIModel(it) }.sortedBy { it.displayName }
    }

    private suspend fun fetchAnthropicModels(client: HttpClient, apiKey: String, customBaseUrl: String): List<ModelInfo> {
        val baseUrl = customBaseUrl.ifBlank { "https://api.anthropic.com/v1" }
        val response = client.get("${baseUrl.trimEnd('/')}/models") {
            header("x-api-key", apiKey)
            header("anthropic-version", "2023-06-01")
        }
        val body = requireOkResponse(response, "Anthropic")

        val root = json.parseToJsonElement(body).jsonObject
        val models = root["data"]?.jsonArray
            ?: throw RuntimeException("Anthropic response missing 'data' key: ${root.keys}")

        return models.mapNotNull { element ->
            val obj = element.jsonObject
            val id = obj["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val displayName = obj["display_name"]?.jsonPrimitive?.content ?: id
            ModelInfo(id = id, displayName = displayName)
        }.map { classifyClaudeModel(it) }.sortedBy { it.displayName }
    }

    // ── litellm context window sync ────────────────────────────────

    private val _litellmSyncState = MutableStateFlow<String?>(null)
    val litellmSyncState: StateFlow<String?> = _litellmSyncState.asStateFlow()

    /**
     * Fetch model context windows from litellm's curated JSON database.
     * Updates runtime model data and persists to disk.
     */
    fun syncModelLimitsFromLiteLLM() {
        viewModelScope.launch {
            _litellmSyncState.value = "syncing"
            try {
                val client = buildModelFetchClient("openai") // reuse proxy if any
                val isOwnedClient = client !== httpClient
                try {
                    val response = client.get(LITELLM_URL)
                    val body = response.bodyAsText()
                    val status = response.status.value
                    if (status !in 200..299) {
                        _litellmSyncState.value = "error: HTTP $status"
                        return@launch
                    }
                    val rootObj = json.parseToJsonElement(body).jsonObject
                    val count = ModelTokenLimits.parseLiteLLMJson(rootObj)
                    ModelTokenLimits.saveToDisk(appContext)
                    _litellmSyncState.value = "ok: $count models"
                    Log.d(TAG, "litellm sync complete: $count models")
                } finally {
                    if (isOwnedClient) client.close()
                }
            } catch (e: Exception) {
                Log.e(TAG, "litellm sync failed", e)
                _litellmSyncState.value = "error: ${e.message}"
            }
        }
    }

    /** Save runtime model data after fetching models from any API. */
    private fun saveRuntimeModelData() {
        ModelTokenLimits.saveToDisk(appContext)
    }
}
