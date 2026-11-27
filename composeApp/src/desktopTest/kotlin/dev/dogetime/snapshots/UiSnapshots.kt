package dev.dogetime.snapshots

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.network.ktor3.KtorNetworkFetcherFactory
import dev.dogetime.AppTheme
import dev.dogetime.ChofoTheme
import dev.dogetime.data.FavoritesRepository
import dev.dogetime.data.KitsuApi
import dev.dogetime.data.MediaRepository
import dev.dogetime.data.NextEpisodeFinder
import dev.dogetime.data.TmdbApi
import dev.dogetime.data.WatchHistoryRepository
import dev.dogetime.data.WatchlistRepository
import dev.dogetime.data.createHttpClient
import dev.dogetime.data.local.AppDatabase
import dev.dogetime.domain.MediaType
import dev.dogetime.domain.WatchStatus
import dev.dogetime.domain.WatchTarget
import dev.dogetime.ui.details.DetailsScreen
import dev.dogetime.ui.home.HomeScreen
import dev.dogetime.ui.home.HomeViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test
import dev.dogetime.domain.DownloadItem
import dev.dogetime.domain.DownloadStatus
import dev.dogetime.ui.library.LibraryScreen
import dev.dogetime.ui.library.LibrarySection
import dev.dogetime.ui.library.LibraryViewModel
import dev.dogetime.data.UpdateChecker
import dev.dogetime.data.sync.AccountRepository
import dev.dogetime.data.sync.ChofoApi
import dev.dogetime.data.sync.WatchSync
import dev.dogetime.ui.account.AccountViewModel
import dev.dogetime.ui.settings.SettingsScreen
import dev.dogetime.ui.settings.SettingsViewModel
import dev.dogetime.ui.settings.StreamAddonsViewModel
import dev.dogetime.core.TorrentDownloadManager
import dev.dogetime.data.DownloadRepository
import dev.dogetime.data.OpenSubtitlesApi
import dev.dogetime.data.StremioSubtitlesApi
import dev.dogetime.data.SubdlApi
import dev.dogetime.data.SubtitleDownloader
import dev.dogetime.data.TorrentioApi
import dev.dogetime.data.YtsSubsApi
import dev.dogetime.data.addons.StreamAddonRepository
import dev.dogetime.ui.watch.WatchScreen
import dev.dogetime.ui.watch.WatchViewModel
import kotlinx.coroutines.flow.first
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import dev.dogetime.FloatingNavBar
import dev.dogetime.navigation.Destination

/**
 * Renders screens with real TMDB data and artwork to PNG files, to look at the UI without
 * opening the app. Only runs with SNAPSHOT_DIR set (it needs the network):
 * `SNAPSHOT_DIR=/tmp/shots ./gradlew :composeApp:desktopTest --tests '*UiSnapshots*'`.
 */
class UiSnapshots {
    private val outDir = System.getenv("SNAPSHOT_DIR")?.let(::File)
    private val db = Room.inMemoryDatabaseBuilder<AppDatabase>().setDriver(BundledSQLiteDriver()).setQueryCoroutineContext(Dispatchers.IO).build()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val http = createHttpClient()
    private val media = MediaRepository(TmdbApi(http), KitsuApi(http))
    private val history = WatchHistoryRepository(db.watchHistoryDao(), scope)
    private val watchlist = WatchlistRepository(db.watchlistDao())
    private val favorites = FavoritesRepository(db.favoriteDao())

    private class Size(val name: String, val widthDp: Int, val heightDp: Int, val density: Float)

    private val phone = Size("phone", 412, 915, 2f)
    private val desktop = Size("desktop", 1440, 900, 1f)
    private val landscape = Size("landscape", 915, 412, 2f)
    private val small = Size("small", 360, 780, 2f)
    private val tallPhone = Size("phone-full", 412, 2600, 1.5f)

    /** Renders [content] for [seconds] (so data and images arrive), then saves it. */
    private fun shoot(name: String, size: Size, seconds: Int = 8, content: @Composable () -> Unit) {
        val dir = outDir ?: return
        dir.mkdirs()
        ImageComposeScene(
            width = (size.widthDp * size.density).toInt(),
            height = (size.heightDp * size.density).toInt(),
            density = Density(size.density),
        ) {
            ChofoTheme(AppTheme.Midnight) {
                setSingletonImageLoaderFactory { context ->
                    ImageLoader.Builder(context).components { add(KtorNetworkFetcherFactory()) }.build()
                }
                // As the app's Scaffold does: the background and the default text colour.
                Surface(color = MaterialTheme.colorScheme.background, contentColor = MaterialTheme.colorScheme.onBackground) {
                    content()
                }
            }
        }.use { scene ->
            val start = System.nanoTime()
            var image = scene.render(0)
            while (System.nanoTime() - start < seconds * 1_000_000_000L) {
                Thread.sleep(100)
                image = scene.render(System.nanoTime() - start)
            }
            File(dir, "$name-${size.name}.png").writeBytes(image.encodeToData(EncodedImageFormat.PNG)!!.bytes)
        }
    }

    private suspend fun seed() {
        val severance = media.details(MediaType.Tv, 95396).item
        val dune = media.details(MediaType.Movie, 693134).item
        val got = media.details(MediaType.Tv, 1399).item
        history.saveProgress(WatchTarget.Episode(95396, 2, 3), severance, 1_200_000, 3_300_000)
        history.saveProgress(WatchTarget.Movie(693134), dune, 2_400_000, 9_960_000)
        history.saveProgress(WatchTarget.Episode(1399, 1, 4), got, 3_000_000, 3_300_000)
        watchlist.set(dune, WatchStatus.Watching)
        watchlist.set(got, WatchStatus.PlanToWatch)
        favorites.toggle(got)
        val downloads = DownloadRepository(db.downloadDao())
        fun dl(id: String, title: String, item: dev.dogetime.domain.MediaItem, season: Int?, episode: Int?, at: Long, status: DownloadStatus = DownloadStatus.COMPLETED, progress: Float = 1f) =
            DownloadItem(id = id, title = title, mediaType = item.type, mediaId = item.id, season = season, episode = episode,
                posterUrl = item.posterUrl, backdropUrl = item.backdropUrl, magnet = "magnet:?xt=urn:btih:$id", infoHash = id,
                quality = "1080p", totalBytes = 1_800_000_000, downloadedBytes = (1_800_000_000 * progress).toLong(), progress = progress,
                status = status, createdAt = at)
        downloads.upsert(dl("s1", "Severance S1E1", severance, 1, 1, 1))
        downloads.upsert(dl("s2", "Severance S1E2", severance, 1, 2, 2))
        downloads.upsert(dl("s3", "Severance S2E1", severance, 2, 1, 5, DownloadStatus.DOWNLOADING, 0.42f))
        downloads.upsert(dl("d", "Dune: Part Two", dune, null, null, 3))
        downloads.upsert(dl("g", "Game of Thrones S1E1", got, 1, 1, 4))
    }

    @Test
    fun screens() {
        if (outDir == null) return println("SNAPSHOT_DIR is not set; skipping.")
        runBlocking { seed() }
        val libSubtitles = SubtitleDownloader(OpenSubtitlesApi(http), SubdlApi(http), YtsSubsApi(http), StremioSubtitlesApi(http))
        val library = LibraryViewModel(TorrentDownloadManager(DownloadRepository(db.downloadDao()), libSubtitles), history, favorites, watchlist)
        val home = HomeViewModel(media, favorites, history, watchlistRepository = watchlist, nextEpisodes = NextEpisodeFinder(media))
        for (size in listOf(phone, desktop, landscape, small)) {
            shoot("home", size) {
                val haze = rememberHazeState()
                val wide = size == desktop || size == landscape
                Box(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize().hazeSource(haze)) {
                        HomeScreen(home, onOpen = {}, isWide = wide, startInset = if (wide) 84.dp else 0.dp, bottomInset = if (wide) 0.dp else 96.dp)
                    }
                    FloatingNavBar(
                        root = Destination.Home,
                        onSelect = {},
                        hazeState = haze,
                        isVertical = wide,
                        compact = size == landscape,
                        // Room for a phone's status and navigation bars in landscape.
                        modifier = if (wide) Modifier.align(Alignment.CenterStart).padding(vertical = if (size == landscape) 28.dp else 0.dp)
                        else Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp),
                    )
                }
            }
            shoot("details-tv", size) {
                DetailsScreen(MediaType.Tv, 95396, media, favorites, onBack = {}, onWatch = { _, _, _, _, _ -> }, onOpen = {}, watchHistory = history, watchlist = watchlist)
            }
            shoot("details-movie", size) {
                DetailsScreen(MediaType.Movie, 693134, media, favorites, onBack = {}, onWatch = { _, _, _, _, _ -> }, onOpen = {}, watchHistory = history, watchlist = watchlist)
            }
            for (section in LibrarySection.entries) {
                library.showSection(section)
                shoot("library-${section.name.lowercase()}", size, seconds = 5) { LibraryScreen(library, onOpen = {}, onPlayDownload = {}) }
            }
            library.showSection(LibrarySection.Downloads)
            library.openGroup("Tv:95396")
            shoot("library-show", size, seconds = 5) { LibraryScreen(library, onOpen = {}, onPlayDownload = {}) }
            library.closeGroup()
        }
        val dune = runBlocking { media.details(MediaType.Movie, 693134) }
        val target = WatchTarget.Movie(693134, imdbId = dune.imdbId)
        val addons = StreamAddonRepository(db.preferenceDao(), http)
        val subtitles = SubtitleDownloader(OpenSubtitlesApi(http), SubdlApi(http), YtsSubsApi(http), StremioSubtitlesApi(http))
        fun watchVm() = WatchViewModel(
            target, "Dune: Part Two", dune.item, dune.item.backdropUrl, TorrentioApi(http), addons,
            TorrentDownloadManager(DownloadRepository(db.downloadDao()), subtitles),
            StremioSubtitlesApi(http), OpenSubtitlesApi(http), SubdlApi(http), history,
        )
        for (size in listOf(phone, desktop)) {
            val menu = watchVm()
            shoot("watch-menu", size, seconds = 5) {
                WatchScreen(menu, target, "Dune: Part Two", createPlayerViewModel = { error("not played") }, onBack = {}, backdropUrl = dune.item.backdropUrl, mediaItem = dune.item)
            }
            val streams = watchVm()
            runBlocking { streams.openTorrents(addons.addons.first().first()) }
            shoot("watch-streams", size, seconds = 10) {
                WatchScreen(streams, target, "Dune: Part Two", createPlayerViewModel = { error("not played") }, onBack = {}, backdropUrl = dune.item.backdropUrl, mediaItem = dune.item)
            }
            menu.clear()
            streams.clear()
        }
        val accounts = AccountRepository(ChofoApi("https://api.example"), db.preferenceDao(), scope)
        val sync = WatchSync(ChofoApi("https://api.example"), accounts, db.watchHistoryDao(), db.preferenceDao(), null, scope)
        val settings = SettingsViewModel(UpdateChecker(http))
        val account = AccountViewModel(accounts, sync)
        val addonSettings = StreamAddonsViewModel(addons)
        for (size in listOf(Size("phone-full", 412, 1900, 1.5f), desktop)) {
            shoot("settings", size, seconds = 4) {
                SettingsScreen(AppTheme.Midnight, {}, torrentBufferMb = 25, onTorrentBufferChange = {}, viewModel = settings, accountViewModel = account, onSignIn = {}, streamAddonsViewModel = addonSettings)
            }
        }
        shoot("home", tallPhone) { HomeScreen(home, onOpen = {}, isWide = false, bottomInset = 96.dp) }
        home.clear()
    }
}
