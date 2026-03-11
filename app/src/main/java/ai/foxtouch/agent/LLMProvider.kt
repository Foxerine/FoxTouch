package ai.foxtouch.agent

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Sealed hierarchy of LLM providers.
 * Each provider knows how to send chat completions with tool calling.
 */
sealed interface LLMProvider {
    val id: String
    val displayName: String
    val supportsVision: Boolean

    suspend fun chat(
        messages: List<ChatMessage>,
        tools: List<ToolDefinition>,
        model: String,
        streaming: Boolean = true,
        thinking: Boolean = true,
    ): Flow<LLMEvent>
}

@Serializable
data class ChatMessage(
    val role: String,
    val content: String? = null,
    val toolCalls: List<ToolCall>? = null,
    val toolCallId: String? = null,
    val toolName: String? = null,
    val imageBase64: String? = null,
)

@Serializable
data class ToolCall(
    val id: String,
    val name: String,
    val arguments: JsonObject,
    /** Gemini thought_signature — must be echoed back in conversation history. */
    val thoughtSignature: String? = null,
)

@Serializable
data class ToolDefinition(
    val name: String,
    val description: String,
    val parameters: JsonObject,
)

sealed interface LLMEvent {
    data class TextDelta(val text: String) : LLMEvent
    data class ToolCallRequest(val calls: List<ToolCall>) : LLMEvent
    /** Token usage reported by the API. Used for context compaction tracking. */
    data class Usage(val promptTokens: Int, val completionTokens: Int, val totalTokens: Int) : LLMEvent
    data class Error(val message: String) : LLMEvent
    data object Done : LLMEvent
}
