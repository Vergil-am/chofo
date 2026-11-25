package dev.dogetime.data.sync

import dev.dogetime.AppConfig
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** The signed-in user, as the Chofo API returns it. */
@Serializable
data class ApiUser(
    val id: String,
    val email: String = "",
    val username: String? = null,
    val name: String = "",
)

@Serializable
data class AuthResponse(val token: String, val user: ApiUser)

@Serializable
private data class SessionToken(val token: String)

/** `POST /auth/one-time-token/verify` answers with the session itself. */
@Serializable
private data class SessionResponse(val session: SessionToken, val user: ApiUser)

@Serializable
private data class IdToken(val token: String)

@Serializable
private data class SocialIdTokenBody(val provider: String, val idToken: IdToken)

@Serializable
private data class TokenBody(val token: String)

/** What a device needs to show a title without looking it up. */
@Serializable
data class ApiCard(
    val title: String? = null,
    val overview: String? = null,
    @SerialName("poster_url") val posterUrl: String? = null,
    @SerialName("backdrop_url") val backdropUrl: String? = null,
    val rating: Double? = null,
    val year: String? = null,
)

/** One movie (season and episode null) or episode. Times are epoch milliseconds. */
@Serializable
data class ApiProgressWrite(
    val type: String,
    val id: Int,
    val season: Int?,
    val episode: Int?,
    @SerialName("position_ms") val positionMs: Long,
    @SerialName("duration_ms") val durationMs: Long,
    @SerialName("watched_at") val watchedAt: Long,
    val card: ApiCard? = null,
)

@Serializable
data class ApiProgressRow(
    val type: String,
    val id: Int,
    val season: Int? = null,
    val episode: Int? = null,
    @SerialName("position_ms") val positionMs: Long = 0,
    @SerialName("duration_ms") val durationMs: Long = 0,
    @SerialName("watched_at") val watchedAt: Long,
    val card: ApiCard? = null,
    val finished: Boolean = false,
    val deleted: Boolean = false,
    val rev: Long,
)

@Serializable
data class ApiPage<T>(
    val items: List<T>,
    @SerialName("next_cursor") val nextCursor: Long,
    @SerialName("has_more") val hasMore: Boolean,
)

@Serializable
private data class BatchBody(val items: List<ApiProgressWrite>)

@Serializable
private data class BatchResult(val items: List<ApiProgressRow>)

/** Both error shapes: this API's `{ error, message }` and Better Auth's `{ code, message }`. */
@Serializable
private data class ApiErrorBody(val error: String? = null, val code: String? = null, val message: String? = null)

/** A request the API answered with an error. [code] is the machine-readable one, when sent. */
class ChofoApiException(val status: Int, val code: String?, message: String) : Exception(message)

/**
 * Client for the Chofo API (github.com/Vergil-am/chofo-api): accounts and watch-progress sync.
 * See its `docs/openapi.yaml`. Every call throws [ChofoApiException] on an error response and
 * the usual IO exceptions when offline.
 */
class ChofoApi(
    private val baseUrl: String,
    private val client: HttpClient = createChofoHttpClient(),
) {
    val isConfigured: Boolean get() = baseUrl.isNotBlank()

    suspend fun signUp(username: String, email: String, password: String): AuthResponse =
        call(HttpMethod.Post, "auth/sign-up/email") {
            jsonBody(mapOf("username" to username, "email" to email, "password" to password, "name" to username))
        }

    suspend fun signIn(username: String, password: String): AuthResponse =
        call(HttpMethod.Post, "auth/sign-in/username") {
            jsonBody(mapOf("username" to username, "password" to password))
        }

    /** Signs in with a Google ID token from the device's account picker (Android). */
    suspend fun signInWithGoogleIdToken(idToken: String): AuthResponse =
        call(HttpMethod.Post, "auth/sign-in/social") {
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(SocialIdTokenBody("google", IdToken(idToken))))
        }

    /** Trades the one-time token from the desktop browser sign-in for a session. */
    suspend fun verifyOneTimeToken(token: String): AuthResponse {
        val response = call<SessionResponse>(HttpMethod.Post, "auth/one-time-token/verify") {
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(TokenBody(token)))
        }
        return AuthResponse(response.session.token, response.user)
    }

    /** Where the desktop app opens the browser for Google sign-in (see chofo-api docs/BEHAVIOR.md). */
    fun desktopGoogleStartUrl(port: Int, state: String): String =
        "${baseUrl.trimEnd('/')}/v1/desktop-auth/start?port=$port&state=$state"

    /** Ends the session on the server. */
    suspend fun signOut(token: String) {
        call<Unit>(HttpMethod.Post, "auth/sign-out") {
            bearerAuth(token)
            jsonBody(emptyMap())
        }
    }

    suspend fun me(token: String): ApiUser = call(HttpMethod.Get, "me") { bearerAuth(token) }

    suspend fun progress(token: String, since: Long, limit: Int = 500): ApiPage<ApiProgressRow> =
        call(HttpMethod.Get, "progress") {
            bearerAuth(token)
            parameter("since", since)
            parameter("limit", limit)
        }

    /** Saves up to 500 rows; returns them as the server now stores them, in the same order. */
    suspend fun putProgress(token: String, items: List<ApiProgressWrite>): List<ApiProgressRow> =
        call<BatchResult>(HttpMethod.Post, "progress/batch") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(BatchBody(items)))
        }.items

    /** Removes one episode ([season] and [episode] set) or every row of a title. */
    suspend fun deleteProgress(token: String, type: String, id: Int, season: Int?, episode: Int?, deletedAt: Long) {
        val path = if (season != null && episode != null) "progress/$type/$id/$season/$episode" else "progress/$type/$id"
        call<Unit>(HttpMethod.Delete, path) {
            bearerAuth(token)
            parameter("deleted_at", deletedAt)
        }
    }

    private suspend inline fun <reified T> call(
        method: HttpMethod,
        path: String,
        crossinline block: HttpRequestBuilder.() -> Unit,
    ): T {
        val response: HttpResponse = client.request("${baseUrl.trimEnd('/')}/v1/$path") {
            this.method = method
            header(HttpHeaders.UserAgent, "chofo/${AppConfig.APP_VERSION}")
            block()
        }
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) {
            val error = runCatching { json.decodeFromString<ApiErrorBody>(text) }.getOrNull()
            throw ChofoApiException(
                status = response.status.value,
                code = error?.error ?: error?.code,
                message = error?.message ?: "The server answered ${response.status.value}.",
            )
        }
        @Suppress("UNCHECKED_CAST")
        return if (T::class == Unit::class) Unit as T else json.decodeFromString(text)
    }

    private fun HttpRequestBuilder.jsonBody(values: Map<String, String>) {
        contentType(ContentType.Application.Json)
        setBody(json.encodeToString(values))
    }

    companion object {
        val json = Json {
            ignoreUnknownKeys = true
            explicitNulls = true
        }
    }
}

/** Separate from the shared client: that one throws on any error status, and this needs the body. */
fun createChofoHttpClient(): HttpClient = HttpClient {
    expectSuccess = false
    install(HttpTimeout) {
        connectTimeoutMillis = 15_000
        socketTimeoutMillis = 30_000
    }
    install(ContentNegotiation) { json(ChofoApi.json) }
}
