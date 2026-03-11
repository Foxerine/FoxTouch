package ai.foxtouch.agent

/**
 * Available LLM providers and their models.
 */
data class ProviderInfo(
    val id: String,
    val displayName: String,
    val models: List<ModelInfo>,
)

data class ModelInfo(
    val id: String,
    val displayName: String,
    val supportsVision: Boolean = true,
    val warning: String? = null,
)

val AVAILABLE_PROVIDERS = listOf(
    ProviderInfo(
        id = "gemini",
        displayName = "Google Gemini",
        models = listOf(
            ModelInfo("gemini-3-flash", "Gemini 3 Flash"),
            ModelInfo("gemini-3.1-pro", "Gemini 3.1 Pro"),
            ModelInfo("gemini-2.5-flash", "Gemini 2.5 Flash"),
            ModelInfo("gemini-2.5-pro", "Gemini 2.5 Pro"),
            ModelInfo("gemini-2.0-flash", "Gemini 2.0 Flash"),
        ),
    ),
    ProviderInfo(
        id = "claude",
        displayName = "Anthropic Claude",
        models = listOf(
            ModelInfo("claude-sonnet-4-5-20250514", "Claude Sonnet 4.5"),
            ModelInfo("claude-sonnet-4-20250514", "Claude Sonnet 4"),
            ModelInfo("claude-haiku-4-5-20250514", "Claude Haiku 4.5"),
        ),
    ),
    ProviderInfo(
        id = "openai",
        displayName = "OpenAI",
        models = listOf(
            ModelInfo("gpt-5.4", "GPT-5.4"),
            ModelInfo("gpt-4.1", "GPT-4.1"),
            ModelInfo("gpt-4o", "GPT-4o"),
            ModelInfo("gpt-4o-mini", "GPT-4o Mini"),
            ModelInfo("gpt-4.1-mini", "GPT-4.1 Mini"),
        ),
    ),
)

/**
 * Classify a dynamically-fetched Gemini model and annotate with quality warnings.
 *
 * Warnings are shown for models that are known to be insufficient for agent tasks
 * (e.g. Nano, 8B, embedding, 1.0).
 */
fun classifyGeminiModel(model: ModelInfo): ModelInfo {
    val id = model.id.lowercase()

    // Filter out non-chat models that somehow passed the generateContent check
    val isEmbeddingOrAqa = "embedding" in id || "aqa" in id || "retrieval" in id
    if (isEmbeddingOrAqa) {
        return model.copy(warning = "Not a chat model. Cannot be used for agent tasks.")
    }

    val isNano = "nano" in id
    val isLite = "lite" in id
    val is8b = "8b" in id || "flash-8b" in id
    val isExperimental = "exp" in id && "experimental" in id

    val is3x = "3.1" in id || "3.0" in id || (id.contains("gemini-3") && !id.contains("gemini-3."))
    val is15 = "1.5" in id
    val is10 = "1.0" in id
    val isPro = "pro" in id
    val isFlash = "flash" in id

    val warning = when {
        isNano -> "On-device model, too small for reliable tool use and screen understanding."
        isLite && !is3x -> "Lite model with reduced reasoning. May fail complex multi-step tasks."
        is8b -> "Small model (8B params). Insufficient for reliable agent control."
        is10 -> "Gemini 1.0 lacks the vision and tool-use quality needed for FoxTouch."
        is15 && isFlash && !isPro -> "Gemini 1.5 Flash works but newer Flash models are significantly better."
        isExperimental -> "Experimental model — may be unstable or change without notice."
        else -> null
    }

    return model.copy(warning = warning)
}

/** Filter: is this an OpenAI model suitable for chat/agent use? */
fun isOpenAIChatModel(id: String): Boolean {
    val lower = id.lowercase()
    // Include GPT, O-series reasoning, ChatGPT models
    val chatPrefixes = listOf("gpt-", "o1", "o3", "o4", "chatgpt-")
    if (chatPrefixes.none { lower.startsWith(it) }) return false
    // Exclude non-chat variants
    val excludes = listOf("realtime", "audio", "transcribe", "search")
    if (excludes.any { it in lower }) return false
    return true
}

/**
 * Classify a dynamically-fetched OpenAI model and annotate with quality warnings.
 */
fun classifyOpenAIModel(model: ModelInfo): ModelInfo {
    val id = model.id.lowercase()

    val warning = when {
        id.startsWith("gpt-3") -> "GPT-3.5 has limited reasoning. Not recommended for agent tasks."
        id.startsWith("gpt-4-") && !id.contains("gpt-4o") && !id.contains("gpt-4.") ->
            "Older GPT-4 model. Consider using GPT-5.4 or GPT-4.1 instead."
        else -> null
    }

    return model.copy(warning = warning)
}

/**
 * Classify a dynamically-fetched Anthropic model and annotate with quality warnings.
 */
fun classifyClaudeModel(model: ModelInfo): ModelInfo {
    val id = model.id.lowercase()

    val warning = when {
        "claude-2" in id || "claude-instant" in id ->
            "Legacy model. Use Claude 4+ for reliable agent tasks."
        "claude-3-" in id && !("claude-3-5" in id || "claude-3.5" in id) ->
            "Older Claude 3 model. Claude 4+ recommended."
        else -> null
    }

    return model.copy(warning = warning)
}

fun getProviderInfo(providerId: String): ProviderInfo? =
    AVAILABLE_PROVIDERS.find { it.id == providerId }

fun getDefaultModel(providerId: String): String =
    getProviderInfo(providerId)?.models?.firstOrNull()?.id ?: "gemini-2.5-flash"
