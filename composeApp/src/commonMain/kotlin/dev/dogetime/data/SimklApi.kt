package dev.dogetime.data

import dev.dogetime.AppConfig
import dev.dogetime.Secrets
import io.ktor.client.HttpClient
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.Parameters
import io.ktor.http.content.TextContent
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json

/**
 * One Simkl response. [body] is set on success; [error] is Simkl's error code (e.g. `id_err`,
 * `authorization_pending`). [remaining] and [retryAfterSeconds] come from the rate-limit headers,
 * which Simkl sends on user-authenticated calls.
 */
class SimklResponse<T>(
    val status: Int,
    val body: T?,
    val error: String?,
    val remaining: Int?,
    val limit: Int?,
    val retryAfterSeconds: Long?,
) {
    val isSuccess: Boolean get() = status in 200..299
}

/**
 * Thin wrapper over the Simkl API (AUTH V2). Returns every response instead of throwing on
 * HTTP errors, since the caller needs the status and quota headers either way. Network failures
 * still throw.
 */
class SimklApi(
    private val client: HttpClient,
    private val clientId: String = Secrets.SIMKL_CLIENT_ID,
) {
    /** False when the build has no Simkl client id, in which case Simkl sync is hidden. */
    val isConfigured: Boolean get() = clientId.isNotBlank()

    suspend fun requestDeviceCode(): SimklResponse<SimklDeviceCode> =
        send(HttpMethod.Post, "oauth2/device", appParams = false) {
            setBody(form("client_id" to clientId, "scope" to "media:read media:write"))
        }

    suspend fun pollDeviceToken(deviceCode: String): SimklResponse<SimklToken> =
        send(HttpMethod.Post, "oauth2/token", appParams = false) {
            setBody(
                form(
                    "grant_type" to "urn:ietf:params:oauth:grant-type:device_code",
                    "client_id" to clientId,
                    "device_code" to deviceCode,
                ),
            )
        }

    suspend fun refreshToken(refreshToken: String): SimklResponse<SimklToken> =
        send(HttpMethod.Post, "oauth2/token", appParams = false) {
            setBody(
                form(
                    "grant_type" to "refresh_token",
                    "client_id" to clientId,
                    "refresh_token" to refreshToken,
                ),
            )
        }

    /** Ends this sign-in on Simkl's side. Simkl answers 200 even for unknown tokens. */
    suspend fun revoke(token: String): SimklResponse<Unit> =
        send(HttpMethod.Post, "oauth2/revoke", appParams = false) {
            setBody(form("client_id" to clientId, "token" to token))
        }

    suspend fun userSettings(accessToken: String): SimklResponse<SimklUserSettings> =
        send(HttpMethod.Get, "users/settings") { bearerAuth(accessToken) }

    suspend fun activities(accessToken: String): SimklResponse<SimklActivities> =
        send(HttpMethod.Get, "sync/activities") { bearerAuth(accessToken) }

    /** Every paused session the account still keeps (7 days on free, 30 on PRO, 90 on VIP). */
    suspend fun playback(accessToken: String): SimklResponse<List<SimklPlaybackItem>> =
        send(HttpMethod.Get, "sync/playback") { bearerAuth(accessToken) }

    /** One list of the user's [type] (`shows` or `anime`), e.g. [status] `watching`. */
    suspend fun allItems(accessToken: String, type: String, status: String): SimklResponse<SimklAllItems> =
        send(HttpMethod.Get, "sync/all-items/$type/$status") { bearerAuth(accessToken) }

    suspend fun scrobble(
        action: ScrobbleAction,
        accessToken: String,
        body: SimklScrobbleBody,
    ): SimklResponse<SimklScrobbleResult> =
        send(HttpMethod.Post, "scrobble/${action.path}") {
            bearerAuth(accessToken)
            setBody(TextContent(json.encodeToString(body), ContentType.Application.Json))
        }

    /** Always 201, even when nothing matched; the body lists what was added and what wasn't found. */
    suspend fun addToList(accessToken: String, body: SimklAddToListBody): SimklResponse<Unit> =
        send(HttpMethod.Post, "sync/add-to-list") {
            bearerAuth(accessToken)
            setBody(TextContent(json.encodeToString(body), ContentType.Application.Json))
        }

    private suspend inline fun <reified T> send(
        method: HttpMethod,
        path: String,
        appParams: Boolean = true,
        crossinline block: HttpRequestBuilder.() -> Unit,
    ): SimklResponse<T> {
        val response = client.request("$BASE_URL/$path") {
            this.method = method
            expectSuccess = false
            header(HttpHeaders.UserAgent, USER_AGENT)
            if (appParams) {
                parameter("client_id", clientId)
                parameter("app-name", APP_NAME)
                parameter("app-version", AppConfig.APP_VERSION)
            }
            block()
        }
        val text = response.bodyAsText()
        val ok = response.status.isSuccess()
        val body: T? = if (ok && T::class != Unit::class) {
            runCatching { json.decodeFromString<T>(text) }.getOrNull()
        } else {
            null
        }
        val error = if (ok) null else runCatching { json.decodeFromString<SimklError>(text).error }.getOrNull()
        return SimklResponse(
            status = response.status.value,
            body = body,
            error = error,
            remaining = response.headers["X-RateLimit-Remaining"]?.trim()?.toIntOrNull(),
            limit = response.headers["X-RateLimit-Limit"]?.trim()?.toIntOrNull(),
            retryAfterSeconds = response.headers[HttpHeaders.RetryAfter]?.trim()?.toLongOrNull(),
        )
    }

    private fun form(vararg values: Pair<String, String>) =
        FormDataContent(Parameters.build { values.forEach { (k, v) -> append(k, v) } })

    private companion object {
        const val BASE_URL = "https://api.simkl.com"
        const val APP_NAME = "chofo"
        val USER_AGENT = "$APP_NAME/${AppConfig.APP_VERSION}"
        val json = Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }
    }
}

enum class ScrobbleAction(val path: String) {
    Start("start"),
    Pause("pause"),
    Stop("stop"),
}
