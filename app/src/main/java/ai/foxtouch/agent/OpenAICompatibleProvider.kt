package ai.foxtouch.agent

import android.util.Log
import com.aallam.openai.api.chat.ChatCompletionRequest
import com.aallam.openai.api.chat.ChatMessage as OaiChatMessage
import com.aallam.openai.api.chat.ChatRole
import com.aallam.openai.api.chat.FunctionCall
import com.aallam.openai.api.chat.FunctionTool
import com.aallam.openai.api.chat.ImagePart
import com.aallam.openai.api.chat.ListContent
import com.aallam.openai.api.chat.TextPart
import com.aallam.openai.api.chat.Tool
import com.aallam.openai.api.chat.ToolCall as OaiToolCall
import com.aallam.openai.api.chat.ToolId
import com.aallam.openai.api.core.Parameters
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.client.OpenAI
import com.aallam.openai.client.OpenAIHost
import com.aallam.openai.client.ProxyConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/**
 * OpenAI-compatible provider using the official openai-kotlin SDK.
 * Supports OpenAI, DeepSeek, and any OpenAI-compatible API.
 */
class OpenAICompatibleProvider(
    private val json: Json,
    private val apiKey: String,
    override val id: String = "openai",
    override val displayName: String = "OpenAI",
    override val supportsVision: Boolean = true,
    private val baseUrl: String = "https://api.openai.com/v1",
    private val proxyUrl: String? = null,
) : LLMProvider {

    companion object {
        private const val TAG = "FoxTouch.OpenAI"
    }

    private val openAI = OpenAI(
        token = apiKey,
        host = OpenAIHost(baseUrl = "${baseUrl.trimEnd('/')}/"),
        proxy = proxyUrl?.let { ProxyConfig.Http(it) },
    )

    override suspend fun chat(
        messages: List<ChatMessage>,
        tools: List<ToolDefinition>,
        model: String,
        streaming: Boolean,
        thinking: Boolean,
    ): Flow<LLMEvent> = flow {
        val oaiMessages = messages.map { it.toOaiMessage() }
        val oaiTools = tools.map { it.toOaiTool() }.ifEmpty { null }

        val request = ChatCompletionRequest(
            model = ModelId(model),
            messages = oaiMessages,
            tools = oaiTools,
        )

        val pendingToolCalls = mutableMapOf<Int, ToolCallBuilder>()

        try {
            openAI.chatCompletions(request).collect { chunk ->
                for (choice in chunk.choices) {
                    val delta = choice.delta ?: continue

                    // Text content
                    val text = delta.content
                    if (!text.isNullOrEmpty()) {
                        emit(LLMEvent.TextDelta(text))
                    }

                    // Tool calls (streamed as ToolCallChunk with index)
                    delta.toolCalls?.forEach { tc ->
                        val index = tc.index
                        val builder = pendingToolCalls.getOrPut(index) { ToolCallBuilder() }
                        tc.id?.let { builder.id = it.id }
                        tc.function?.nameOrNull?.let { builder.name = it }
                        tc.function?.argumentsOrNull?.let { builder.argsBuffer.append(it) }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "OpenAI streaming error", e)
            emit(LLMEvent.Error("OpenAI error: ${e.message}"))
            emit(LLMEvent.Done)
            return@flow
        }

        // Emit accumulated tool calls
        if (pendingToolCalls.isNotEmpty()) {
            val calls = pendingToolCalls.values.mapNotNull { builder ->
                val name = builder.name ?: return@mapNotNull null
                val callId = builder.id ?: "call_${System.currentTimeMillis()}"
                val args = try {
                    json.parseToJsonElement(builder.argsBuffer.toString()).jsonObject
                } catch (_: Exception) {
                    JsonObject(emptyMap())
                }
                ToolCall(id = callId, name = name, arguments = args)
            }
            if (calls.isNotEmpty()) emit(LLMEvent.ToolCallRequest(calls))
        }

        emit(LLMEvent.Done)
    }

    // ── Conversion helpers ───────────────────────────────────────────

    private fun ChatMessage.toOaiMessage(): OaiChatMessage = when {
        role == "tool" -> OaiChatMessage(
            role = ChatRole.Tool,
            content = content ?: "",
            toolCallId = toolCallId?.let { ToolId(it) },
        )
        role == "assistant" -> {
            val oaiToolCalls = toolCalls?.map { call ->
                OaiToolCall.Function(
                    id = ToolId(call.id),
                    function = FunctionCall(
                        nameOrNull = call.name,
                        argumentsOrNull = json.encodeToString(JsonObject.serializer(), call.arguments),
                    ),
                )
            }
            OaiChatMessage(
                role = ChatRole.Assistant,
                content = content,
                toolCalls = oaiToolCalls,
            )
        }
        role == "system" -> OaiChatMessage(
            role = ChatRole.System,
            content = content ?: "",
        )
        else -> {
            // User message — may have vision content
            if (imageBase64 != null) {
                OaiChatMessage(
                    role = ChatRole.User,
                    messageContent = ListContent(
                        buildList {
                            if (content != null) add(TextPart(content))
                            add(ImagePart("data:image/jpeg;base64,$imageBase64"))
                        },
                    ),
                )
            } else {
                OaiChatMessage(
                    role = ChatRole.User,
                    content = content ?: "",
                )
            }
        }
    }

    private fun ToolDefinition.toOaiTool(): Tool = Tool(
        type = com.aallam.openai.api.chat.ToolType.Function,
        function = FunctionTool(
            name = name,
            description = description,
            parameters = Parameters.fromJsonString(
                json.encodeToString(JsonObject.serializer(), parameters),
            ),
        ),
    )

    private class ToolCallBuilder {
        var id: String? = null
        var name: String? = null
        val argsBuffer = StringBuilder()
    }
}
