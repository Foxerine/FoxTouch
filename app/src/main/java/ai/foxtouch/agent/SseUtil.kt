package ai.foxtouch.agent

import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.preparePost
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.utils.io.readUTF8Line
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/**
 * Shared SSE (Server-Sent Events) streaming utility for LLM providers.
 * Handles the common pattern of: POST → read SSE lines → parse JSON chunks.
 */
object SseUtil {

    /**
     * Result of a single SSE data line parsed as JSON.
     */
    sealed interface SseResult {
        data class JsonChunk(val data: JsonObject) : SseResult
        data class HttpError(val statusCode: Int, val body: String) : SseResult
    }

    /**
     * Stream SSE events from an HTTP POST endpoint.
     *
     * @param httpClient Ktor HTTP client
     * @param url Full endpoint URL
     * @param json Json serializer for parsing
     * @param configure Lambda to configure the request (headers, body, etc.)
     * @param onChunk Called for each parsed JSON chunk from an SSE `data:` line
     * @param onHttpError Called if HTTP status is not 2xx. Return to abort streaming.
     * @param stopToken Optional token that signals end of stream (e.g., "[DONE]" for OpenAI)
     */
    suspend fun streamSse(
        httpClient: HttpClient,
        url: String,
        json: Json,
        configure: HttpRequestBuilder.() -> Unit,
        onChunk: suspend (JsonObject) -> Unit,
        onHttpError: suspend (statusCode: Int, body: String) -> Unit,
        stopToken: String? = null,
    ) {
        httpClient.preparePost(url, configure).execute { response ->
            val statusCode = response.status.value
            if (statusCode !in 200..299) {
                val errorBody = try {
                    response.bodyAsText().take(500)
                } catch (_: Exception) {
                    "(unreadable)"
                }
                onHttpError(statusCode, errorBody)
                return@execute
            }

            val channel = response.bodyAsChannel()
            while (!channel.isClosedForRead) {
                val line = channel.readUTF8Line() ?: break
                if (!line.startsWith("data: ")) continue
                val data = line.removePrefix("data: ").trim()
                if (data.isEmpty()) continue
                if (stopToken != null && data == stopToken) break

                try {
                    val chunk = json.parseToJsonElement(data).jsonObject
                    onChunk(chunk)
                } catch (_: Exception) {
                    // Skip malformed lines
                }
            }
        }
    }

    /**
     * Non-streaming POST request that returns parsed JSON response.
     */
    suspend fun postJson(
        httpClient: HttpClient,
        url: String,
        json: Json,
        configure: HttpRequestBuilder.() -> Unit,
    ): Pair<Int, JsonObject?> {
        var statusCode = 0
        var result: JsonObject? = null

        httpClient.preparePost(url, configure).execute { response ->
            statusCode = response.status.value
            val body = response.bodyAsText()
            result = try {
                json.parseToJsonElement(body).jsonObject
            } catch (_: Exception) {
                null
            }
        }

        return statusCode to result
    }
}
