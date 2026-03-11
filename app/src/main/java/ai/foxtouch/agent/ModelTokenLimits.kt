package ai.foxtouch.agent

import android.content.Context
import android.util.Log
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Context window sizes and compact thresholds for LLM models.
 *
 * Data sources (priority order):
 * 1. Runtime-discovered data (from API responses or litellm sync)
 * 2. Hardcoded known models (built-in fallback)
 * 3. Default 128K assumption
 *
 * Compact threshold uses a tiered formula:
 * - <= 128K context: 70% (leaves ~38K headroom)
 * - 129K–256K context: 75% (leaves ~50K+ headroom)
 * - 257K–500K context: 80%
 * - > 500K context: 85%
 */
object ModelTokenLimits {

    private const val TAG = "ModelTokenLimits"
    private const val CACHE_FILE = "model_context_windows.json"

    data class ModelInfo(
        val contextWindow: Int,
        val compactThreshold: Int = defaultThresholdFor(contextWindow),
    )

    // ── Runtime-discovered models (populated from API / litellm sync) ──
    private val runtimeModels = ConcurrentHashMap<String, Int>()

    // ── Hardcoded known models (built-in fallback) ─────────────────────
    private val KNOWN_MODELS = mapOf(
        // ── OpenAI ──────────────────────────────────────────────────
        // GPT-5.x (2026)
        "gpt-5.4" to ModelInfo(1_048_576),
        "gpt-5.4-pro" to ModelInfo(1_048_576),
        "gpt-5.2" to ModelInfo(400_000),
        "gpt-5.2-codex" to ModelInfo(400_000),
        "gpt-5" to ModelInfo(400_000),
        // GPT-4.x
        "gpt-4.1" to ModelInfo(1_047_576),
        "gpt-4.1-mini" to ModelInfo(1_047_576),
        "gpt-4.1-nano" to ModelInfo(1_047_576),
        "gpt-4o" to ModelInfo(128_000),
        "gpt-4o-mini" to ModelInfo(128_000),
        "gpt-4-turbo" to ModelInfo(128_000),
        "gpt-4" to ModelInfo(8_192),
        "gpt-3.5-turbo" to ModelInfo(16_385),
        "chatgpt-4o" to ModelInfo(128_000),
        // O-series reasoning
        "o3" to ModelInfo(200_000),
        "o3-mini" to ModelInfo(200_000),
        "o4-mini" to ModelInfo(200_000),
        "o1" to ModelInfo(200_000),
        "o1-mini" to ModelInfo(128_000),
        "o1-preview" to ModelInfo(128_000),

        // ── Anthropic Claude ────────────────────────────────────────
        // Claude 4.6
        "claude-opus-4-6" to ModelInfo(200_000),
        "claude-sonnet-4-6" to ModelInfo(200_000),
        // Claude 4.5 / 4
        "claude-sonnet-4-5" to ModelInfo(200_000),
        "claude-sonnet-4" to ModelInfo(200_000),
        "claude-opus-4" to ModelInfo(200_000),
        "claude-haiku-4-5" to ModelInfo(200_000),
        "claude-haiku-4" to ModelInfo(200_000),
        // Claude 3.5
        "claude-3-5-sonnet" to ModelInfo(200_000),
        "claude-3-5-haiku" to ModelInfo(200_000),
        // Claude 3
        "claude-3-opus" to ModelInfo(200_000),
        "claude-3-sonnet" to ModelInfo(200_000),
        "claude-3-haiku" to ModelInfo(200_000),

        // ── Google Gemini ───────────────────────────────────────────
        "gemini-3.1-pro" to ModelInfo(1_048_576),
        "gemini-3.1-flash-lite" to ModelInfo(1_048_576),
        "gemini-3-pro" to ModelInfo(1_048_576),
        "gemini-3-flash" to ModelInfo(1_048_576),
        "gemini-2.5-pro" to ModelInfo(1_048_576),
        "gemini-2.5-flash" to ModelInfo(1_048_576),
        "gemini-2.0-flash" to ModelInfo(1_048_576),
        "gemini-2.0-flash-lite" to ModelInfo(1_048_576),
        "gemini-1.5-pro" to ModelInfo(1_048_576),
        "gemini-1.5-flash" to ModelInfo(1_048_576),

        // ── Zhipu GLM ──────────────────────────────────────────────
        "glm-5" to ModelInfo(200_000),
        "glm-4.7" to ModelInfo(200_000),
        "glm-4.6" to ModelInfo(200_000),
        "glm-4" to ModelInfo(128_000),

        // ── DeepSeek ────────────────────────────────────────────────
        "deepseek-v3" to ModelInfo(128_000),
        "deepseek-v3.1" to ModelInfo(128_000),
        "deepseek-v3.2" to ModelInfo(128_000),
        "deepseek-r1" to ModelInfo(128_000),
        "deepseek-chat" to ModelInfo(128_000),
        "deepseek-reasoner" to ModelInfo(128_000),
        "deepseek-coder" to ModelInfo(128_000),

        // ── Alibaba Qwen ────────────────────────────────────────────
        "qwen3.5" to ModelInfo(262_144),
        "qwen3-coder" to ModelInfo(262_144),
        "qwen3" to ModelInfo(131_072),
        "qwq" to ModelInfo(131_072),
        "qwen2.5" to ModelInfo(131_072),
        "qwen-plus" to ModelInfo(131_072),
        "qwen-turbo" to ModelInfo(131_072),
        "qwen-max" to ModelInfo(131_072),

        // ── Meta Llama ──────────────────────────────────────────────
        "llama-4" to ModelInfo(1_048_576),
        "llama-3.3" to ModelInfo(131_072),
        "llama-3.1" to ModelInfo(131_072),

        // ── Mistral ─────────────────────────────────────────────────
        "mistral-large" to ModelInfo(128_000),
        "mistral-medium" to ModelInfo(128_000),
        "mistral-small" to ModelInfo(128_000),
    )

    /** Fallback for unknown models. */
    private val DEFAULT_INFO = ModelInfo(128_000)

    // ── Runtime model management ────────────────────────────────────

    /**
     * Register a model's context window discovered at runtime (from API, litellm sync, etc.).
     * Runtime data takes priority over hardcoded data.
     */
    fun putRuntime(modelId: String, contextWindow: Int) {
        if (contextWindow > 0) {
            runtimeModels[modelId] = contextWindow
        }
    }

    /** Bulk-update runtime models. */
    fun putAllRuntime(models: Map<String, Int>) {
        models.forEach { (id, ctx) -> if (ctx > 0) runtimeModels[id] = ctx }
    }

    /** Number of runtime-discovered models. */
    val runtimeModelCount: Int get() = runtimeModels.size

    /** Load cached runtime data from disk. Call once at app init. */
    fun loadFromDisk(context: Context) {
        try {
            val file = File(context.filesDir, CACHE_FILE)
            if (!file.exists()) return
            val jsonStr = file.readText()
            val json = Json.parseToJsonElement(jsonStr).jsonObject
            json.forEach { (key, value) ->
                val ctx = value.jsonPrimitive.int
                if (ctx > 0) runtimeModels[key] = ctx
            }
            Log.d(TAG, "Loaded ${runtimeModels.size} runtime models from disk")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load runtime model cache: ${e.message}")
        }
    }

    /** Persist runtime data to disk. */
    fun saveToDisk(context: Context) {
        try {
            val file = File(context.filesDir, CACHE_FILE)
            val entries = runtimeModels.entries.joinToString(",\n") { (k, v) ->
                "  \"${k.replace("\"", "\\\"")}\":$v"
            }
            file.writeText("{\n$entries\n}")
            Log.d(TAG, "Saved ${runtimeModels.size} runtime models to disk")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to save runtime model cache: ${e.message}")
        }
    }

    /**
     * Parse litellm's model_prices_and_context_window.json and extract context windows.
     * Expected format: { "model-name": { "max_input_tokens": 128000, ... }, ... }
     */
    fun parseLiteLLMJson(rootObj: JsonObject): Int {
        var count = 0
        for ((key, value) in rootObj) {
            try {
                val obj = value.jsonObject
                val maxInput = obj["max_input_tokens"]?.jsonPrimitive?.int
                    ?: obj["max_tokens"]?.jsonPrimitive?.int
                    ?: continue
                if (maxInput > 0) {
                    // Strip provider prefixes like "openai/", "anthropic/"
                    val cleanId = key.substringAfter("/")
                    runtimeModels[cleanId] = maxInput
                    count++
                }
            } catch (_: Exception) {
                // Skip non-object entries (e.g. "sample_spec")
            }
        }
        Log.d(TAG, "Parsed $count models from litellm JSON")
        return count
    }

    // ── Lookup ──────────────────────────────────────────────────────

    /**
     * Calculate compact threshold based on context window size.
     */
    fun defaultThresholdFor(contextWindow: Int): Int = when {
        contextWindow <= 128_000 -> (contextWindow * 0.70).toInt()
        contextWindow <= 256_000 -> (contextWindow * 0.75).toInt()
        contextWindow <= 500_000 -> (contextWindow * 0.80).toInt()
        else -> (contextWindow * 0.85).toInt()
    }

    /**
     * Look up model info by name.
     * Checks runtime data first, then hardcoded data, then defaults.
     * Supports partial matching (e.g., "gemini-2.5-flash-preview-05-20" matches "gemini-2.5-flash").
     */
    fun getModelInfo(model: String): ModelInfo {
        // 1. Exact match in runtime data
        runtimeModels[model]?.let { return ModelInfo(it) }
        // 2. Exact match in hardcoded data
        KNOWN_MODELS[model]?.let { return it }

        // 3. Partial match in runtime data (longest prefix wins)
        val runtimeMatch = runtimeModels.keys
            .filter { model.startsWith(it) }
            .maxByOrNull { it.length }
        runtimeMatch?.let { return ModelInfo(runtimeModels[it]!!) }

        // 4. Partial match in hardcoded data
        val knownMatch = KNOWN_MODELS.keys
            .filter { model.startsWith(it) }
            .maxByOrNull { it.length }
        return knownMatch?.let { KNOWN_MODELS[it] } ?: DEFAULT_INFO
    }

    /** Get compact threshold for a model, optionally using a custom context window. */
    fun getDefaultThreshold(model: String, customContextWindow: Int = 0): Int {
        return if (customContextWindow > 0) {
            defaultThresholdFor(customContextWindow)
        } else {
            getModelInfo(model).compactThreshold
        }
    }

    /** Get context window size for a model, optionally using a custom value. */
    fun getContextWindow(model: String, customContextWindow: Int = 0): Int {
        return if (customContextWindow > 0) customContextWindow
        else getModelInfo(model).contextWindow
    }

    /** Common compact threshold presets for UI display. */
    val THRESHOLD_PRESETS = listOf(
        0 to "Auto (based on model)",
        32_000 to "32K",
        64_000 to "64K",
        100_000 to "100K",
        128_000 to "128K",
        200_000 to "200K",
        500_000 to "500K",
        800_000 to "800K",
        1_000_000 to "1M",
    )

    /** Common context window presets for UI display. */
    val CONTEXT_WINDOW_PRESETS = listOf(
        0 to "Auto (based on model)",
        32_000 to "32K",
        64_000 to "64K",
        128_000 to "128K",
        200_000 to "200K",
        262_144 to "262K",
        400_000 to "400K",
        1_000_000 to "1M",
        1_048_576 to "1.05M",
        2_000_000 to "2M",
    )
}
