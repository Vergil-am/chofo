package dev.dogetime.data

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import dev.dogetime.data.local.AppDatabase
import dev.dogetime.domain.MediaItem
import dev.dogetime.domain.MediaType
import dev.dogetime.domain.WatchStatus
import dev.dogetime.domain.WatchTarget
import dev.dogetime.domain.isFinished
import dev.dogetime.ui.home.HomeViewModel
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A show with two seasons out, a third announced: S1 has episodes 1–3, S2 has 1–2 out and
 * episode 3 airing next year, S3 has no aired episodes yet.
 */
private class FakeTmdb {
    val requests = AtomicInteger()
    var offline = false

    val client = HttpClient(MockEngine) {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        engine {
            addHandler { request ->
                requests.incrementAndGet()
                if (offline) throw java.io.IOException("offline")
                val json = headersOf("Content-Type", "application/json")
                val path = request.url.encodedPath
                fun eps(season: Int, vararg dates: String?) = dates.mapIndexed { i, d ->
                    """{"id":${season * 100 + i},"episode_number":${i + 1},"season_number":$season,"air_date":${d?.let { "\"$it\"" } ?: "null"}}"""
                }.joinToString(",", """{"episodes":[""", "]}")
                when {
                    path.endsWith("/tv/1399/season/1") -> respond(eps(1, "2011-04-17", "2011-04-24", "2011-05-01"), HttpStatusCode.OK, json)
                    path.endsWith("/tv/1399/season/2") -> respond(eps(2, "2012-04-01", "2012-04-08", "2999-01-01"), HttpStatusCode.OK, json)
                    path.endsWith("/tv/1399/season/3") -> respond(eps(3, null, "2999-02-01"), HttpStatusCode.OK, json)
                    path.endsWith("/tv/1399") -> respond(
                        """{"id":1399,"name":"Game of Thrones","seasons":[
                            {"id":0,"season_number":0,"episode_count":4},{"id":1,"season_number":1,"episode_count":3},
                            {"id":2,"season_number":2,"episode_count":3},{"id":3,"season_number":3,"episode_count":2}]}""",
                        HttpStatusCode.OK, json,
                    )
                    else -> respond("{}", HttpStatusCode.NotFound, json)
                }
            }
        }
    }
    val media = MediaRepository(TmdbApi(client), KitsuApi(client))
}

class NextEpisodeFinderTest {
    private val tmdb = FakeTmdb()
    private val finder = NextEpisodeFinder(tmdb.media, today = { "2026-11-26" })

    @Test
    fun theNextAiredEpisodeIsFound() = runBlocking {
        assertEquals(NextEpisode.Available(EpisodeNumber(1, 2)), finder.after(MediaType.Tv, 1399, 1, 1))
        // End of a season: the first episode of the next one.
        assertEquals(NextEpisode.Available(EpisodeNumber(2, 1)), finder.after(MediaType.Tv, 1399, 1, 3))
        // Caught up: S2E3 hasn't aired and S3 has nothing out.
        assertEquals(NextEpisode.None, finder.after(MediaType.Tv, 1399, 2, 2))
    }

    @Test
    fun answersAreRememberedAndFailuresAreNot() = runBlocking {
        assertNull(finder.cached(MediaType.Tv, 1399, 1, 1))
        finder.after(MediaType.Tv, 1399, 1, 1)
        val asked = tmdb.requests.get()
        tmdb.offline = true
        assertEquals(NextEpisode.Available(EpisodeNumber(1, 2)), finder.after(MediaType.Tv, 1399, 1, 1))
        assertEquals(asked, tmdb.requests.get())
        val failed = runCatching { finder.after(MediaType.Tv, 1399, 1, 2) }
        assertTrue(failed.isFailure)
        assertNull(finder.cached(MediaType.Tv, 1399, 1, 2))
    }

    @Test
    fun datesAreReadInUtc() {
        assertEquals("1970-01-01", isoDate(0))
        assertEquals("2026-11-26", isoDate(1_795_651_200_000)) // 2026-11-26T00:00Z
        assertEquals("2026-11-26", isoDate(1_795_737_599_999)) // its last millisecond
        assertEquals("2024-02-29", isoDate(1_709_164_800_000))
        assertEquals("1969-12-31", isoDate(-1))
    }

    @Test
    fun ninetyPercentIsFinished() {
        assertFalse(isFinished(3_239_999, 3_600_000))
        assertTrue(isFinished(3_240_000, 3_600_000))
        assertFalse(isFinished(5_000, 0)) // length unknown
    }
}

class ContinueWatchingTest {
    private val tmdb = FakeTmdb()
    private val db = Room.inMemoryDatabaseBuilder<AppDatabase>().setDriver(BundledSQLiteDriver()).setQueryCoroutineContext(Dispatchers.IO).build()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val history = WatchHistoryRepository(db.watchHistoryDao(), scope)
    private val watchlist = WatchlistRepository(db.watchlistDao())
    private val finder = NextEpisodeFinder(tmdb.media, today = { "2026-11-26" })
    private val completion = CompletionTracker(watchlist, finder, scope)
    private val show = MediaItem(1399, MediaType.Tv, "Game of Thrones", "", null, null, 8.5, "2011")
    private val movie = MediaItem(550, MediaType.Movie, "Fight Club", "", null, null, 8.4, "1999")
    private val hour = 3_600_000L

    @AfterTest
    fun tearDown() = runBlocking {
        scope.coroutineContext.job.cancelAndJoin()
        delay(200)
        db.close()
    }

    private suspend fun continueWatching(expect: (List<Triple<Int, Int?, Int?>>) -> Boolean): List<Triple<Int, Int?, Int?>> {
        val home = HomeViewModel(tmdb.media, FavoritesRepository(db.favoriteDao()), history, watchlist = watchlist, nextEpisodes = finder)
        try {
            return withTimeout(5_000) {
                home.watchHistory.first { list -> expect(list.map { Triple(it.mediaItem.id, it.season, it.episode) }) }
            }.map { Triple(it.mediaItem.id, it.season, it.episode) }
        } finally {
            home.clear()
        }
    }

    @Test
    fun aFinishedEpisodeMakesWayForTheNextOne() = runBlocking {
        history.saveProgress(WatchTarget.Episode(1399, 1, 3), show, (hour * 0.95).toLong(), hour)
        history.saveProgress(WatchTarget.Movie(550), movie, hour / 2, 2 * hour)
        val shown = continueWatching { it.any { (id, s, _) -> id == 1399 && s == 2 } }
        assertTrue(Triple(1399, 2, 1) in shown, "$shown")
        assertTrue(Triple(550, null, null) in shown) // half-watched movie stays
        val next = history.observeHistory().first().first { it.mediaItem.id == 1399 }
        assertEquals(3, next.episode) // the saved history itself is unchanged
    }

    @Test
    fun finishedMoviesAndCaughtUpShowsLeaveAndAreCompleted() = runBlocking {
        val caughtUp = WatchTarget.Episode(1399, 2, 2)
        history.saveProgress(caughtUp, show, (hour * 0.91).toLong(), hour)
        completion.onProgress(caughtUp, show, (hour * 0.91).toLong(), hour)
        history.saveProgress(WatchTarget.Movie(550), movie, (2 * hour * 0.9).toLong(), 2 * hour)
        completion.onProgress(WatchTarget.Movie(550), movie, (2 * hour * 0.9).toLong(), 2 * hour)

        assertEquals(emptyList(), continueWatching { it.isEmpty() })
        withTimeout(5_000) {
            watchlist.observeAll().first { entries -> entries.size == 2 && entries.all { it.status == WatchStatus.Completed } }
        }
        Unit
    }

    @Test
    fun aShowWithMoreToWatchIsNotCompleted() = runBlocking {
        completion.onProgress(WatchTarget.Episode(1399, 1, 1), show, hour, hour)
        delay(500)
        assertNull(watchlist.observeStatus(MediaType.Tv, 1399).first())
        // Below 90%, nothing at all.
        completion.onProgress(WatchTarget.Movie(550), movie, hour, 2 * hour)
        delay(300)
        assertNull(watchlist.observeStatus(MediaType.Movie, 550).first())
    }

    @Test
    fun aNewEpisodeMovesACompletedShowBackToWatching() = runBlocking {
        watchlist.set(show, WatchStatus.Completed)
        watchlist.set(movie, WatchStatus.Completed)
        watchlist.startedWatching(show, isEpisode = true)
        watchlist.startedWatching(movie, isEpisode = false) // rewatching a movie: still completed
        assertEquals(WatchStatus.Watching, watchlist.observeStatus(MediaType.Tv, 1399).first())
        assertEquals(WatchStatus.Completed, watchlist.observeStatus(MediaType.Movie, 550).first())
    }
}
