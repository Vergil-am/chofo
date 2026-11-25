package dev.dogetime.di

import dev.dogetime.core.TorrentDownloadManager
import dev.dogetime.data.DownloadRepository
import dev.dogetime.data.FavoritesRepository
import dev.dogetime.data.KitsuApi
import dev.dogetime.data.KitsuMappingRepository
import dev.dogetime.data.MediaRepository
import dev.dogetime.data.OfflineCopyFinder
import dev.dogetime.data.PreferencesRepository
import dev.dogetime.data.OpenSubtitlesApi
import dev.dogetime.data.StremioSubtitlesApi
import dev.dogetime.data.SubdlApi
import dev.dogetime.data.SubtitleDownloader
import dev.dogetime.data.TmdbApi
import dev.dogetime.data.TorrentioApi
import dev.dogetime.data.UpdateChecker
import dev.dogetime.data.WatchHistoryRepository
import dev.dogetime.data.YtsSubsApi
import dev.dogetime.data.createHttpClient
import dev.dogetime.data.sync.AccountRepository
import dev.dogetime.data.sync.ChofoApi
import dev.dogetime.data.sync.WatchSync
import dev.dogetime.Secrets
import dev.dogetime.data.local.createDatabase
import dev.dogetime.ui.player.PlaybackSource
import dev.dogetime.ui.player.PlayerViewModel
import dev.dogetime.ui.watch.WatchViewModel
import dev.dogetime.navigation.Destination
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Manual dependency container. Created once in App() and passed down to the screens. */
class AppContainer {
    private val httpClient = createHttpClient()
    private val database = createDatabase()

    /** For work that must finish even after the screen that started it is gone. */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val kitsuMapping: KitsuMappingRepository = KitsuMappingRepository(httpClient).apply { warmUp() }
    val kitsuApi: KitsuApi = KitsuApi(httpClient)
    val repository: MediaRepository = MediaRepository(TmdbApi(httpClient), kitsuApi)
    val torrentio: TorrentioApi = TorrentioApi(httpClient)
    val subdl: SubdlApi = SubdlApi(httpClient, kitsuMapping)
    val openSubtitles: OpenSubtitlesApi = OpenSubtitlesApi(httpClient, kitsuMapping)
    val ytsSubs: YtsSubsApi = YtsSubsApi(httpClient, kitsuMapping)
    val stremioSubtitles: StremioSubtitlesApi = StremioSubtitlesApi(httpClient, kitsuMapping)
    val favorites: FavoritesRepository = FavoritesRepository(database.favoriteDao())
    val watchHistory: WatchHistoryRepository = WatchHistoryRepository(database.watchHistoryDao(), appScope)
    val updateChecker: UpdateChecker = UpdateChecker(httpClient)
    val preferences: PreferencesRepository = PreferencesRepository(database.preferenceDao(), appScope)

    private val chofoApi = ChofoApi(Secrets.CHOFO_API_URL)
    val accounts: AccountRepository = AccountRepository(chofoApi, database.preferenceDao(), appScope)
    val watchSync: WatchSync = WatchSync(
        api = chofoApi,
        accounts = accounts,
        dao = database.watchHistoryDao(),
        prefs = database.preferenceDao(),
        media = repository,
        appScope = appScope,
    ).also { watchHistory.listener = it }

    val downloadRepository: DownloadRepository = DownloadRepository(database.downloadDao())
    val subtitleDownloader: SubtitleDownloader = SubtitleDownloader(
        openSubtitles = openSubtitles,
        subdl = subdl,
        ytsSubs = ytsSubs,
        stremioSubtitles = stremioSubtitles,
    )
    val downloadManager: TorrentDownloadManager = TorrentDownloadManager(
        repository = downloadRepository,
        subtitleDownloader = subtitleDownloader,
    )

    val offlineCopies: OfflineCopyFinder = OfflineCopyFinder(downloadRepository, downloadManager)

    fun playerViewModel(source: PlaybackSource): PlayerViewModel = PlayerViewModel(
        source = source,
        watchHistory = watchHistory,
        downloadManager = downloadManager,
        subtitleDownloader = subtitleDownloader,
        kitsuMapping = kitsuMapping,
        stremioSubtitles = stremioSubtitles,
        subdl = subdl,
        openSubtitles = openSubtitles,
        ytsSubs = ytsSubs,
    )

    fun watchViewModel(destination: Destination.Watch): WatchViewModel = WatchViewModel(
        target = destination.target,
        title = destination.title,
        mediaItem = destination.mediaItem,
        backdropUrl = destination.backdropUrl,
        torrentio = torrentio,
        downloadManager = downloadManager,
        stremioSubtitles = stremioSubtitles,
        openSubtitles = openSubtitles,
        subdl = subdl,
    )
}
