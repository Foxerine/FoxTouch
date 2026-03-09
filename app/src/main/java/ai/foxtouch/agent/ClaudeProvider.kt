package ai.foxtouch.agent

import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Claude Messages API provider (not OpenAI-compatible).
 * Uses Anthropic's native streaming format with SSE events.
 */
class ClaudeProvider(
    private val httpClient: HttpClient,
    private val json: Json,
    private val apiKey: String,
    private val baseUrl: String = "https://api.anthropic.com/v1",
) : LLMProvider {

    companion object {
        private const val TAG = "FoxTouch.Claude"
    }

    override val id = "claude"
    override val displayName = "Anthropic Claude"
    override val supportsVision = true

    override suspend fun chat(
        messages: List<ChatMessage>,
        tools: List<ToolDefinition>,
        model: String,
        streaming: Boolean,
        thinking: Boolean,
    ): Flow<LLMEvent> = flow {
        val url = "$baseUrl/messages"

        val systemContent = messages.firstOrNull { it.role == "system" }?.content
        // Build per-message JSON, then merge consecutive same-role messages.
        // Claude API requires strictly alternating user/assistant turns.
        // Tool results map to "user" role, so consecutive tool results + user image
        // messages must be merged into a single user turn.
        val apiMessages = mergeConsecutiveRoles(
            messages.filter { it.role != "system" }.map { msg -> buildClaudeMessage(msg) },
        )

        val body = buildJsonObject {
            put("model", model)
            put("max_tokens", if (thinking) 16384 else 8192)
            put("stream", true)
            if (thinking) {
                put("thinking", buildJsonObject {
                    put("type", "enabled")
                    put("budget_tokens", 8192)
                })
            }
            if (systemContent != null) {
                put("system", systemContent)
            }
            put("messages", buildJsonArray { apiMessages.forEach { add(it) } })
            if (tools.isNotEmpty()) {
                put("tools", buildJsonArray {
                    for (tool in tools) {
                        add(buildJsonObject {
                            put("name", tool.name)
                            put("description", tool.description)
                            put("input_schema", tool.parameters)
                        })
                    }
                })
            }
        }

        val toolUseBlocks = mutableMapOf<Int, ToolCallBuilder>()

        SseUtil.streamSse(
            httpClient = httpClient,
            url = url,
            json = json,
            configure = {
                contentType(ContentType.Application.Json)
                header("x-api-key", apiKey)
                header("anthropic-version", "2023-06-01")
                setBody(json.encodeToString(JsonObject.serializer(), body))
            },
            onHttpError = { statusCode, errorBody ->
                emit(LLMEvent.Error("Claude HTTP $statusCode: $errorBody"))
            },
            onChunk = { event ->
                val type = event["type"]?.jsonPrimitive?.content

                when (type) {
                    "content_block_start" -> {
                        val index = event["index"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
                        val block = event["content_block"]?.jsonObject
                        if (block?.get("type")?.jsonPrimitive?.content == "tool_use") {
                            val builder = ToolCallBuilder()
                            builder.id = block["id"]?.jsonPrimitive?.content
                            builder.name = block["name"]?.jsonPrimitive?.content
                            toolUseBlocks[index] = builder
                        }
                    }
                    "content_block_delta" -> {
                        val index = event["index"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
                        val delta = event["delta"]?.jsonObject
                        val deltaType = delta?.get("type")?.jsonPrimitive?.content

                        when (deltaType) {
                            "text_delta" -> {
                                val text = delta["text"]?.jsonPrimitive?.content ?: ""
                                if (text.isNotEmpty()) emit(LLMEvent.TextDelta(text))
                            }
                            "input_json_delta" -> {
                                val partial = delta["partial_json"]?.jsonPrimitive?.content ?: ""
                                toolUseBlocks[index]?.argsBuffer?.append(partial)
                            }
                        }
                    }
                    "message_stop" -> {}
                }
            },
        )

        // Emit tool calls if any
        if (toolUseBlocks.isNotEmpty()) {
            val calls = toolUseBlocks.values.mapNotNull { builder ->
                val name = builder.name ?: return@mapNotNull null
                val id = builder.id ?: "call_${System.currentTimeMillis()}"
                val args = try {
                    json.parseToJsonElement(builder.argsBuffer.toString()).jsonObject
                } catch (_: Exception) {
                    JsonObject(emptyMap())
                }
                ToolCall(id = id, name = name, arguments = args)
            }
            if (calls.isNotEmpty()) emit(LLMEvent.ToolCallRequest(calls))
        }

        emit(LLMEvent.Done)
    }

    private fun buildClaudeMessage(msg: ChatMessage): JsonObject = buildJsonObject {
        put("role", when (msg.role) {
            "tool" -> "user"
            else -> msg.role
        })
        put("content", buildJsonArray {
            when {
                msg.role == "tool" -> {
                    add(buildJsonObject {
                        put("type", "tool_result")
                        put("tool_use_id", msg.toolCallId ?: "")
                        put("content", msg.content ?: "")
                    })
                }
                msg.imageBase64 != null -> {
                    if (msg.content != null) {
                        add(buildJsonObject {
                            put("type", "text")
                            put("text", msg.content)
                        })
                    }
                    add(buildJsonObject {
                        put("type", "image")
                        put("source", buildJsonObject {
                            put("type", "base64")
                            put("media_type", "image/jpeg")
                            put("data", msg.imageBase64)
                        })
                    })
                }
                msg.toolCalls != null -> {
                    if (!msg.content.isNullOrBlank()) {
                        add(buildJsonObject {
                            put("type", "text")
                            put("text", msg.content)
                        })
                    }
                    for (call in msg.toolCalls) {
                        add(buildJsonObject {
                            put("type", "tool_use")
                            put("id", call.id)
                            put("name", call.name)
                            put("input", call.arguments)
                        })
                    }
                }
                else -> {
                    add(buildJsonObject {
                        put("type", "text")
                        put("text", msg.content ?: "")
                    })
                }
            }
        })
    }

    /**
     * Merge consecutive messages with the same role into a single message.
     * Claude API requires strictly alternating user/assistant turns.
     * Tool results (mapped to "user") followed by user image messages must be combined.
     */
    private fun mergeConsecutiveRoles(messages: List<JsonObject>): List<JsonObject> {
        if (messages.isEmpty()) return messages
        val merged = mutableListOf<JsonObject>()
        for (msg in messages) {
            val role = msg["role"]?.jsonPrimitive?.content
            val prevRole = merged.lastOrNull()?.get("role")?.jsonPrimitive?.content
            if (role == prevRole && role != null) {
                // Merge content arrays
                val prev = merged.removeLast()
                val prevContent = prev["content"]?.jsonArray ?: buildJsonArray {}
                val currContent = msg["content"]?.jsonArray ?: buildJsonArray {}
                merged.add(buildJsonObject {
                    put("role", role)
                    put("content", buildJsonArray {
                        prevContent.forEach { add(it) }
                        currContent.forEach { add(it) }
                    })
                })
            } else {
                merged.add(msg)
            }
        }
        return merged
    }

    private class ToolCallBuilder {
        var id: String? = null
        var name: String? = null
        val argsBuffer = StringBuilder()
    }
}
