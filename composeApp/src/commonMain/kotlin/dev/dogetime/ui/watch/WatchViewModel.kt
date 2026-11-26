package dev.dogetime.ui.watch

import dev.dogetime.core.TorrentDownloadManager
import dev.dogetime.core.Torrents
import dev.dogetime.core.UiState
import dev.dogetime.core.ViewModel
import dev.dogetime.core.runForUiState
import dev.dogetime.data.OpenSubtitlesApi
import dev.dogetime.data.StremioSubtitlesApi
import dev.dogetime.data.SubdlApi
import dev.dogetime.data.addons.StreamAddon
import dev.dogetime.data.addons.StreamAddonRepository
import dev.dogetime.data.TorrentioApi
import dev.dogetime.data.WatchHistoryRepository
import dev.dogetime.domain.DownloadItem
import dev.dogetime.domain.MediaItem
import dev.dogetime.domain.MediaType
import dev.dogetime.domain.RememberedStream
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
import kotlinx.coroutines.flow.first
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
    /** The stream last played for this movie or episode, here or on another signed-in device. */
    val remembered: RememberedStream? = null,
    /** Looking up a fresh link for [remembered] from its add-on. */
    val isResuming: Boolean = false,
    /** Still finding out whether there's a stream to start straight away. */
    val isCheckingRemembered: Boolean = false,
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
    private val watchHistory: WatchHistoryRepository? = null,
    /** Opened from Continue Watching: play the last stream without showing the source menu. */
    private val resumeLastStream: Boolean = false,
) : ViewModel() {

    private val _state = MutableStateFlow(WatchUiState(isCheckingRemembered = resumeLastStream && watchHistory != null))
    val state: StateFlow<WatchUiState> = _state.asStateFlow()

    /** Whether torrents can be downloaded on this platform. */
    val canDownload: Boolean get() = downloadManager.isSupported

    private var torrentsJob: Job? = null
    private var resumeJob: Job? = null
    private var subtitlesJob: Job? = null
    private var noticeJob: Job? = null

    fun pickSource(source: VideoSource) {
        _state.update { it.copy(step = WatchStep.Source(source)) }
    }

    init {
        scope.launch {
            val key = historyKey()
            val remembered = watchHistory?.rememberedStream(key.type, key.id, key.season, key.episode)
                ?.takeIf { it.kind != RememberedStream.Kind.Torrent || Torrents.isSupported }
            // Unless "Change source" was pressed while this was looked up.
            // The spinner stays up until the stream is opened, so the menu never flashes by.
            val stillWaiting = _state.value.isCheckingRemembered
            _state.update { it.copy(remembered = remembered, isCheckingRemembered = stillWaiting) }
            when {
                !stillWaiting -> Unit
                remembered != null -> resumeRemembered()
                // A new episode: carry on with what the show was last watched with.
                else -> continueWithPreviousStream(key)
            }
        }
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
        val pick = pickFor(option, _state.value.provider?.id)
        _state.update { it.copy(step = WatchStep.Torrent(pick)) }
    }

    private fun pickFor(option: TorrentOption, addonId: String?): TorrentPick {
        val episode = target as? WatchTarget.Episode
        return TorrentPick(
            option.magnet, option.fileName, option.fileIndex, episode?.season, episode?.episode,
            directUrl = option.directUrl, headers = option.headers,
            remembered = RememberedStream.of(option, addonId),
        )
    }

    /**
     * Opens [WatchUiState.remembered] again. A direct link is asked for again from its add-on
     * when that's installed here, since such links often expire; the saved one is the fallback.
     */
    fun resumeRemembered() {
        val remembered = _state.value.remembered ?: return
        val episode = target as? WatchTarget.Episode
        when (remembered.kind) {
            RememberedStream.Kind.Web -> {
                val source = remembered.videoSource ?: return backToSourceMenu()
                _state.update { it.copy(step = WatchStep.Source(source), isCheckingRemembered = false) }
            }
            RememberedStream.Kind.Torrent -> {
                val magnet = remembered.magnet ?: return backToSourceMenu()
                val pick = TorrentPick(magnet, remembered.fileName, remembered.fileIndex, episode?.season, episode?.episode, remembered = remembered)
                _state.update { it.copy(step = WatchStep.Torrent(pick), isCheckingRemembered = false) }
            }
            RememberedStream.Kind.Direct -> {
                if (resumeJob?.isActive == true) return
                _state.update { it.copy(isResuming = true, isCheckingRemembered = false) }
                resumeJob = scope.launch {
                    val fresh = freshLink(remembered)
                    val pick = fresh?.let {
                        TorrentPick(it.magnet, it.fileName, it.fileIndex, episode?.season, episode?.episode, it.directUrl, it.headers, remembered)
                    } ?: TorrentPick(
                        "", remembered.fileName, remembered.fileIndex, episode?.season, episode?.episode,
                        remembered.url, remembered.headers, remembered,
                    )
                    _state.update { it.copy(isResuming = false, step = WatchStep.Torrent(pick)) }
                }
            }
        }
    }

    /**
     * Opens this episode the way the show's previous one was watched: the same web source, or the
     * same add-on. From an add-on, the same torrent plays when it has this episode too (a season
     * pack); otherwise its list for this episode is shown. With nothing to go on, the menu shows.
     */
    private suspend fun continueWithPreviousStream(key: HistoryKey) {
        val previous = if (target is WatchTarget.Episode) watchHistory?.latestRememberedStream(key.type, key.id) else null
        val source = previous?.videoSource
        if (previous?.kind == RememberedStream.Kind.Web && source != null) {
            _state.update { it.copy(step = WatchStep.Source(source), isCheckingRemembered = false) }
            return
        }
        val addon = previous?.addonId?.let { id -> addonRepository.addons.first().firstOrNull { it.id == id && it.canServe(target) } }
        if (previous == null || addon == null || (previous.kind == RememberedStream.Kind.Torrent && !Torrents.isSupported)) {
            _state.update { it.copy(isCheckingRemembered = false) }
            return
        }
        val streams = runForUiState { torrentio.streams(target, addon) }
        val hash = previous.magnet?.let(::extractInfoHash)?.lowercase()
        val samePack = (streams as? UiState.Success)?.data?.firstOrNull {
            hash != null && it.isTorrent && extractInfoHash(it.magnet).lowercase() == hash
        }
        val step = samePack?.let { WatchStep.Torrent(pickFor(it, addon.id)) } ?: WatchStep.Torrents
        _state.update { it.copy(provider = addon, torrents = streams, isCheckingRemembered = false, step = step) }
    }

    /** The same file from the same add-on, with a link fetched now. Null when it can't be found. */
    private suspend fun freshLink(remembered: RememberedStream): TorrentOption? {
        // Read the installed add-ons directly: on a resume at start-up the state may not have them yet.
        val addon = addonRepository.addons.first().firstOrNull { it.id == remembered.addonId && it.canServe(target) } ?: return null
        val name = remembered.fileName ?: return null
        return try {
            torrentio.streams(target, addon).firstOrNull { it.directUrl != null && (it.fileName ?: it.release) == name }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    private data class HistoryKey(val type: MediaType, val id: Int, val season: Int?, val episode: Int?)

    /** Where the player saves this target's progress (see [downloadItemFor] for the type). */
    private fun historyKey(): HistoryKey {
        val type = mediaItem?.type ?: if (target is WatchTarget.Movie) MediaType.Movie else MediaType.Tv
        return when (target) {
            is WatchTarget.Movie -> HistoryKey(type, mediaItem?.id ?: target.id, null, null)
            is WatchTarget.Episode -> HistoryKey(type, mediaItem?.id ?: target.showId, target.season, target.episode)
        }
    }

    /** Back to the torrent list from a playing torrent. */
    fun backToTorrents() {
        val state = _state.value
        if (state.provider != null) {
            _state.update { it.copy(step = WatchStep.Torrents) }
            return
        }
        // Resumed without opening a list: show the list of the add-on it came from, else the menu.
        val addon = state.addons.firstOrNull { it.id == state.remembered?.addonId }
        if (addon != null) openTorrents(addon) else backToSourceMenu()
    }

    fun backToSourceMenu() {
        resumeJob?.cancel()
        _state.update { it.copy(step = WatchStep.SourceMenu, isResuming = false, isCheckingRemembered = false) }
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
