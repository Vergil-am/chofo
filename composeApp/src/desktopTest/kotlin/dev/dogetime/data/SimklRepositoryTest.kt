package dev.dogetime.data

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import dev.dogetime.data.local.AppDatabase
import dev.dogetime.data.local.PreferenceEntity
import dev.dogetime.domain.MediaItem
import dev.dogetime.domain.MediaType
import dev.dogetime.domain.WatchTarget
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SimklRepositoryTest {
    private val db = Room.inMemoryDatabaseBuilder<AppDatabase>()
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** "action body" for every scrobble the fake server received. */
    private val scrobbles = CopyOnWriteArrayList<String>()

    /** Bodies of every `/sync/add-to-list` call. */
    private val listAdds = CopyOnWriteArrayList<String>()
    private var respondStatus = HttpStatusCode.Created
    private var respondBody = """{"action":"start"}"""
    private var respondHeaders = headersOf("X-RateLimit-Remaining", "321")

    private val client = HttpClient(MockEngine) {
        engine {
            addHandler { request ->
                val path = request.url.encodedPath
                if (path.startsWith("/scrobble/")) {
                    scrobbles += path.removePrefix("/scrobble/") + " " + (request.body as TextContent).text
                }
                if (path == "/sync/add-to-list") listAdds += (request.body as TextContent).text
                respond(respondBody, respondStatus, respondHeaders)
            }
        }
    }

    private val movie = MediaItem(550, MediaType.Movie, "Fight Club", "", null, null, 8.4, "1999")
    private val movieTarget = WatchTarget.Movie(id = 550, imdbId = "tt0137523")

    private fun repo(): SimklRepository = SimklRepository(
        api = SimklApi(client, clientId = "test-client"),
        prefs = db.preferenceDao(),
        appScope = appScope,
        settleMs = SETTLE,
        minWriteSpacingMs = 0,
    )

    private suspend fun signIn() {
        db.preferenceDao().upsert(
            PreferenceEntity(
                "simkl_session",
                """{"accessToken":"at","refreshToken":"rt","expiresAtMs":${Long.MAX_VALUE / 2},"username":"neo","accountType":"free"}""",
            ),
        )
    }

    @AfterTest
    fun tearDown() {
        appScope.cancel()
        db.close()
    }

    @Test
    fun closingWithinTheSettleTimeSendsNothing() = runBlocking {
        signIn()
        val session = repo().newSession(movieTarget, movie)!!
        session.onPlayingChanged(true, 1.0)
        session.onFinished(3.0)
        delay(SETTLE * 3)
        assertTrue(scrobbles.isEmpty(), "sent: $scrobbles")
    }

    @Test
    fun quickPauseAndResumeSendsOneStartThenStop() = runBlocking {
        signIn()
        val session = repo().newSession(movieTarget, movie)!!
        session.onPlayingChanged(true, 10.0)
        session.onPlayingChanged(false, 11.0)
        session.onPlayingChanged(true, 11.0)
        repeat(5) { session.onProgress(20.0 + it) } // progress ticks don't send or delay anything
        awaitScrobbles(1)
        session.onFinished(95.0)
        awaitScrobbles(2)
        assertEquals(listOf("start", "stop"), scrobbles.map { it.substringBefore(' ') })
        assertTrue(scrobbles[0].contains("\"progress\":24.0"), scrobbles[0])
        assertTrue(scrobbles[1].contains("\"progress\":95.0"), scrobbles[1])
    }

    @Test
    fun repeatedPlayEventsAreSentOnce() = runBlocking {
        signIn()
        val session = repo().newSession(movieTarget, movie)!!
        session.onPlayingChanged(true, 10.0)
        awaitScrobbles(1)
        session.onPlayingChanged(true, 12.0)
        delay(SETTLE * 3)
        assertEquals(1, scrobbles.size)
    }

    @Test
    fun anItemSimklCantMatchIsNotSentAgain() = runBlocking {
        signIn()
        respondStatus = HttpStatusCode.NotFound
        respondBody = """{"error":"id_err","code":404}"""
        val repo = repo()
        repo.newSession(movieTarget, movie)!!.onFinished(90.0)
        awaitScrobbles(1)
        repo.newSession(movieTarget, movie)!!.onFinished(90.0)
        delay(SETTLE * 3)
        assertEquals(1, scrobbles.size)
    }

    @Test
    fun anExhaustedDailyQuotaStopsSending() = runBlocking {
        signIn()
        respondStatus = HttpStatusCode.TooManyRequests
        respondBody = """{"error":"user_limit_exceeded","code":429}"""
        respondHeaders = headersOf("Retry-After", "3600")
        val repo = repo()
        repo.newSession(movieTarget, movie)!!.onFinished(90.0)
        awaitScrobbles(1)
        repo.newSession(movieTarget, movie)!!.onFinished(90.0)
        delay(SETTLE * 3)
        assertEquals(1, scrobbles.size)
        assertEquals(0, repo.account.first { it?.requestsLeftToday != null }?.requestsLeftToday)
    }

    @Test
    fun quotaHeadersAreShownOnTheAccount() = runBlocking {
        signIn()
        val repo = repo()
        repo.newSession(movieTarget, movie)!!.onFinished(90.0)
        val account = withTimeout(5_000) { repo.account.first { it?.requestsLeftToday != null } }
        assertEquals("neo", account?.username)
        assertEquals(321, account?.requestsLeftToday)
    }

    @Test
    fun nothingIsSentWhenSignedOut() = runBlocking {
        repo().newSession(movieTarget, movie)!!.onFinished(90.0)
        delay(SETTLE * 3)
        assertTrue(scrobbles.isEmpty())
    }

    @Test
    fun animeUsesItsKitsuIdAndPlainEpisodeNumber() {
        val anime = MediaItem(7442, MediaType.Anime, "Attack on Titan", "", null, null, 8.0, "2013")
        val body = scrobbleBody(WatchTarget.Episode(showId = 7442, season = 1, episode = 5, kitsuId = 7442), anime, 50.0)!!
        assertEquals(7442, body.anime?.ids?.kitsu)
        assertNull(body.anime?.ids?.tmdb)
        assertEquals(5, body.episode?.number)
        assertNull(body.episode?.season)
        assertNull(body.show)
    }

    @Test
    fun tvEpisodesUseTmdbShowIdWithSeason() {
        val show = MediaItem(1399, MediaType.Tv, "Game of Thrones", "", null, null, 8.4, "2011")
        val body = scrobbleBody(WatchTarget.Episode(showId = 1399, season = 2, episode = 3), show, 50.0)!!
        assertEquals(1399, body.show?.ids?.tmdb)
        assertEquals(2, body.episode?.season)
        assertEquals(3, body.episode?.number)
        assertFalse(body.anime != null || body.movie != null)
    }

    private val show = MediaItem(1399, MediaType.Tv, "Game of Thrones", "", null, null, 8.4, "2011")

    @Test
    fun aStartedEpisodePutsItsShowOnWatchingOnce() = runBlocking {
        signIn()
        val repo = repo()
        repo.newSession(WatchTarget.Episode(showId = 1399, season = 1, episode = 1), show)!!
            .onPlayingChanged(true, 1.0)
        withTimeout(5_000) { while (listAdds.isEmpty()) delay(20) }
        assertTrue(listAdds[0].contains("\"shows\":[{\"to\":\"watching\""), listAdds[0])
        assertTrue(listAdds[0].contains("\"tmdb\":1399"), listAdds[0])

        repo.newSession(WatchTarget.Episode(showId = 1399, season = 1, episode = 2), show)!!
            .onPlayingChanged(true, 1.0)
        awaitScrobbles(2)
        delay(SETTLE * 3)
        assertEquals(1, listAdds.size)
    }

    @Test
    fun moviesAreNotPutOnWatching() = runBlocking {
        signIn()
        repo().newSession(movieTarget, movie)!!.onPlayingChanged(true, 1.0)
        awaitScrobbles(1)
        delay(SETTLE * 3)
        assertTrue(listAdds.isEmpty())
    }

    @Test
    fun animeGoesUnderTheAnimeKey() {
        val anime = MediaItem(7442, MediaType.Anime, "Attack on Titan", "", null, null, 8.0, "2013")
        val body = watchingListBody(scrobbleBody(WatchTarget.Episode(showId = 7442, season = 1, episode = 5, kitsuId = 7442), anime, 0.0)!!)!!
        assertEquals("watching", body.anime?.single()?.to)
        assertEquals(7442, body.anime?.single()?.ids?.kitsu)
        assertNull(body.shows)
    }

    private suspend fun awaitScrobbles(count: Int) = withTimeout(5_000) {
        while (scrobbles.size < count) delay(20)
    }

    private companion object {
        const val SETTLE = 150L
    }
}
