package dev.dogetime.ui.home

import dev.dogetime.data.NextEpisode
import dev.dogetime.data.NextEpisodeFinder
import kotlinx.coroutines.flow.transformLatest
import dev.dogetime.data.WatchlistRepository
import dev.dogetime.domain.WatchStatus
import kotlinx.coroutines.flow.map
import dev.dogetime.core.ViewModel
import dev.dogetime.data.FavoritesRepository
import dev.dogetime.data.MediaRepository
import dev.dogetime.data.WatchHistoryRepository
import dev.dogetime.domain.MediaItem
import dev.dogetime.domain.MediaType
import dev.dogetime.domain.WatchHistoryItem
import dev.dogetime.domain.popularCategory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the hero shows beyond the card: the title's logo art and its genres. */
data class FeaturedExtras(val logoUrl: String?, val genres: List<String>)

data class HomeContent(
    val popularMovies: List<MediaItem> = emptyList(),
    val popularTv: List<MediaItem> = emptyList(),
    val trendingMovies: List<MediaItem> = emptyList(),
    val trendingTv: List<MediaItem> = emptyList(),
    val trendingAnime: List<MediaItem> = emptyList(),
    val popularAnime: List<MediaItem> = emptyList(),
    val isLoadingPopularMovies: Boolean = false,
    val isLoadingPopularTv: Boolean = false,
    val isLoadingTrendingMovies: Boolean = false,
    val isLoadingTrendingTv: Boolean = false,
    val isLoadingTrendingAnime: Boolean = false,
    val isLoadingPopularAnime: Boolean = false,
    val globalError: String? = null,
) {
    val isAnyLoading: Boolean
        get() = isLoadingPopularMovies || isLoadingPopularTv || isLoadingTrendingMovies ||
            isLoadingTrendingTv || isLoadingTrendingAnime || isLoadingPopularAnime

    val hasAnyContent: Boolean
        get() = popularMovies.isNotEmpty() || popularTv.isNotEmpty() || trendingMovies.isNotEmpty() ||
            trendingTv.isNotEmpty() || trendingAnime.isNotEmpty() || popularAnime.isNotEmpty()
}

/** How many titles the hero pages through. */
const val FEATURED_COUNT = 5

private val ACTIVE_STATUSES = setOf(WatchStatus.Watching, WatchStatus.PlanToWatch, WatchStatus.OnHold)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class HomeViewModel(
    private val repository: MediaRepository,
    favoritesRepository: FavoritesRepository,
    watchHistoryRepository: WatchHistoryRepository? = null,
    /** Pulls watch progress from the other signed-in devices; it lands in the watch history. */
    private val syncWatchHistory: () -> Unit = {},
    private val watchlistRepository: WatchlistRepository? = null,
    private val nextEpisodes: NextEpisodeFinder? = null,
) : ViewModel() {

    private val _featured = MutableStateFlow<Map<String, FeaturedExtras>>(emptyMap())
    /** Logo and genres of the hero's titles, by "type:id", as they arrive. */
    val featured: StateFlow<Map<String, FeaturedExtras>> = _featured.asStateFlow()

    /** "type:id" of every title on the watchlist, for the hero's button. */
    val onWatchlist: StateFlow<Set<String>> = (watchlistRepository?.observeAll() ?: kotlinx.coroutines.flow.flowOf(emptyList()))
        .map { entries -> entries.mapTo(mutableSetOf()) { "${it.item.type}:${it.item.id}" } }
        .stateIn(scope, SharingStarted.Eagerly, initialValue = emptySet())

    /** Adds [item] to Plan to watch, or takes it off the watchlist. */
    fun toggleWatchlist(item: MediaItem) {
        val list = watchlistRepository ?: return
        scope.launch {
            if ("${item.type}:${item.id}" in onWatchlist.value) list.remove(item.type, item.id)
            else list.set(item, WatchStatus.PlanToWatch)
        }
    }

    private fun loadFeatured(items: List<MediaItem>) {
        items.forEach { item ->
            val key = "${item.type}:${item.id}"
            if (key in _featured.value) return@forEach
            scope.launch {
                val details = try {
                    repository.details(item.type, item.id)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    null
                }
                _featured.update { it + (key to FeaturedExtras(details?.logoUrl, details?.genres.orEmpty().take(3))) }
            }
        }
    }

    private val _state = MutableStateFlow(HomeContent())
    val state: StateFlow<HomeContent> = _state.asStateFlow()

    /** Saved favorites, newest first. Updates as soon as one is added or removed. */
    val favorites: StateFlow<List<MediaItem>> = favoritesRepository.observeFavorites()
        .stateIn(scope, SharingStarted.Eagerly, initialValue = emptyList())

    /**
     * Continue Watching, newest first: the latest movie or episode of each title that isn't
     * finished yet. A finished episode is replaced by the next one (at its own progress, or 0%),
     * and a title with nothing left to watch is left out. Shown at once from what's already
     * known, then again once the next episodes have been looked up.
     */
    val watchHistory: StateFlow<List<WatchHistoryItem>> = (watchHistoryRepository?.observeHistory()
        ?: kotlinx.coroutines.flow.flowOf(emptyList()))
        .transformLatest { latest ->
            emit(continueWatching(latest, watchHistoryRepository, lookUp = false))
            if (latest.any { it.isFinished && it.season != null }) {
                emit(continueWatching(latest, watchHistoryRepository, lookUp = true))
            }
        }
        .stateIn(scope, SharingStarted.Eagerly, initialValue = emptyList())

    private suspend fun continueWatching(
        latest: List<WatchHistoryItem>,
        history: WatchHistoryRepository?,
        lookUp: Boolean,
    ): List<WatchHistoryItem> = latest.mapNotNull { item ->
        if (!item.isFinished) return@mapNotNull item
        val season = item.season ?: return@mapNotNull null // a finished movie
        val episode = item.episode ?: return@mapNotNull null
        val finder = nextEpisodes ?: return@mapNotNull null
        val type = item.mediaItem.type
        val next = try {
            if (lookUp) finder.after(type, item.mediaItem.id, season, episode) else finder.cached(type, item.mediaItem.id, season, episode)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            null // offline: left out until it can be looked up
        }
        val number = (next as? NextEpisode.Available)?.episode ?: return@mapNotNull null
        val saved = history?.getHistory(type, item.mediaItem.id, number.season, number.episode)
        // Watched before (e.g. a rewatch from an earlier episode): carry on where that stopped.
        saved?.takeIf { !it.isFinished }?.copy(lastWatchedAt = item.lastWatchedAt)
            ?: item.copy(season = number.season, episode = number.episode, positionMs = 0, durationMs = 0)
    }

    /** What's being watched or planned (not completed or dropped), most recently changed first. */
    val watchlist: StateFlow<List<MediaItem>> = (watchlistRepository?.observeAll() ?: kotlinx.coroutines.flow.flowOf(emptyList()))
        .map { entries -> entries.filter { it.status in ACTIVE_STATUSES }.map { it.item } }
        .stateIn(scope, SharingStarted.Eagerly, initialValue = emptyList())

    init {
        load()
    }

    fun load() {
        syncWatchHistory()
        _state.value = HomeContent(
            isLoadingPopularMovies = true,
            isLoadingPopularTv = true,
            isLoadingTrendingMovies = true,
            isLoadingTrendingTv = true,
            isLoadingTrendingAnime = true,
            isLoadingPopularAnime = true,
        )

        // Launch each fetch in its own independent coroutine so it renders as soon as it arrives
        // without waiting for sibling requests or failing if another request throws an error.
        scope.launch {
            try {
                val items = repository.browse(MediaType.Movie, popularCategory, 1).items
                _state.update { it.copy(popularMovies = items, isLoadingPopularMovies = false) }
                // The hero falls back to popular movies when trending ones don't load.
                if (_state.value.trendingMovies.isEmpty() && !_state.value.isLoadingTrendingMovies) loadFeatured(items.take(FEATURED_COUNT))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                println("Failed to load popular movies: ${e.message}")
                _state.update { it.copy(isLoadingPopularMovies = false) }
                checkAllFailed()
            }
        }

        scope.launch {
            try {
                val items = repository.browse(MediaType.Tv, popularCategory, 1).items
                _state.update { it.copy(popularTv = items, isLoadingPopularTv = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                println("Failed to load popular TV: ${e.message}")
                _state.update { it.copy(isLoadingPopularTv = false) }
                checkAllFailed()
            }
        }

        scope.launch {
            try {
                val items = repository.trending(MediaType.Movie)
                _state.update { it.copy(trendingMovies = items, isLoadingTrendingMovies = false) }
                loadFeatured(items.take(FEATURED_COUNT))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                println("Failed to load trending movies: ${e.message}")
                _state.update { it.copy(isLoadingTrendingMovies = false) }
                checkAllFailed()
            }
        }

        scope.launch {
            try {
                val items = repository.trending(MediaType.Tv)
                _state.update { it.copy(trendingTv = items, isLoadingTrendingTv = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                println("Failed to load trending TV: ${e.message}")
                _state.update { it.copy(isLoadingTrendingTv = false) }
                checkAllFailed()
            }
        }

        scope.launch {
            try {
                val items = repository.trending(MediaType.Anime)
                _state.update { it.copy(trendingAnime = items, isLoadingTrendingAnime = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                println("Failed to load trending anime: ${e.message}")
                _state.update { it.copy(isLoadingTrendingAnime = false) }
                checkAllFailed()
            }
        }

        scope.launch {
            try {
                val items = repository.browse(MediaType.Anime, popularCategory, 1).items
                _state.update { it.copy(popularAnime = items, isLoadingPopularAnime = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                println("Failed to load popular anime: ${e.message}")
                _state.update { it.copy(isLoadingPopularAnime = false) }
                checkAllFailed()
            }
        }
    }

    private fun checkAllFailed() {
        _state.update { current ->
            if (!current.isAnyLoading && !current.hasAnyContent) {
                current.copy(globalError = "Failed to load content. Please check your network connection.")
            } else {
                current
            }
        }
    }
}
