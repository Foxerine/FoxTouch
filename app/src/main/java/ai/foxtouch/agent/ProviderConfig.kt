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
    val recommended: Boolean = false,
    val warning: String? = null,
)

val AVAILABLE_PROVIDERS = listOf(
    ProviderInfo(
        id = "gemini",
        displayName = "Google Gemini",
        models = listOf(
            ModelInfo("gemini-2.5-flash", "Gemini 2.5 Flash", recommended = true),
            ModelInfo("gemini-2.5-pro", "Gemini 2.5 Pro", recommended = true),
            ModelInfo("gemini-2.0-flash", "Gemini 2.0 Flash"),
        ),
    ),
    ProviderInfo(
        id = "claude",
        displayName = "Anthropic Claude",
        models = listOf(
            ModelInfo("claude-sonnet-4-5-20250514", "Claude Sonnet 4.5", recommended = true),
            ModelInfo("claude-sonnet-4-20250514", "Claude Sonnet 4", recommended = true),
            ModelInfo("claude-haiku-4-5-20250514", "Claude Haiku 4.5"),
        ),
    ),
    ProviderInfo(
        id = "openai",
        displayName = "OpenAI",
        models = listOf(
            ModelInfo("gpt-4o", "GPT-4o", recommended = true),
            ModelInfo("gpt-4o-mini", "GPT-4o Mini"),
            ModelInfo("gpt-4.1", "GPT-4.1", recommended = true),
            ModelInfo("gpt-4.1-mini", "GPT-4.1 Mini"),
        ),
    ),
)

/**
 * Classify a dynamically-fetched Gemini model and annotate with quality info.
 *
 * Quality tiers for FoxTouch agent use (requires vision + tool calling):
 * - Recommended: Gemini 2.5 Pro, Gemini 2.5 Flash (best reasoning + tool use)
 * - Acceptable: Gemini 2.0 Flash, Gemini 1.5 Pro (solid but older)
 * - Not recommended: Lite, Nano, 8B, embedding, AQA, 1.0 (insufficient for agent control)
 */
fun classifyGeminiModel(model: ModelInfo): ModelInfo {
    val id = model.id.lowercase()

    // Filter out non-chat models that somehow passed the generateContent check
    val isEmbeddingOrAqa = "embedding" in id || "aqa" in id || "retrieval" in id
    if (isEmbeddingOrAqa) {
        return model.copy(warning = "Not a chat model. Cannot be used for agent tasks.", recommended = false)
    }

    // Identify model characteristics
    val isNano = "nano" in id
    val isLite = "lite" in id
    val is8b = "8b" in id || "flash-8b" in id
    val isExperimental = "exp" in id && "experimental" in id
    val isThinking = "thinking" in id

    val is25 = "2.5" in id
    val is20 = "2.0" in id
    val is15 = "1.5" in id
    val is10 = "1.0" in id

    val isPro = "pro" in id
    val isFlash = "flash" in id

    val warning = when {
        isNano -> "On-device model, too small for reliable tool use and screen understanding."
        isLite -> "Lite model with reduced reasoning. May fail complex multi-step tasks."
        is8b -> "Small model (8B params). Insufficient for reliable agent control."
        is10 -> "Gemini 1.0 lacks the vision and tool-use quality needed for FoxTouch."
        is15 && isFlash && !isPro -> "Gemini 1.5 Flash works but 2.5 Flash is significantly better."
        is15 && isPro -> null // 1.5 Pro is acceptable
        is20 && isFlash -> null // 2.0 Flash is acceptable
        is25 -> null // 2.5 models are recommended
        isExperimental -> "Experimental model — may be unstable or change without notice."
        else -> null
    }

    val recommended = is25 && !isNano && !isLite && !is8b

    return model.copy(recommended = recommended, warning = warning)
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
 * Classify a dynamically-fetched OpenAI model and annotate with quality info.
 */
fun classifyOpenAIModel(model: ModelInfo): ModelInfo {
    val id = model.id.lowercase()

    val isO = id.startsWith("o1") || id.startsWith("o3") || id.startsWith("o4")
    val isMini = "mini" in id
    val isGpt4 = id.startsWith("gpt-4")
    val isGpt3 = id.startsWith("gpt-3")

    val warning = when {
        isGpt3 -> "GPT-3.5 has limited reasoning. Not recommended for agent tasks."
        id.startsWith("gpt-4-") && !id.contains("gpt-4o") && !id.contains("gpt-4.") ->
            "Older GPT-4 model. Consider using GPT-4o or GPT-4.1 instead."
        else -> null
    }

    val recommended = when {
        isGpt3 -> false
        isO && !isMini -> true
        id.startsWith("gpt-4o") && !isMini -> true
        id.startsWith("gpt-4.1") && !isMini -> true
        else -> false
    }

    return model.copy(recommended = recommended, warning = warning)
}

/**
 * Classify a dynamically-fetched Anthropic model and annotate with quality info.
 */
fun classifyClaudeModel(model: ModelInfo): ModelInfo {
    val id = model.id.lowercase()

    val isHaiku = "haiku" in id
    val isSonnet = "sonnet" in id
    val isOpus = "opus" in id

    val warning = when {
        "claude-2" in id || "claude-instant" in id ->
            "Legacy model. Use Claude 3.5+ for reliable agent tasks."
        "claude-3-" in id && !("claude-3-5" in id || "claude-3.5" in id) ->
            "Older Claude 3 model. Claude 3.5+ recommended."
        else -> null
    }

    val recommended = when {
        isOpus -> true
        isSonnet && ("4.5" in id || "4-5" in id || "sonnet-4" in id) -> true
        isHaiku && ("4.5" in id || "4-5" in id) -> false
        else -> false
    }

    return model.copy(recommended = recommended, warning = warning)
}

fun getProviderInfo(providerId: String): ProviderInfo? =
    AVAILABLE_PROVIDERS.find { it.id == providerId }

fun getDefaultModel(providerId: String): String =
    getProviderInfo(providerId)?.models?.firstOrNull()?.id ?: "gemini-2.5-flash"
