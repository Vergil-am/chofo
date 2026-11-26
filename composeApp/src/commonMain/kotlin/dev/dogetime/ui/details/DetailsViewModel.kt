package dev.dogetime.ui.details

import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.CancellationException
import dev.dogetime.domain.WatchHistoryItem
import dev.dogetime.data.NextEpisodeFinder
import dev.dogetime.data.NextEpisode
import dev.dogetime.core.UiState
import dev.dogetime.core.ViewModel
import dev.dogetime.core.runForUiState
import dev.dogetime.data.FavoritesRepository
import dev.dogetime.data.MediaRepository
import dev.dogetime.data.WatchHistoryRepository
import dev.dogetime.data.WatchlistRepository
import dev.dogetime.domain.WatchStatus
import kotlinx.coroutines.flow.flowOf
import dev.dogetime.domain.Episode
import dev.dogetime.domain.MediaDetails
import dev.dogetime.domain.MediaType
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The season the user picked and the state of its episode list. */
data class EpisodesState(
    val seasonNumber: Int? = null,
    val content: UiState<List<Episode>> = UiState.Loading,
)

/** What a show's play button offers, from its watch history. */
data class PlayButton(val season: Int, val episode: Int, val kind: Kind) {
    enum class Kind {
        /** Nothing watched yet. */
        Start,
        /** The latest episode watched isn't finished. */
        Continue,
        /** The latest episode watched is finished; this is the next one out. */
        Next,
        /** Finished and nothing newer has aired: from the first episode again. */
        Rewatch,
    }
}

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class DetailsViewModel(
    private val repository: MediaRepository,
    private val favorites: FavoritesRepository,
    private val type: MediaType,
    private val id: Int,
    watchHistory: WatchHistoryRepository? = null,
    private val watchlist: WatchlistRepository? = null,
    private val nextEpisodes: NextEpisodeFinder? = null,
) : ViewModel() {

    private val _state = MutableStateFlow<UiState<MediaDetails>>(UiState.Loading)
    val state: StateFlow<UiState<MediaDetails>> = _state.asStateFlow()

    private val _episodes = MutableStateFlow(EpisodesState())
    val episodes: StateFlow<EpisodesState> = _episodes.asStateFlow()

    private var episodesJob: Job? = null

    val isFavorite: StateFlow<Boolean> = favorites.observeIsFavorite(type, id)
        .stateIn(scope, SharingStarted.Eagerly, initialValue = false)

    /** Where the title is on the watchlist, or null when it isn't. */
    val watchStatus: StateFlow<WatchStatus?> = (watchlist?.observeStatus(type, id) ?: flowOf(null))
        .stateIn(scope, SharingStarted.Eagerly, initialValue = null)

    /**
     * The episode a show's play button plays: the latest one watched while it's unfinished, else
     * the next one out. Null (the screen picks) until history is read, and for movies.
     */
    val playButton: StateFlow<PlayButton?> = (watchHistory?.observeByMedia(type, id) ?: emptyFlow())
        .mapLatest { history -> playButtonFor(history) }
        .stateIn(scope, SharingStarted.Eagerly, initialValue = null)

    private suspend fun playButtonFor(history: List<WatchHistoryItem>): PlayButton? {
        if (type == MediaType.Movie) return null
        val latest = history.filter { it.season != null && it.episode != null }.maxByOrNull { it.lastWatchedAt } ?: return null
        val season = latest.season!!
        val episode = latest.episode!!
        if (!latest.isFinished) return PlayButton(season, episode, PlayButton.Kind.Continue)
        val next = try {
            nextEpisodes?.after(type, id, season, episode)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null // offline: fall back to the episode after, by number
        }
        return when (next) {
            is NextEpisode.Available -> PlayButton(next.episode.season, next.episode.episode, PlayButton.Kind.Next)
            NextEpisode.None -> PlayButton(firstSeason(), 1, PlayButton.Kind.Rewatch)
            null -> PlayButton(season, episode + 1, PlayButton.Kind.Next)
        }
    }

    /** The first numbered season (Specials, season 0, come last), or 1 before details load. */
    private fun firstSeason(): Int =
        (_state.value as? UiState.Success)?.data?.seasons?.map { it.number }?.filter { it > 0 }?.minOrNull() ?: 1

    val episodeProgress: StateFlow<Map<Pair<Int, Int>, Float>> = (watchHistory?.observeByMedia(type, id) ?: emptyFlow())
        .map { list ->
            list.mapNotNull { item ->
                val s = item.season
                val e = item.episode
                if (s != null && e != null && item.progress > 0f) {
                    (s to e) to item.progress
                } else null
            }.toMap()
        }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    init {
        load()
    }

    fun load() {
        _state.value = UiState.Loading
        _episodes.value = EpisodesState()
        scope.launch {
            val result = runForUiState { repository.details(type, id) }
            _state.value = result
            // TV shows open on their first season so the episode list isn't empty.
            val firstSeason = (result as? UiState.Success)?.data?.seasons?.firstOrNull()
            if (firstSeason != null) selectSeason(firstSeason.number)
        }
    }

    fun toggleFavorite() {
        val item = (_state.value as? UiState.Success)?.data?.item ?: return
        scope.launch { favorites.toggle(item) }
    }

    /** Puts the title on the watchlist with [status], or takes it off with null. */
    fun setWatchStatus(status: WatchStatus?) {
        val list = watchlist ?: return
        val item = (_state.value as? UiState.Success)?.data?.item ?: return
        scope.launch { if (status == null) list.remove(type, id) else list.set(item, status) }
    }

    fun selectSeason(seasonNumber: Int) {
        if (type != MediaType.Tv && type != MediaType.Anime) return
        val current = _episodes.value
        if (current.seasonNumber == seasonNumber && current.content is UiState.Success) return

        episodesJob?.cancel()
        _episodes.value = EpisodesState(seasonNumber, UiState.Loading)
        episodesJob = scope.launch {
            val content = runForUiState { repository.episodes(id, seasonNumber, type) }
            _episodes.value = EpisodesState(seasonNumber, content)
        }
    }
}
