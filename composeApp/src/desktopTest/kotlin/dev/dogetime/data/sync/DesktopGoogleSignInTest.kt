package dev.dogetime.data.sync

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import dev.dogetime.data.local.AppDatabase
import io.ktor.client.HttpClient
import io.ktor.client.request.request
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.Url
import io.ktor.http.contentType
import io.ktor.http.setCookie
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DesktopGoogleSignInTest {
    private val browser = HttpClient { followRedirects = false }

    /** The start URL the app opened, split into what the API would send back to. */
    private class Opened(url: String) {
        private val parsed = Url(url)
        val port = parsed.parameters["port"]!!.toInt()
        val state = parsed.parameters["state"]!!
        fun callback(vararg params: Pair<String, String>) =
            "http://127.0.0.1:$port/callback?" + params.joinToString("&") { (k, v) -> "$k=$v" }
    }

    private fun signInWith(timeoutMs: Long = 10_000, onOpen: suspend (Opened) -> Unit): suspend () -> GoogleCredential {
        val scope = CoroutineScope(Dispatchers.IO)
        val signIn = DesktopGoogleSignIn(ChofoApi("https://api.example"), { url -> scope.launch { onOpen(Opened(url)) } }, timeoutMs)
        return { signIn.signIn() }
    }

    @Test
    fun theTokenComesBackWhenTheStateMatches() = runBlocking {
        val signIn = signInWith { opened ->
            // Someone else's link: refused, and the app keeps waiting.
            assertEquals(400, browser.get(opened.callback("state" to "wrong", "token" to "evil")).status.value)
            assertTrue(browser.get(opened.callback("state" to opened.state, "token" to "ott123")).bodyAsText().contains("signed in"))
        }
        assertEquals(GoogleCredential.OneTimeToken("ott123"), signIn())
    }

    @Test
    fun anErrorFromTheApiIsReported() = runBlocking {
        val signIn = signInWith { opened -> browser.get(opened.callback("state" to opened.state, "error" to "google_failed")) }
        assertFailsWith<GoogleSignInException> { signIn() }
        Unit
    }

    @Test
    fun itGivesUpAfterTheTimeout() = runBlocking {
        val e = assertFailsWith<GoogleSignInException> { signInWith(timeoutMs = 300, onOpen = {})() }
        assertTrue(e.message!!.contains("timed out"))
    }

    @Test
    fun theStartUrlPointsAtTheApi() {
        val url = ChofoApi("https://chofo-api.vercel.app/").desktopGoogleStartUrl(51234, "abc")
        assertEquals("https://chofo-api.vercel.app/v1/desktop-auth/start?port=51234&state=abc", url)
    }

    /**
     * The whole hand-off against a real API (CHOFO_API_TEST_URL), with the Google step replaced:
     * the "browser" gets its session from a username sign-in, then follows the API's finish
     * redirect back to the app, which trades the one-time token and is signed in.
     */
    @Test
    fun theHandOffSignsTheAppInAgainstARealApi(): Unit = runBlocking {
        val base = System.getenv("CHOFO_API_TEST_URL").orEmpty()
        if (base.isBlank()) return@runBlocking println("CHOFO_API_TEST_URL is not set; skipping.")
        val db = Room.inMemoryDatabaseBuilder<AppDatabase>().setDriver(BundledSQLiteDriver()).setQueryCoroutineContext(Dispatchers.IO).build()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val api = ChofoApi(base)
            val accounts = AccountRepository(api, db.preferenceDao(), scope)
            accounts.awaitLoaded()
            val username = "goog_${Random.nextInt(1_000_000)}"
            val signIn = DesktopGoogleSignIn(api, { startUrl ->
                scope.launch {
                    val web = HttpClient { followRedirects = false }
                    web.post("$base/v1/auth/sign-up/email") {
                        contentType(ContentType.Application.Json)
                        setBody("""{"email":"$username@chofo.test","password":"password123","name":"G","username":"$username"}""")
                    }
                    val cookie = web.post("$base/v1/auth/sign-in/username") {
                        contentType(ContentType.Application.Json)
                        setBody("""{"username":"$username","password":"password123"}""")
                    }.setCookie().joinToString("; ") { "${it.name}=${it.value}" }
                    val finish = web.get(startUrl.replace("/start?", "/finish?")) { header(HttpHeaders.Cookie, cookie) }
                    val back = finish.headers[HttpHeaders.Location]!!
                    assertTrue(back.startsWith("http://127.0.0.1:"), back)
                    web.get(back)
                }
            })
            accounts.signInWithGoogle(signIn.signIn())
            assertEquals(username, accounts.account.value?.username)
            // The session works for the sync API, and the account can go.
            api.progress(accounts.account.value!!.token, since = 0)
            HttpClient().use { it.request("$base/v1/me") { method = io.ktor.http.HttpMethod.Delete; header(HttpHeaders.Authorization, "Bearer ${accounts.account.value!!.token}") } }
        } finally {
            scope.coroutineContext.job.cancelAndJoin()
            delay(200)
            db.close()
        }
    }
}
