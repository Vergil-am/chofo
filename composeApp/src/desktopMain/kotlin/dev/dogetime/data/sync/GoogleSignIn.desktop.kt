package dev.dogetime.data.sync

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import java.security.SecureRandom
import java.util.Base64

/** The browser flow: see [DesktopGoogleSignIn]. */
@Composable
actual fun rememberGoogleSignIn(api: ChofoApi): GoogleSignIn? {
    val uriHandler = LocalUriHandler.current
    return remember(api, uriHandler) { if (api.isConfigured) DesktopGoogleSignIn(api, uriHandler::openUri) else null }
}

/**
 * Google sign-in in the browser, handed back to the app:
 *
 * 1. Listens on `http://127.0.0.1:<random port>/callback`.
 * 2. Opens the API's `/v1/desktop-auth/start?port=…&state=…` in the browser.
 * 3. After Google, the API redirects the browser to the callback with a one-time token and the
 *    same `state` (anything else is refused), which is traded for a session.
 *
 * Gives up after [timeoutMs], or when the calling coroutine is cancelled.
 */
class DesktopGoogleSignIn(
    private val api: ChofoApi,
    private val openBrowser: (String) -> Unit,
    private val timeoutMs: Long = 5 * 60_000,
) : GoogleSignIn {
    override suspend fun signIn(): GoogleCredential {
        val state = randomState()
        val result = CompletableDeferred<GoogleCredential>()
        val server = embeddedServer(CIO, host = "127.0.0.1", port = 0) {
            routing {
                get("/callback") {
                    val params = call.request.queryParameters
                    if (params["state"] != state) {
                        call.respondText(page("This sign-in link doesn't belong to this window. Start again from Chofo."), ContentType.Text.Html, HttpStatusCode.BadRequest)
                        return@get
                    }
                    val token = params["token"]
                    val error = params["error"]
                    if (token != null) {
                        call.respondText(page("You're signed in. You can close this tab and go back to Chofo."), ContentType.Text.Html)
                        result.complete(GoogleCredential.OneTimeToken(token))
                    } else {
                        call.respondText(page("Google sign-in didn't finish. Go back to Chofo and try again."), ContentType.Text.Html)
                        result.completeExceptionally(GoogleSignInException(errorMessage(error)))
                    }
                }
            }
        }
        server.startSuspend(wait = false)
        try {
            val port = server.engine.resolvedConnectors().first().port
            openBrowser(api.desktopGoogleStartUrl(port, state))
            return withTimeout(timeoutMs) { result.await() }
        } catch (_: TimeoutCancellationException) {
            throw GoogleSignInException("Google sign-in timed out. Try again.")
        } finally {
            server.stop(gracePeriodMillis = 500, timeoutMillis = 1_000)
        }
    }

    private fun errorMessage(code: String?) = when (code) {
        "google_unavailable" -> "Google sign-in isn't set up on the server."
        "no_session" -> "Google sign-in didn't create a session. Try again."
        else -> "Google sign-in didn't finish. Try again."
    }

    private fun randomState(): String {
        val bytes = ByteArray(24).also { SecureRandom().nextBytes(it) }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun page(message: String) = """
        <!doctype html><html><head><meta charset="utf-8"><title>Chofo</title>
        <style>body{font-family:system-ui,sans-serif;display:grid;place-items:center;height:100vh;margin:0;background:#111;color:#eee}</style>
        </head><body><p>$message</p></body></html>
    """.trimIndent()
}
