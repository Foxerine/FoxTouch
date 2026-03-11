package ai.foxtouch.agent

import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

class GeminiProvider(
    private val httpClient: HttpClient,
    private val json: Json,
    private val apiKey: String,
    private val baseUrl: String = "https://generativelanguage.googleapis.com/v1beta",
    private val onLog: ((String) -> Unit)? = null,
) : LLMProvider {

    companion object {
        private const val TAG = "FoxTouch.Gemini"
    }

    private fun log(message: String) {
        Log.d(TAG, message)
        onLog?.invoke(message)
    }

    override val id = "gemini"
    override val displayName = "Google Gemini"
    override val supportsVision = true

    override suspend fun chat(
        messages: List<ChatMessage>,
        tools: List<ToolDefinition>,
        model: String,
        streaming: Boolean,
        thinking: Boolean,
    ): Flow<LLMEvent> {
        // Force non-streaming when tools are present — Gemini 3+ requires thoughtSignature
        // in functionCall parts, which streaming mode does not reliably return.
        // Text-only responses (no tools) still use streaming for better UX.
        val effectiveStreaming = streaming && tools.isEmpty()
        return if (effectiveStreaming) chatStreaming(messages, tools, model, thinking)
        else chatNonStreaming(messages, tools, model, thinking)
    }

    // ── Non-streaming ──────────────────────────────────────────────────

    private fun chatNonStreaming(
        messages: List<ChatMessage>,
        tools: List<ToolDefinition>,
        model: String,
        thinking: Boolean,
    ): Flow<LLMEvent> = flow {
        val url = "$baseUrl/models/$model:generateContent?key=$apiKey"
        val body = buildGeminiRequest(messages, tools, thinking)

        val (statusCode, responseJson) = SseUtil.postJson(
            httpClient = httpClient,
            url = url,
            json = json,
        ) {
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(JsonObject.serializer(), body))
        }

        if (statusCode !in 200..299) {
            emit(LLMEvent.Error("Gemini HTTP $statusCode: ${responseJson?.toString()?.take(300) ?: "(no body)"}"))
            emit(LLMEvent.Done)
            return@flow
        }

        if (responseJson == null) {
            emit(LLMEvent.Error("Gemini: invalid JSON response"))
            emit(LLMEvent.Done)
            return@flow
        }

        checkTopLevelErrors(responseJson)?.let { emit(it); emit(LLMEvent.Done); return@flow }
        emitCandidates(responseJson, responseJson.toString())
        emitUsageMetadata(responseJson)
        emit(LLMEvent.Done)
    }

    // ── Streaming ──────────────────────────────────────────────────────

    private fun chatStreaming(
        messages: List<ChatMessage>,
        tools: List<ToolDefinition>,
        model: String,
        thinking: Boolean,
    ): Flow<LLMEvent> = flow {
        val url = "$baseUrl/models/$model:streamGenerateContent?alt=sse&key=$apiKey"
        val body = buildGeminiRequest(messages, tools, thinking)
        var hasContent = false

        SseUtil.streamSse(
            httpClient = httpClient,
            url = url,
            json = json,
            configure = {
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(JsonObject.serializer(), body))
            },
            onHttpError = { statusCode, errorBody ->
                emit(LLMEvent.Error("Gemini HTTP $statusCode: $errorBody"))
            },
            onChunk = { chunk ->
                val candidates = chunk["candidates"]?.jsonArray ?: return@streamSse

                for (candidate in candidates) {
                    val content = candidate.jsonObject["content"]?.jsonObject ?: continue
                    val parts = content["parts"]?.jsonArray ?: continue

                    for (part in parts) {
                        val parsed = parsePart(part.jsonObject)
                        if (parsed != null) {
                            hasContent = true
                            emit(parsed)
                        }
                    }
                }
            },
        )

        if (!hasContent) {
            emit(LLMEvent.Error("Gemini streaming: empty response"))
        }
        // Note: streaming mode may not reliably provide usageMetadata in every chunk
        emit(LLMEvent.Done)
    }

    private suspend fun kotlinx.coroutines.flow.FlowCollector<LLMEvent>.emitUsageMetadata(
        responseJson: JsonObject,
    ) {
        val usage = responseJson["usageMetadata"]?.jsonObject ?: return
        val prompt = usage["promptTokenCount"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
        val completion = usage["candidatesTokenCount"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
        val total = usage["totalTokenCount"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
        if (prompt > 0 || total > 0) {
            emit(LLMEvent.Usage(promptTokens = prompt, completionTokens = completion, totalTokens = total))
        }
    }

    // ── Response parsing helpers ───────────────────────────────────────

    private fun checkTopLevelErrors(responseJson: JsonObject): LLMEvent.Error? {
        val promptFeedback = responseJson["promptFeedback"]?.jsonObject
        if (promptFeedback != null) {
            val blockReason = promptFeedback["blockReason"]?.jsonPrimitive?.content
            if (blockReason != null) return LLMEvent.Error("Gemini blocked: $blockReason")
        }
        val error = responseJson["error"]?.jsonObject
        if (error != null) {
            val msg = error["message"]?.jsonPrimitive?.content ?: "Unknown error"
            return LLMEvent.Error("Gemini error: $msg")
        }
        return null
    }

    /** Parse a single part into an LLMEvent, or null if it's a thought/empty. */
    private fun parsePart(partObj: JsonObject): LLMEvent? {
        val text = partObj["text"]?.jsonPrimitive?.content
        val functionCall = partObj["functionCall"]?.jsonObject
        // Skip thought parts (they contain model reasoning, not user-visible text)
        val isThought = partObj["thought"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() == true

        if (text != null && text.isNotEmpty() && !isThought) {
            return LLMEvent.TextDelta(text)
        }

        if (functionCall != null) {
            val fnName = functionCall["name"]?.jsonPrimitive?.content ?: return null
            // thought_signature can be at part level or inside functionCall, camelCase or snake_case
            val thoughtSig = partObj["thought_signature"]?.jsonPrimitive?.content
                ?: partObj["thoughtSignature"]?.jsonPrimitive?.content
                ?: functionCall["thought_signature"]?.jsonPrimitive?.content
                ?: functionCall["thoughtSignature"]?.jsonPrimitive?.content
            log("functionCall raw part: $partObj")
            if (thoughtSig == null) {
                log("WARNING: No thought_signature for $fnName! part keys=${partObj.keys}, fc keys=${functionCall.keys}")
            }
            return LLMEvent.ToolCallRequest(listOf(ToolCall(
                id = "call_${System.currentTimeMillis()}",
                name = fnName,
                arguments = functionCall["args"]?.jsonObject ?: JsonObject(emptyMap()),
                thoughtSignature = thoughtSig,
            )))
        }

        return null
    }

    private suspend fun FlowCollector<LLMEvent>.emitCandidates(
        responseJson: JsonObject,
        rawBody: String,
    ) {
        val candidates = responseJson["candidates"]?.jsonArray
        if (candidates == null || candidates.isEmpty()) {
            emit(LLMEvent.Error("Gemini: no candidates. Raw: ${rawBody.take(300)}"))
            return
        }

        var hasContent = false
        for (candidate in candidates) {
            val candidateObj = candidate.jsonObject
            val finishReason = candidateObj["finishReason"]?.jsonPrimitive?.content
            if (finishReason != null && finishReason != "STOP") {
                emit(LLMEvent.Error("Gemini response blocked: $finishReason"))
            }

            val content = candidateObj["content"]?.jsonObject ?: continue
            val parts = content["parts"]?.jsonArray ?: continue

            for (part in parts) {
                val parsed = parsePart(part.jsonObject)
                if (parsed != null) {
                    hasContent = true
                    emit(parsed)
                }
            }
        }

        if (!hasContent) {
            emit(LLMEvent.Error("Gemini: candidates but no content. Raw: ${rawBody.take(300)}"))
        }
    }

    // ── Request building ───────────────────────────────────────────────

    private fun buildGeminiRequest(
        messages: List<ChatMessage>,
        tools: List<ToolDefinition>,
        thinking: Boolean = true,
    ): JsonObject = buildJsonObject {
        val systemMessage = messages.firstOrNull { it.role == "system" }
        if (systemMessage?.content != null) {
            put("systemInstruction", buildJsonObject {
                put("parts", buildJsonArray {
                    add(buildJsonObject { put("text", systemMessage.content) })
                })
            })
        }

        put("contents", buildJsonArray {
            for (msg in messages) {
                if (msg.role == "system") continue

                when (msg.role) {
                    "tool" -> {
                        add(buildJsonObject {
                            put("role", "function")
                            put("parts", buildJsonArray {
                                add(buildJsonObject {
                                    put("functionResponse", buildJsonObject {
                                        put("name", msg.toolName ?: msg.toolCallId ?: "unknown")
                                        put("response", buildJsonObject {
                                            put("result", msg.content ?: "")
                                        })
                                    })
                                })
                            })
                        })
                    }
                    "assistant" -> {
                        add(buildJsonObject {
                            put("role", "model")
                            put("parts", buildJsonArray {
                                if (msg.content != null) {
                                    add(buildJsonObject { put("text", msg.content) })
                                }
                                if (msg.toolCalls != null) {
                                    for (call in msg.toolCalls) {
                                        add(buildJsonObject {
                                            put("functionCall", buildJsonObject {
                                                put("name", call.name)
                                                put("args", call.arguments)
                                            })
                                            // thoughtSignature at part level (sibling to functionCall, camelCase per Gemini docs)
                                            if (call.thoughtSignature != null) {
                                                put("thoughtSignature", call.thoughtSignature)
                                            }
                                        })
                                    }
                                }
                            })
                        })
                    }
                    else -> {
                        add(buildJsonObject {
                            put("role", "user")
                            put("parts", buildJsonArray {
                                if (msg.content != null) {
                                    add(buildJsonObject { put("text", msg.content) })
                                }
                                if (msg.imageBase64 != null) {
                                    add(buildJsonObject {
                                        put("inlineData", buildJsonObject {
                                            put("mimeType", "image/jpeg")
                                            put("data", msg.imageBase64)
                                        })
                                    })
                                }
                            })
                        })
                    }
                }
            }
        })

        if (tools.isNotEmpty()) {
            put("tools", buildJsonArray {
                add(buildJsonObject {
                    put("functionDeclarations", buildJsonArray {
                        for (tool in tools) {
                            add(buildJsonObject {
                                put("name", tool.name)
                                put("description", tool.description)
                                put("parameters", tool.parameters)
                            })
                        }
                    })
                })
            })
        }

        put("generationConfig", buildJsonObject {
            put("thinkingConfig", buildJsonObject {
                put("thinkingBudget", if (thinking) -1 else 0)
            })
        })
    }
}
