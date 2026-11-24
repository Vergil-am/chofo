package dev.dogetime.data

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import dev.dogetime.data.local.AppDatabase
import dev.dogetime.data.local.PreferenceEntity
import dev.dogetime.domain.MediaItem
import dev.dogetime.domain.MediaType
import dev.dogetime.domain.WatchTarget
import dev.dogetime.ui.player.savedPositionFor
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class SimklPlaybackImporterTest {
    private val db = Room.inMemoryDatabaseBuilder<AppDatabase>()
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val requests = CopyOnWriteArrayList<String>()
    private var stamp = "2026-11-20T10:00:00Z"
    private var showsStamp = "2026-11-20T10:00:00Z"
    private var showsJson = """{"shows":[]}"""
    private var animeJson = "null"

    private val playbackJson = """
        [
          {"id": 1, "progress": 40, "paused_at": "2026-11-20T10:00:00Z", "type": "episode",
           "episode": {"season": 2, "number": 4, "title": "x"},
           "show": {"title": "Game of Thrones", "year": 2011, "ids": {"simkl": 17465, "tmdb": "1399", "imdb": "tt0944947"}}},
          {"id": 2, "progress": 63, "paused_at": "2026-11-20T09:00:00Z", "type": "episode",
           "episode": {"season": 1, "number": 7, "tvdb_season": 1, "tvdb_number": 7},
           "anime": {"title": "Frieren", "anime_type": "tv", "ids": {"simkl": 1, "kitsu": "46474", "mal": "52991"}}},
          {"id": 3, "progress": 50, "paused_at": "2026-11-01T10:00:00Z", "type": "movie",
           "movie": {"title": "Fight Club", "year": 1999, "ids": {"tmdb": 550}}},
          {"id": 4, "progress": 30, "paused_at": "2026-11-20T10:00:00Z", "type": "movie",
           "movie": {"title": "No tmdb id", "ids": {"simkl": 99}}}
        ]
    """.trimIndent()

    private val client = HttpClient(MockEngine) {
        engine {
            addHandler { request ->
                val path = request.url.encodedPath
                requests += path
                val headers = headersOf("Content-Type", "application/json")
                when (path) {
                    "/sync/activities" -> respond(
                        """{"all":"$stamp","movies":{"playback":"$stamp"},"tv_shows":{"all":"$showsStamp","playback":"$stamp"},"anime":{"all":"$showsStamp","playback":null}}""",
                        HttpStatusCode.OK, headers,
                    )
                    "/sync/playback" -> respond(playbackJson, HttpStatusCode.OK, headers)
                    "/sync/all-items/shows/watching" -> respond(showsJson, HttpStatusCode.OK, headers)
                    "/sync/all-items/anime/watching" -> respond(animeJson, HttpStatusCode.OK, headers)
                    else -> respond("{}", HttpStatusCode.NotFound, headers)
                }
            }
        }
    }

    private val history = WatchHistoryRepository(db.watchHistoryDao(), appScope)
    private val got = MediaItem(1399, MediaType.Tv, "Game of Thrones", "", "poster", null, 8.4, "2011")
    private val frieren = MediaItem(46474, MediaType.Anime, "Frieren", "", "poster", null, 9.0, "2023")
    private val fightClub = MediaItem(550, MediaType.Movie, "Fight Club", "", "poster", null, 8.4, "1999")

    @AfterTest
    fun tearDown() {
        appScope.cancel()
        db.close()
    }

    private fun simkl(now: () -> Long = { System.currentTimeMillis() }) = SimklRepository(
        api = SimklApi(client, clientId = "test-client"),
        prefs = db.preferenceDao(),
        appScope = appScope,
        now = now,
        minWriteSpacingMs = 0,
    )

    private suspend fun seed() {
        db.preferenceDao().upsert(
            PreferenceEntity(
                "simkl_session",
                """{"accessToken":"at","refreshToken":"rt","expiresAtMs":${Long.MAX_VALUE / 2},"username":"neo","accountType":"free"}""",
            ),
        )
        // Cards the importer reuses, so it needn't look titles up. All older than Simkl's sessions,
        // except Fight Club, which was watched here after it was paused on Simkl.
        val old = 1_700_000_000_000L
        history.saveProgress(WatchTarget.Episode(1399, 1, 1), got, 1_000, 3_000_000, watchedAt = old)
        history.saveProgress(WatchTarget.Episode(46474, 1, 1), frieren, 1_000, 1_440_000, watchedAt = old)
        history.saveProgress(WatchTarget.Movie(550), fightClub, 600_000, 8_000_000, watchedAt = 1_795_000_000_000L)
    }

    @Test
    fun sessionsLandInWatchHistory() = runBlocking {
        seed()
        SimklPlaybackImporter(simkl(), history, MediaRepository(TmdbApi(client)), appScope)

        val episode = withTimeout(5_000) {
            var found = history.getHistory(MediaType.Tv, 1399, 2, 4)
            while (found == null) { delay(20); found = history.getHistory(MediaType.Tv, 1399, 2, 4) }
            found
        }
        assertEquals(0.4f, episode.progress, 0.01f)
        assertEquals("poster", episode.mediaItem.posterUrl)

        val anime = withTimeout(5_000) {
            var found = history.getHistory(MediaType.Anime, 46474, 1, 7)
            while (found == null) { delay(20); found = history.getHistory(MediaType.Anime, 46474, 1, 7) }
            found
        }
        assertEquals(0.63f, anime.progress, 0.01f)

        // The local Fight Club entry is newer than Simkl's, so it is kept.
        assertEquals(600_000, history.getHistory(MediaType.Movie, 550)?.positionMs)
    }

    @Test
    fun unchangedActivityCostsOneRequestAndRecentCheckCostsNone() = runBlocking {
        seed()
        var clock = 1_800_000_000_000L
        val repo = simkl { clock }
        val first = repo.changesSinceLastCheck()
        assertEquals(
            listOf("/sync/activities", "/sync/playback", "/sync/all-items/shows/watching", "/sync/all-items/anime/watching"),
            requests.toList(),
        )
        assertEquals(emptyList(), first?.watchingAnime) // Simkl sends `null` for an empty list

        requests.clear()
        assertEquals(null, repo.changesSinceLastCheck()) // checked moments ago
        assertEquals(emptyList(), requests.toList())

        clock += SimklRepository.ACTIVITY_CHECK_INTERVAL_MS
        assertEquals(null, repo.changesSinceLastCheck()) // nothing changed on Simkl
        assertEquals(listOf("/sync/activities"), requests.toList())

        requests.clear()
        stamp = "2026-11-21T08:00:00Z"
        clock += SimklRepository.ACTIVITY_CHECK_INTERVAL_MS
        val changes = repo.changesSinceLastCheck()
        assertNotNull(changes?.playback)
        assertEquals(null, changes.watchingShows)
        assertEquals(listOf("/sync/activities", "/sync/playback"), requests.toList())
    }

    @Test
    fun watchingListBringsInTheNextEpisode() = runBlocking {
        seed()
        showsJson = """{"shows":[
          {"next_to_watch":"S01E03","last_watched_at":"2026-11-22T10:00:00Z","show":{"title":"Game of Thrones","ids":{"tmdb":"1399"}}},
          {"next_to_watch":null,"show":{"title":"Done","ids":{"tmdb":"5"}}}
        ]}"""
        animeJson = """{"anime":[
          {"next_to_watch":"E10","last_watched_at":null,"added_to_watchlist_at":"2026-11-22T09:00:00Z","show":{"title":"Frieren","ids":{"kitsu":"46474"}},"anime_type":"tv"}
        ]}"""
        SimklPlaybackImporter(simkl(), history, MediaRepository(TmdbApi(client)), appScope)

        val next = awaitHistory(MediaType.Tv, 1399, 1, 3)
        assertEquals(0, next.positionMs)
        assertEquals("poster", next.mediaItem.posterUrl)
        assertEquals(awaitHistory(MediaType.Anime, 46474, 1, 10).positionMs, 0)
    }

    @Test
    fun aFinishedLocalEpisodeIsFollowedByTheNextOne() = runBlocking {
        seed()
        // Finished here after Simkl last saw it: the next episode still comes first.
        history.saveProgress(WatchTarget.Episode(1399, 1, 2), got, 2_900_000, 3_000_000, watchedAt = 1_900_000_000_000L)
        showsJson = """{"shows":[{"next_to_watch":"S01E03","last_watched_at":"2026-11-22T10:00:00Z","show":{"ids":{"tmdb":"1399"}}}]}"""
        SimklPlaybackImporter(simkl(), history, MediaRepository(TmdbApi(client)), appScope)

        val next = awaitHistory(MediaType.Tv, 1399, 1, 3)
        assertEquals(1_900_000_000_001L, next.lastWatchedAt)
    }

    @Test
    fun episodeCodesParse() {
        assertEquals(1 to 5, parseEpisodeCode("S01E05"))
        assertEquals(null to 10, parseEpisodeCode("E10"))
        assertEquals(null, parseEpisodeCode("finished"))
    }

    private suspend fun awaitHistory(type: MediaType, id: Int, season: Int, episode: Int) = withTimeout(5_000) {
        var found = history.getHistory(type, id, season, episode)
        while (found == null) { delay(20); found = history.getHistory(type, id, season, episode) }
        found
    }

    @Test
    fun positionsSavedAgainstAnotherLengthAreScaled() {
        // 40% of a 45-minute estimate, played back as a 53-minute stream.
        assertEquals(1_272_000, savedPositionFor(1_080_000, 2_700_000, 3_180_000))
        // Same length (within 10%): kept as is.
        assertEquals(600_000, savedPositionFor(600_000, 3_000_000, 3_100_000))
    }
}
