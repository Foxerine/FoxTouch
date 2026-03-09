package ai.foxtouch.data.foxline

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class LoginRequest(val phone: String, val verification_code: String)

@Serializable
data class LoginResponse(val access_token: String, val token_type: String)

@Serializable
data class UserInfo(val id: String, val phone: String? = null, val balance: Int = 0)

@Singleton
class FoxlineClient @Inject constructor(
    private val httpClient: HttpClient,
    private val json: Json,
) {
    companion object {
        const val DEFAULT_BASE_URL = "https://api.foxline.ai"
    }

    private var baseUrl: String = DEFAULT_BASE_URL
    private var token: String? = null

    val isLoggedIn: Boolean get() = token != null

    fun setBaseUrl(url: String) {
        baseUrl = url.trimEnd('/')
    }

    suspend fun login(phone: String, verificationCode: String): Boolean {
        return try {
            val response = httpClient.post("$baseUrl/api/v1/auth/token") {
                contentType(ContentType.Application.Json)
                setBody(LoginRequest(phone, verificationCode))
            }
            val loginResponse: LoginResponse = response.body()
            token = loginResponse.access_token
            true
        } catch (_: Exception) {
            false
        }
    }

    suspend fun getUserInfo(): UserInfo? {
        val t = token ?: return null
        return try {
            httpClient.get("$baseUrl/api/v1/users/me") {
                bearerAuth(t)
            }.body()
        } catch (_: Exception) {
            null
        }
    }

    suspend fun logout() {
        token = null
    }
}
