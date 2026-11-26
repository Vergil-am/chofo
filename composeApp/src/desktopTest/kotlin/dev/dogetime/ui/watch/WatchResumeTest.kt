package dev.dogetime.ui.watch

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import dev.dogetime.core.CapturedStream
import dev.dogetime.core.TorrentDownloadManager
import dev.dogetime.data.KitsuMappingRepository
import dev.dogetime.ui.player.PlaybackSource
import dev.dogetime.ui.player.PlayerViewModel
import dev.dogetime.data.DownloadRepository
import dev.dogetime.data.OpenSubtitlesApi
import dev.dogetime.data.StremioSubtitlesApi
import dev.dogetime.data.SubdlApi
import dev.dogetime.data.SubtitleDownloader
import dev.dogetime.data.TorrentioApi
import dev.dogetime.data.WatchHistoryRepository
import dev.dogetime.data.YtsSubsApi
import dev.dogetime.data.addons.StreamAddonRepository
import dev.dogetime.data.local.AppDatabase
import dev.dogetime.domain.MediaItem
import dev.dogetime.domain.MediaType
import dev.dogetime.domain.RememberedStream
import dev.dogetime.domain.WatchTarget
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
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Continue Watching goes straight to the stream last played, without the source menu. */
class WatchResumeTest {
    private val db = Room.inMemoryDatabaseBuilder<AppDatabase>().setDriver(BundledSQLiteDriver()).setQueryCoroutineContext(Dispatchers.IO).build()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val history = WatchHistoryRepository(db.watchHistoryDao(), scope)
    private val show = MediaItem(247718, MediaType.Tv, "MobLand", "", null, null, 8.1, "2025")
    private val target = WatchTarget.Episode(247718, 1, 3, imdbId = "tt31510819")

    /** The add-on now hands out a new link for the same file. */
    private val client = HttpClient(MockEngine) {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        engine {
            addHandler { request ->
                val json = headersOf("Content-Type", "application/json")
                val url = request.url.toString()
                when {
                    url.endsWith("/manifest.json") -> respond(
                        """{"id":"org.example.debrid","name":"Debrid","resources":["stream"],"types":["series"],"idPrefixes":["tt"]}""",
                        HttpStatusCode.OK, json,
                    )
                    "/stream/" in url -> respond(
                        """{"streams":[{"name":"Debrid 1080p","title":"MobLand S01E03","url":"https://cdn.example/fresh.mkv",
                            "behaviorHints":{"filename":"MobLand.S01E03.mkv"}}]}""",
                        HttpStatusCode.OK, json,
                    )
                    else -> respond("{}", HttpStatusCode.NotFound, json)
                }
            }
        }
    }
    private val addons = StreamAddonRepository(db.preferenceDao(), client)
    private val viewModels = mutableListOf<WatchViewModel>()
    private val subtitles = SubtitleDownloader(OpenSubtitlesApi(client), SubdlApi(client), YtsSubsApi(client), StremioSubtitlesApi(client))
    private val downloads = TorrentDownloadManager(DownloadRepository(db.downloadDao()), subtitles)

    private fun watch(resume: Boolean) = WatchViewModel(
        target = target,
        title = "MobLand S1E3",
        mediaItem = show,
        backdropUrl = null,
        torrentio = TorrentioApi(client),
        addonRepository = addons,
        downloadManager = downloads,
        stremioSubtitles = StremioSubtitlesApi(client),
        openSubtitles = OpenSubtitlesApi(client),
        subdl = SubdlApi(client),
        watchHistory = history,
        resumeLastStream = resume,
    ).also { viewModels += it }

    private suspend fun WatchViewModel.settled(): WatchUiState = withTimeout(5_000) {
        state.first { !it.isCheckingRemembered && !it.isResuming }
    }

    @AfterTest
    fun tearDown() = runBlocking {
        viewModels.forEach { it.clear() }
        scope.coroutineContext.job.cancelAndJoin()
        delay(200)
        db.close()
    }

    @Test
    fun aTorrentStartsStraightAway() = runBlocking {
        val torrent = RememberedStream(RememberedStream.Kind.Torrent, label = "1080p · Torrentio", magnet = "magnet:?xt=urn:btih:abc", fileIndex = 4)
        history.saveProgress(target, show, 600_000, 3_600_000, stream = torrent)

        val step = assertIs<WatchStep.Torrent>(watch(resume = true).settled().step)
        assertEquals("magnet:?xt=urn:btih:abc", step.pick.magnet)
        assertEquals(4, step.pick.fileIndex)
        assertEquals(3, step.pick.episode)
        assertEquals(torrent, step.pick.remembered) // kept, so the next save remembers it again

        // Opened from a title's page instead: the stream list, with the stream offered first.
        val fromDetails = watch(resume = false).settled()
        assertEquals(WatchStep.Torrents, fromDetails.step)
        assertEquals(torrent, fromDetails.remembered)
    }

    @Test
    fun theStreamListOpensWithoutAMenu() = runBlocking {
        addons.install("https://debrid.example/key=1/manifest.json")
        // Nothing watched yet: the first add-on that serves the title.
        val first = watch(resume = false).settled()
        assertEquals(WatchStep.Torrents, first.step)
        assertEquals(addons.addons.first().first { it.canServe(target) }.id, first.provider?.id)

        // Watched before with the debrid add-on: its list opens.
        history.saveProgress(target, show, 600_000, 3_600_000, stream = RememberedStream(RememberedStream.Kind.Direct, addonId = "org.example.debrid", url = "https://cdn.example/a.mkv"))
        assertEquals("org.example.debrid", watch(resume = false).settled().provider?.id)
    }

    @Test
    fun withNoAddOnsItSaysSo() = runBlocking {
        addons.current().forEach { addons.remove(it.id) }
        val state = watch(resume = false).settled()
        assertEquals(WatchStep.SourceMenu, state.step)
        assertTrue(state.noStreamSources)
    }

    @Test
    fun aSavedWebPlayerFallsBackToTheStreamList() = runBlocking {
        // Saved while web players were on: nothing to reopen, so the list shows.
        history.saveProgress(target, show, 600_000, 3_600_000)
        val state = watch(resume = true).settled()
        assertEquals(WatchStep.Torrents, state.step)
    }

    @Test
    fun aDirectLinkIsFetchedAgainFromItsAddOn() = runBlocking {
        addons.install("https://debrid.example/key=1/manifest.json")
        val old = RememberedStream(
            RememberedStream.Kind.Direct, addonId = "org.example.debrid", url = "https://cdn.example/expired.mkv",
            fileName = "MobLand.S01E03.mkv",
        )
        history.saveProgress(target, show, 600_000, 3_600_000, stream = old)
        val step = assertIs<WatchStep.Torrent>(watch(resume = true).settled().step)
        assertEquals("https://cdn.example/fresh.mkv", step.pick.directUrl)

        // Without the add-on here (e.g. installed only on the phone), the saved link is tried.
        addons.remove("org.example.debrid")
        val fallback = assertIs<WatchStep.Torrent>(watch(resume = true).settled().step)
        assertEquals("https://cdn.example/expired.mkv", fallback.pick.directUrl)
    }

    @Test
    fun startingOverIgnoresTheSavedPosition() = runBlocking {
        history.saveProgress(target, show, 600_000, 3_600_000)
        fun player(fromBeginning: Boolean) = PlayerViewModel(
            source = PlaybackSource.Stream(CapturedStream("https://cdn.example/a.mkv", emptyMap()), "MobLand S1E3", target, show),
            watchHistory = history,
            downloadManager = downloads,
            subtitleDownloader = subtitles,
            kitsuMapping = KitsuMappingRepository(client, scope),
            stremioSubtitles = StremioSubtitlesApi(client),
            subdl = SubdlApi(client),
            openSubtitles = OpenSubtitlesApi(client),
            ytsSubs = YtsSubsApi(client),
            startFromBeginning = fromBeginning,
        )
        val resumed = player(fromBeginning = false)
        val restarted = player(fromBeginning = true)
        try {
            assertEquals(600_000, resumed.resumePosition(3_600_000))
            assertEquals(null, restarted.resumePosition(3_600_000))
        } finally {
            resumed.clear()
            restarted.clear()
        }
    }

    @Test
    fun withoutARememberedStreamTheListShows() = runBlocking {
        history.saveProgress(target, show, 600_000, 3_600_000)
        val state = watch(resume = true).settled()
        assertEquals(WatchStep.Torrents, state.step)
        assertEquals(null, state.remembered)
    }
}
