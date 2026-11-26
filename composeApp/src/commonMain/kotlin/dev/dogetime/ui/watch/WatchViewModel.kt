package dev.dogetime.ui.watch

import dev.dogetime.core.TorrentDownloadManager
import dev.dogetime.core.UiState
import dev.dogetime.core.ViewModel
import dev.dogetime.core.runForUiState
import dev.dogetime.data.OpenSubtitlesApi
import dev.dogetime.data.StremioSubtitlesApi
import dev.dogetime.data.SubdlApi
import dev.dogetime.data.addons.StreamAddon
import dev.dogetime.data.addons.StreamAddonRepository
import dev.dogetime.data.TorrentioApi
import dev.dogetime.domain.DownloadItem
import dev.dogetime.domain.MediaItem
import dev.dogetime.domain.MediaType
import dev.dogetime.domain.Subtitle
import dev.dogetime.domain.SubtitleGroup
import dev.dogetime.domain.TorrentOption
import dev.dogetime.domain.VideoSource
import dev.dogetime.domain.WatchTarget
import dev.dogetime.domain.extractInfoHash
import kotlin.time.Clock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Where the user is in the watch flow. */
sealed interface WatchStep {
    /** Choosing a source or the torrent list. */
    data object SourceMenu : WatchStep

    /** Playing a web source's page. */
    data class Source(val source: VideoSource) : WatchStep

    /** Browsing torrents. */
    data object Torrents : WatchStep

    /** Playing a torrent picked from the list. */
    data class Torrent(val pick: TorrentPick) : WatchStep
}

/** The torrent you tapped download on, and the subtitles you can save with it. */
data class DownloadChoice(
    val option: TorrentOption,
    val subtitles: List<Subtitle> = emptyList(),
    val isLoadingSubtitles: Boolean = true,
)

data class WatchUiState(
    val step: WatchStep = WatchStep.SourceMenu,
    /** The stream add-ons that can serve this title, in the user's order. */
    val addons: List<StreamAddon> = emptyList(),
    /** The add-on whose streams are shown. */
    val provider: StreamAddon? = null,
    /** The stream list for [provider]. */
    val torrents: UiState<List<TorrentOption>> = UiState.Loading,
    val downloadChoice: DownloadChoice? = null,
    /** A short message, e.g. that a download was queued. Clears itself after a few seconds. */
    val notice: String? = null,
)

/**
 * State behind [WatchScreen]: which step of the watch flow is shown, the torrent list for the
 * chosen provider, and queueing downloads with an optional subtitle.
 */
class WatchViewModel(
    private val target: WatchTarget,
    private val title: String,
    private val mediaItem: MediaItem?,
    private val backdropUrl: String?,
    private val torrentio: TorrentioApi,
    private val addonRepository: StreamAddonRepository,
    private val downloadManager: TorrentDownloadManager,
    private val stremioSubtitles: StremioSubtitlesApi,
    private val openSubtitles: OpenSubtitlesApi,
    private val subdl: SubdlApi,
) : ViewModel() {

    private val _state = MutableStateFlow(WatchUiState())
    val state: StateFlow<WatchUiState> = _state.asStateFlow()

    /** Whether torrents can be downloaded on this platform. */
    val canDownload: Boolean get() = downloadManager.isSupported

    private var torrentsJob: Job? = null
    private var subtitlesJob: Job? = null
    private var noticeJob: Job? = null

    fun pickSource(source: VideoSource) {
        _state.update { it.copy(step = WatchStep.Source(source)) }
    }

    init {
        scope.launch {
            addonRepository.addons.collect { all ->
                val usable = all.filter { it.canServe(target) }
                _state.update { state ->
                    state.copy(addons = usable, provider = state.provider?.let { p -> usable.firstOrNull { it.id == p.id } })
                }
            }
        }
    }

    fun openTorrents(provider: StreamAddon) {
        _state.update { it.copy(step = WatchStep.Torrents) }
        selectProvider(provider)
    }

    fun selectProvider(provider: StreamAddon) {
        _state.update { it.copy(provider = provider) }
        loadTorrents()
    }

    fun loadTorrents() {
        val provider = _state.value.provider ?: return
        torrentsJob?.cancel()
        _state.update { it.copy(torrents = UiState.Loading) }
        torrentsJob = scope.launch {
            val result = runForUiState { torrentio.streams(target, provider) }
            _state.update { it.copy(torrents = result) }
        }
    }

    fun playTorrent(option: TorrentOption) {
        val episode = target as? WatchTarget.Episode
        val pick = TorrentPick(
            option.magnet, option.fileName, option.fileIndex, episode?.season, episode?.episode,
            directUrl = option.directUrl, headers = option.headers,
        )
        _state.update { it.copy(step = WatchStep.Torrent(pick)) }
    }

    /** Back to the torrent list from a playing torrent. */
    fun backToTorrents() {
        _state.update { it.copy(step = WatchStep.Torrents) }
    }

    fun backToSourceMenu() {
        _state.update { it.copy(step = WatchStep.SourceMenu) }
    }

    /** Opens the download dialog for [option] and looks for subtitles to save with it. */
    fun chooseDownload(option: TorrentOption) {
        _state.update { it.copy(downloadChoice = DownloadChoice(option)) }
        subtitlesJob?.cancel()
        subtitlesJob = scope.launch {
            val subtitles = findSubtitles()
            _state.update { state ->
                state.copy(downloadChoice = state.downloadChoice?.copy(subtitles = subtitles, isLoadingSubtitles = false))
            }
        }
    }

    fun dismissDownload() {
        subtitlesJob?.cancel()
        _state.update { it.copy(downloadChoice = null) }
    }

    /** Queues [option] for download, with [subtitle] saved next to it if one was picked. */
    fun download(option: TorrentOption, subtitle: Subtitle?) {
        if (!canDownload) return
        downloadManager.startDownload(downloadItemFor(target, title, mediaItem, backdropUrl, option), subtitle)
        val withSubtitle = subtitle?.let { " with ${it.language} subtitles" }.orEmpty()
        dismissDownload()
        showNotice("Download queued: $title$withSubtitle")
    }

    private fun showNotice(message: String) {
        noticeJob?.cancel()
        _state.update { it.copy(notice = message) }
        noticeJob = scope.launch {
            delay(NOTICE_DURATION_MS)
            _state.update { it.copy(notice = null) }
        }
    }

    /**
     * Subtitles for the download dialog. Providers are tried in order until one has results:
     * the Stremio add-ons first, since they have no rate limit.
     */
    private suspend fun findSubtitles(): List<Subtitle> {
        val providers: List<suspend (WatchTarget) -> List<SubtitleGroup>> = listOf(
            { stremioSubtitles.search(it) },
            { openSubtitles.search(it) },
            { subdl.search(it) },
        )
        for (search in providers) {
            val found = try {
                search(target).flatMap { it.subtitles }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                println("Subtitle search for the download dialog failed: ${e.message}")
                emptyList()
            }
            if (found.isNotEmpty()) return found.distinctBy { "${it.language}-${it.releaseName}" }
        }
        return emptyList()
    }

    private companion object {
        const val NOTICE_DURATION_MS = 4_000L
    }
}

/**
 * The [DownloadItem] for downloading [option] of [target]. Its id is the torrent's info hash,
 * plus the episode and file for episodes, since one season pack can hold many of them.
 */
internal fun downloadItemFor(
    target: WatchTarget,
    title: String,
    mediaItem: MediaItem?,
    backdropUrl: String?,
    option: TorrentOption,
    now: Long = Clock.System.now().toEpochMilliseconds(),
): DownloadItem {
    val hash = extractInfoHash(option.magnet)
    val episode = target as? WatchTarget.Episode
    val id = if (episode != null) {
        "${hash}_s${episode.season}e${episode.episode}_${option.fileIndex ?: option.fileName?.hashCode() ?: 0}"
    } else {
        "${hash}_movie"
    }
    return DownloadItem(
        id = id,
        title = title,
        // The media's own type, so anime (whose id is a Kitsu id) is saved as Anime and shares
        // watch history with streaming instead of landing under a TV show or movie that happens
        // to have the same TMDB id.
        mediaType = mediaItem?.type ?: when (target) {
            is WatchTarget.Movie -> MediaType.Movie
            is WatchTarget.Episode -> MediaType.Tv
        },
        mediaId = when (target) {
            is WatchTarget.Movie -> target.id
            is WatchTarget.Episode -> target.showId
        },
        season = episode?.season,
        episode = episode?.episode,
        posterUrl = mediaItem?.posterUrl,
        backdropUrl = backdropUrl,
        magnet = option.magnet,
        infoHash = hash,
        fileName = option.fileName,
        fileIndex = option.fileIndex,
        quality = option.quality,
        createdAt = now,
    )
}
