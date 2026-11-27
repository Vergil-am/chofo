package dev.dogetime.ui.watch

import dev.dogetime.core.TorrentDownloadManager
import dev.dogetime.core.Torrents
import dev.dogetime.core.UiState
import dev.dogetime.core.ViewModel
import dev.dogetime.core.runForUiState
import dev.dogetime.data.MediaRepository
import dev.dogetime.data.OpenSubtitlesApi
import dev.dogetime.data.StremioSubtitlesApi
import dev.dogetime.data.SubdlApi
import dev.dogetime.data.addons.StreamAddon
import dev.dogetime.data.addons.StreamAddonRepository
import dev.dogetime.data.TorrentioApi
import dev.dogetime.data.WatchHistoryRepository
import dev.dogetime.domain.DownloadItem
import dev.dogetime.domain.MediaDetails
import dev.dogetime.ui.details.EpisodesState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
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
    /** Still working out what to open: the last stream, or which add-on's list. */
    val isCheckingRemembered: Boolean = false,
    /** No installed add-on can list streams for this title (or torrents don't work here). */
    val noStreamSources: Boolean = false,
    /** The show's episodes, for the player's episode panel. Untouched for movies. */
    val episodePanel: EpisodePanelState = EpisodePanelState(),
)

/** The show and its selected season's episodes, loaded when the player's episode panel first opens. */
data class EpisodePanelState(
    val details: UiState<MediaDetails> = UiState.Loading,
    val episodes: EpisodesState = EpisodesState(),
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
    /** Looks up the show's episodes for the player's panel. Without it, there's no episode list. */
    private val media: MediaRepository? = null,
) : ViewModel() {

    private val _state = MutableStateFlow(WatchUiState(isCheckingRemembered = true))
    val state: StateFlow<WatchUiState> = _state.asStateFlow()

    /** Whether torrents can be downloaded on this platform. */
    val canDownload: Boolean get() = downloadManager.isSupported

    private var torrentsJob: Job? = null
    private var resumeJob: Job? = null
    private var subtitlesJob: Job? = null
    private var noticeJob: Job? = null
    private var seasonsJob: Job? = null
    private var episodesJob: Job? = null

    /** Whether the player can list this show's other episodes. */
    val hasEpisodes: Boolean get() = target is WatchTarget.Episode && media != null

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
                resumeLastStream && remembered != null -> resumeRemembered()
                // A new episode: carry on with what the show was last watched with.
                resumeLastStream -> continueWithPreviousStream(key)
                // From the title's page: straight to the stream list, of the add-on used last time.
                else -> openDefaultTorrents(remembered?.addonId)
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
            openDefaultTorrents(previous?.addonId)
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

    /**
     * The stream list of [preferredAddonId] when it can serve this title, else of the first add-on
     * that can. With none (or no torrent support here), says so instead.
     */
    private suspend fun openDefaultTorrents(preferredAddonId: String?) {
        val usable = addonRepository.addons.first().filter { it.canServe(target) }
        val addon = usable.firstOrNull { it.id == preferredAddonId } ?: usable.firstOrNull()
        if (addon == null || !Torrents.isSupported) {
            _state.update { it.copy(step = WatchStep.SourceMenu, isCheckingRemembered = false, isResuming = false, noStreamSources = true) }
            return
        }
        _state.update { it.copy(step = WatchStep.Torrents, provider = addon, isCheckingRemembered = false, isResuming = false) }
        loadTorrents()
    }

    /**
     * Makes sure the player's source panel has a stream list: when the player was opened straight
     * from a remembered stream, no list was loaded yet. A failed list is tried again.
     */
    fun loadSourcesForPanel() {
        val state = _state.value
        if (state.provider == null) {
            val addon = state.addons.firstOrNull { it.id == state.remembered?.addonId } ?: state.addons.firstOrNull() ?: return
            selectProvider(addon)
            return
        }
        val needsList = state.torrents is UiState.Error || (state.torrents is UiState.Loading && torrentsJob?.isActive != true)
        if (needsList) loadTorrents()
    }

    /** Plays [option] from the player's panel, unless it's what's playing already. */
    fun switchSource(option: TorrentOption) {
        val current = (_state.value.step as? WatchStep.Torrent)?.pick
        if (current != null && current.isSameStream(option)) return
        playTorrent(option)
    }

    /** How far each episode of the show was watched (season to episode), for the episode panel. */
    val episodeProgress: StateFlow<Map<Pair<Int, Int>, Float>> by lazy {
        val key = historyKey()
        (watchHistory?.observeByMedia(key.type, key.id) ?: emptyFlow())
            .map { list ->
                list.mapNotNull { item ->
                    val s = item.season
                    val e = item.episode
                    if (s != null && e != null && item.progress > 0f) (s to e) to item.progress else null
                }.toMap()
            }
            .stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyMap())
    }

    /** Loads the show and the playing episode's season, the first time the episode panel opens. */
    fun loadEpisodePanel() {
        val episode = target as? WatchTarget.Episode ?: return
        val repository = media ?: return
        val panel = _state.value.episodePanel
        if (seasonsJob?.isActive == true) return
        if (panel.details is UiState.Success) {
            if (panel.episodes.content is UiState.Error) panel.episodes.seasonNumber?.let(::selectEpisodeSeason)
            return
        }
        _state.update { it.copy(episodePanel = EpisodePanelState()) }
        seasonsJob = scope.launch {
            val details = runForUiState { repository.details(historyKey().type, episode.showId) }
            _state.update { it.copy(episodePanel = it.episodePanel.copy(details = details)) }
            if (details is UiState.Success) {
                val seasons = details.data.seasons
                val season = seasons.firstOrNull { it.number == episode.season }?.number
                    ?: seasons.firstOrNull()?.number
                    ?: episode.season
                selectEpisodeSeason(season)
            }
        }
    }

    fun selectEpisodeSeason(season: Int) {
        val episode = target as? WatchTarget.Episode ?: return
        val repository = media ?: return
        val current = _state.value.episodePanel.episodes
        if (current.seasonNumber == season && current.content is UiState.Success) return
        episodesJob?.cancel()
        _state.update { it.copy(episodePanel = it.episodePanel.copy(episodes = EpisodesState(season, UiState.Loading))) }
        episodesJob = scope.launch {
            val episodes = runForUiState { repository.episodes(episode.showId, season, historyKey().type) }
            _state.update { it.copy(episodePanel = it.episodePanel.copy(episodes = EpisodesState(season, episodes))) }
        }
    }

    /** Back to the stream list ("Change source"), opening one if none was yet. */
    fun backToTorrents() {
        resumeJob?.cancel()
        val state = _state.value
        if (state.provider != null) {
            _state.update { it.copy(step = WatchStep.Torrents, isResuming = false, isCheckingRemembered = false) }
            return
        }
        scope.launch { openDefaultTorrents(state.remembered?.addonId) }
    }

    /** There's no source menu any more: "Change source" goes to the stream list. */
    fun backToSourceMenu() = backToTorrents()

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
