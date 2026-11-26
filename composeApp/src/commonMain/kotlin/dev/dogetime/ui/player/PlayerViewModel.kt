package dev.dogetime.ui.player

import dev.dogetime.core.CapturedStream
import dev.dogetime.core.TorrentDownloadManager
import dev.dogetime.core.ViewModel
import dev.dogetime.core.extractAndSaveSubtitle
import dev.dogetime.data.KitsuMappingRepository
import dev.dogetime.data.OpenSubtitlesApi
import dev.dogetime.data.StremioSubtitlesApi
import dev.dogetime.data.SubdlApi
import dev.dogetime.data.SubtitleDownloader
import dev.dogetime.data.WatchHistoryRepository
import dev.dogetime.data.WatchlistRepository
import dev.dogetime.data.CompletionTracker
import dev.dogetime.data.YtsSubsApi
import dev.dogetime.domain.DownloadItem
import dev.dogetime.domain.MediaItem
import dev.dogetime.domain.MediaType
import dev.dogetime.domain.RememberedStream
import dev.dogetime.domain.Subtitle
import dev.dogetime.domain.SubtitleGroup
import dev.dogetime.domain.WatchTarget
import dev.dogetime.domain.isFinished
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What the player was opened with. */
sealed interface PlaybackSource {
    /** A stream that is already playable: a captured web stream or a torrent's local server. */
    data class Stream(
        val stream: CapturedStream,
        val title: String,
        val target: WatchTarget? = null,
        val mediaItem: MediaItem? = null,
        /** How to open this stream again, saved with the progress. Null when it can't be. */
        val remembered: RememberedStream? = null,
    ) : PlaybackSource

    /** A finished download. Its files are looked up on disk before playback starts. */
    data class Download(val item: DownloadItem) : PlaybackSource
}

/** Everything the player needs to start. [target] and [mediaItem] are null when progress isn't tracked. */
data class Playback(
    val stream: CapturedStream,
    val title: String,
    val target: WatchTarget?,
    val mediaItem: MediaItem?,
    val remembered: RememberedStream? = null,
)

/** One subtitle provider's results. */
data class SubtitleSearch(
    val groups: List<SubtitleGroup> = emptyList(),
    val isLoading: Boolean = false,
    val hasSearched: Boolean = false,
)

data class PlayerUiState(
    /** Null while a download's files are being found. */
    val playback: Playback? = null,
    /** Set when the player can't start at all, e.g. a download's video file is gone. */
    val loadError: String? = null,
    val activeSubtitleLanguage: String? = null,
    /**
     * The external subtitle the player should show. Kept in state rather than sent once, so a
     * player rebuilt by "Try again" loads it again.
     */
    val subtitleFile: SubtitleFile? = null,
    val stremio: SubtitleSearch = SubtitleSearch(),
    val subdl: SubtitleSearch = SubtitleSearch(),
    val openSubtitles: SubtitleSearch = SubtitleSearch(),
    val ytsSubs: SubtitleSearch = SubtitleSearch(),
    val canSearchSubdl: Boolean = false,
    val canSearchOpenSubtitles: Boolean = false,
    val canSearchYtsSubs: Boolean = false,
    /** The subtitle being downloaded, so the menu can show a spinner next to it. */
    val downloadingSubtitleUrl: String? = null,
    val subtitleError: String? = null,
) {
    /** Every provider's results, one group per language. */
    val subtitleGroups: List<SubtitleGroup> =
        mergeSubtitleGroups(stremio.groups + subdl.groups + openSubtitles.groups + ytsSubs.groups)

    val isLoadingAnySubtitles: Boolean
        get() = stremio.isLoading || subdl.isLoading || openSubtitles.isLoading || ytsSubs.isLoading
}

/**
 * A subtitle file on disk for the player to load. [id] tells two loads of the same file apart.
 * [announce] shows its language over the video.
 */
data class SubtitleFile(val path: String, val language: String, val announce: Boolean, val id: Int)

/**
 * State and work behind [VideoPlayerScreen] that doesn't touch the video itself: finding a
 * download's files, resolving Kitsu ids to IMDb ids, searching and downloading subtitles, and
 * reading and saving watch progress. The playback controller stays in the Composable, which loads
 * [PlayerUiState.subtitleFile] and reports positions back.
 */
class PlayerViewModel(
    source: PlaybackSource,
    private val watchHistory: WatchHistoryRepository,
    private val downloadManager: TorrentDownloadManager,
    private val subtitleDownloader: SubtitleDownloader,
    private val kitsuMapping: KitsuMappingRepository,
    private val stremioSubtitles: StremioSubtitlesApi,
    private val subdl: SubdlApi,
    private val openSubtitles: OpenSubtitlesApi,
    private val ytsSubs: YtsSubsApi,
    private val watchlist: WatchlistRepository? = null,
    /** Ignore the saved position: "Start from the beginning". */
    private val startFromBeginning: Boolean = false,
    private val completion: CompletionTracker? = null,
) : ViewModel() {

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    private var nextSubtitleId = 0

    private fun showSubtitle(path: String, language: String, announce: Boolean) {
        val file = SubtitleFile(path, language, announce, id = nextSubtitleId++)
        _state.update { it.copy(subtitleFile = file, activeSubtitleLanguage = language) }
    }

    /** The target with its Kitsu id resolved to an IMDb id, once that's done. */
    private var searchTarget: WatchTarget? = null

    init {
        when (source) {
            is PlaybackSource.Stream -> start(
                Playback(source.stream, source.title, source.target, source.mediaItem, source.remembered),
                searchSubtitles = true,
            )
            is PlaybackSource.Download -> scope.launch { openDownload(source.item) }
        }
    }

    private suspend fun openDownload(item: DownloadItem) {
        val resolved = try {
            downloadManager.resolveOfflinePlayback(item)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.update { it.copy(loadError = e.message ?: "The download couldn't be opened.") }
            return
        }
        val path = resolved.videoFilePath
        if (path == null) {
            _state.update { it.copy(loadError = "The downloaded video file is missing.") }
            return
        }
        // Watch history is keyed by TMDB id. A download without one (e.g. added from a bare
        // magnet) isn't tracked, rather than saved under a made-up id that could collide with a
        // real title.
        val mediaId = resolved.mediaId
        start(
            Playback(
                stream = CapturedStream(url = path, headers = emptyMap()),
                title = resolved.title,
                target = mediaId?.let { resolved.watchTarget(it) },
                mediaItem = mediaId?.let { resolved.toMediaItem(it) },
            ),
            // Downloads play offline: no subtitle searches, only the subtitle saved with them.
            searchSubtitles = false,
        )
        resolved.subtitleFilePath?.let { subtitlePath ->
            showSubtitle(subtitlePath, resolved.subtitleLanguage ?: "Default", announce = false)
        }
    }

    private fun start(playback: Playback, searchSubtitles: Boolean) {
        _state.update { it.copy(playback = playback) }
        val target = playback.target
        if (target == null || !searchSubtitles) return
        searchTarget = target
        _state.update { it.withSearchAbility(target) }

        if (target.imdbId == null && target.kitsuId != null) {
            scope.launch {
                val resolved = resolveTarget(target)
                searchTarget = resolved
                _state.update { it.withSearchAbility(resolved) }
            }
        }
        // Stremio add-ons (OpenSubtitles v3, Anime Kitsu) are searched right away; the other
        // providers only when the user asks, since they have rate limits.
        scope.launch {
            _state.update { it.copy(stremio = it.stremio.copy(isLoading = true)) }
            val groups = try {
                stremioSubtitles.search(target)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                emptyList()
            }
            _state.update { it.copy(stremio = SubtitleSearch(groups, isLoading = false, hasSearched = true)) }
        }
    }

    private fun PlayerUiState.withSearchAbility(target: WatchTarget?) = copy(
        canSearchSubdl = target != null,
        canSearchOpenSubtitles = target != null,
        canSearchYtsSubs = target is WatchTarget.Movie && (target.imdbId != null || target.kitsuId != null),
    )

    private suspend fun resolveTarget(target: WatchTarget): WatchTarget {
        if (target.imdbId != null) return target
        val kitsuId = target.kitsuId ?: return target
        val resolved = try {
            kitsuMapping.resolve(kitsuId, (target as? WatchTarget.Episode)?.episode)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        } ?: return target
        return when (target) {
            is WatchTarget.Movie -> target.copy(imdbId = resolved.imdbId)
            is WatchTarget.Episode -> target.copy(
                imdbId = resolved.imdbId,
                season = resolved.season ?: target.season,
                episode = resolved.episode ?: target.episode,
            )
        }
    }

    fun searchSubdl() = search(
        get = { it.subdl },
        set = { state, search -> state.copy(subdl = search) },
        failure = "Failed to find SubDL subtitles.",
    ) { subdl.search(it) }

    fun searchOpenSubtitles() = search(
        get = { it.openSubtitles },
        set = { state, search -> state.copy(openSubtitles = search) },
        failure = "Failed to find OpenSubtitles subtitles.",
    ) { openSubtitles.search(it) }

    fun searchYtsSubs() {
        if (searchTarget !is WatchTarget.Movie) return
        search(
            get = { it.ytsSubs },
            set = { state, search -> state.copy(ytsSubs = search) },
            failure = "Failed to find YTS subtitles.",
        ) { ytsSubs.search(it) }
    }

    private fun search(
        get: (PlayerUiState) -> SubtitleSearch,
        set: (PlayerUiState, SubtitleSearch) -> PlayerUiState,
        failure: String,
        fetch: suspend (WatchTarget) -> List<SubtitleGroup>,
    ) {
        val target = searchTarget ?: return
        if (get(_state.value).isLoading) return
        _state.update { set(it, get(it).copy(isLoading = true, hasSearched = true)).copy(subtitleError = null) }
        scope.launch {
            try {
                val groups = fetch(resolveTarget(target))
                _state.update { set(it, get(it).copy(groups = groups, isLoading = false)) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { set(it, get(it).copy(isLoading = false)).copy(subtitleError = e.message ?: failure) }
            }
        }
    }

    /** Downloads [subtitle], unpacks it to a file and makes it [PlayerUiState.subtitleFile]. */
    fun pickSubtitle(subtitle: Subtitle) {
        if (_state.value.downloadingSubtitleUrl != null) return
        _state.update { it.copy(downloadingSubtitleUrl = subtitle.downloadUrl, subtitleError = null) }
        scope.launch {
            try {
                val bytes = subtitleDownloader.downloadBytes(subtitle)
                val path = withContext(Dispatchers.IO) { extractAndSaveSubtitle(bytes, subtitle.releaseName) }
                if (path != null) {
                    showSubtitle(path, subtitle.language, announce = true)
                } else {
                    _state.update { it.copy(subtitleError = "Could not extract subtitle file.") }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(subtitleError = e.message ?: "Failed to download subtitle.") }
            } finally {
                _state.update { it.copy(downloadingSubtitleUrl = null) }
            }
        }
    }

    fun onSubtitlesOff() {
        _state.update { it.copy(activeSubtitleLanguage = null, subtitleFile = null) }
    }

    /** Where to resume playback of a [durationMs]-long video, or null to start from the beginning. */
    suspend fun resumePosition(durationMs: Long): Long? {
        if (startFromBeginning) return null
        val playback = _state.value.playback ?: return null
        val target = playback.target ?: return null
        val mediaItem = playback.mediaItem ?: return null
        val episode = target as? WatchTarget.Episode
        val saved = try {
            watchHistory.getHistory(mediaItem.type, mediaItem.id, episode?.season, episode?.episode)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        } ?: return null
        return resumePositionFor(saved.positionMs, durationMs)
    }

    /**
     * Saves the playback position. Does nothing for untracked playback or a zero position.
     * [urgent] (play, pause, end) also asks for a sync to the account soon; the periodic saves
     * only update this device.
     */
    fun saveProgress(positionMs: Long, durationMs: Long, urgent: Boolean = false) {
        val (target, mediaItem) = trackedMedia() ?: return
        if (positionMs <= 0) return
        val stream = _state.value.playback?.remembered
        scope.launch {
            try {
                watchHistory.saveProgress(target, mediaItem, positionMs, durationMs, urgent, stream)
                // Playing something planned moves it to Watching; finishing it, to Completed.
                if (urgent && !isFinished(positionMs, durationMs)) watchlist?.startedWatching(mediaItem, target is WatchTarget.Episode)
                completion?.onProgress(target, mediaItem, positionMs, durationMs)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Losing one periodic save is harmless; the next one catches up.
            }
        }
    }

    /**
     * Like [saveProgress], but runs on the app scope, so it still completes while the screen and
     * this ViewModel are being torn down.
     */
    fun saveFinalProgress(positionMs: Long, durationMs: Long) {
        val (target, mediaItem) = trackedMedia() ?: return
        if (positionMs <= 0) return
        watchHistory.saveProgressDetached(target, mediaItem, positionMs, durationMs, _state.value.playback?.remembered)
        completion?.onProgress(target, mediaItem, positionMs, durationMs)
    }

    private fun trackedMedia(): Pair<WatchTarget, MediaItem>? {
        val playback = _state.value.playback ?: return null
        val target = playback.target ?: return null
        val mediaItem = playback.mediaItem ?: return null
        return target to mediaItem
    }
}

/**
 * The position to resume a [durationMs]-long video from, given the [savedMs] position. Very early
 * positions start over, and nearly finished ones start over too, to rewatch.
 */
internal fun resumePositionFor(savedMs: Long, durationMs: Long): Long? =
    savedMs.takeIf { it > RESUME_MIN_MS && !isFinished(it, durationMs) }

/** Combines the providers' groups into one group per language, sorted by language. */
internal fun mergeSubtitleGroups(groups: List<SubtitleGroup>): List<SubtitleGroup> =
    groups.groupBy { it.language }
        .map { (language, sameLanguage) -> SubtitleGroup(language, sameLanguage.flatMap { it.subtitles }) }
        .sortedBy { it.language }

private const val RESUME_MIN_MS = 5_000L

private fun DownloadItem.watchTarget(mediaId: Int): WatchTarget =
    if (season != null && episode != null) {
        WatchTarget.Episode(showId = mediaId, season = season, episode = episode)
    } else {
        WatchTarget.Movie(id = mediaId)
    }

private fun DownloadItem.toMediaItem(mediaId: Int): MediaItem = MediaItem(
    id = mediaId,
    type = mediaType ?: if (season != null) MediaType.Tv else MediaType.Movie,
    title = title,
    overview = "",
    posterUrl = posterUrl,
    backdropUrl = backdropUrl,
    rating = 0.0,
    year = null,
)
