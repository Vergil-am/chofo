package dev.dogetime.ui.library

import dev.dogetime.core.TorrentDownloadManager
import dev.dogetime.core.ViewModel
import dev.dogetime.data.FavoritesRepository
import dev.dogetime.data.WatchHistoryRepository
import dev.dogetime.data.WatchlistRepository
import dev.dogetime.domain.DownloadItem
import dev.dogetime.domain.DownloadStatus
import dev.dogetime.domain.MediaItem
import dev.dogetime.domain.MediaType
import dev.dogetime.domain.WatchlistEntry
import dev.dogetime.ui.downloads.DownloadEntry
import dev.dogetime.ui.downloads.withWatchProgress
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

/** The Library's sections. */
enum class LibrarySection(val label: String) {
    Downloads("Downloads"),
    Favorites("Favorites"),
    Watchlist("Watchlist"),
}

/** Every download of one movie or show, episodes in order. */
data class DownloadGroup(
    val key: String,
    val title: String,
    val type: MediaType?,
    val posterUrl: String?,
    val backdropUrl: String?,
    val entries: List<DownloadEntry>,
) {
    val isShow: Boolean get() = type == MediaType.Tv || entries.any { it.item.season != null }
    val totalBytes: Long get() = entries.sumOf { it.item.totalBytes }

    /** Downloads still running, queued or paused. */
    val unfinished: List<DownloadItem> get() = entries.map { it.item }.filter { it.status != DownloadStatus.COMPLETED }

    /** How far the unfinished downloads have got together, from 0 to 1. */
    val unfinishedProgress: Float
        get() = unfinished.takeIf { it.isNotEmpty() }?.let { list -> list.sumOf { it.progress.toDouble() }.toFloat() / list.size } ?: 1f
}

private val EPISODE_SUFFIX = Regex("""\s+S\d+\s*E\d+\s*$""", RegexOption.IGNORE_CASE)

/** "MobLand S1E3" → "MobLand": downloads of episodes are titled after the episode. */
internal fun showTitleOf(title: String): String = title.replace(EPISODE_SUFFIX, "").ifBlank { title }

/**
 * Groups downloads by the movie or show they belong to (by TMDB/Kitsu id, or by title for those
 * without one). Each show's episodes are in season and episode order; the groups come most
 * recently added first.
 */
internal fun groupDownloads(entries: List<DownloadEntry>): List<DownloadGroup> =
    entries
        .groupBy { (item, _) ->
            val id = item.mediaId
            if (id != null) "${item.mediaType ?: if (item.season != null) MediaType.Tv else MediaType.Movie}:$id"
            else "title:${showTitleOf(item.title).lowercase()}"
        }
        .map { (key, group) ->
            val sorted = group.sortedWith(compareBy({ it.item.season ?: 0 }, { it.item.episode ?: 0 }, { it.item.createdAt }))
            val first = sorted.first().item
            DownloadGroup(
                key = key,
                title = showTitleOf(first.title),
                type = first.mediaType ?: if (first.season != null) MediaType.Tv else null,
                posterUrl = sorted.firstNotNullOfOrNull { it.item.posterUrl },
                backdropUrl = sorted.firstNotNullOfOrNull { it.item.backdropUrl },
                entries = sorted,
            )
        }
        .sortedByDescending { group -> group.entries.maxOf { it.item.createdAt } }

/** State behind the Library tab: downloads grouped by title, favorites and the watchlist. */
class LibraryViewModel(
    private val downloadManager: TorrentDownloadManager,
    watchHistory: WatchHistoryRepository,
    favorites: FavoritesRepository,
    watchlist: WatchlistRepository?,
) : ViewModel() {

    private val _section = MutableStateFlow(LibrarySection.Downloads)
    val section: StateFlow<LibrarySection> = _section.asStateFlow()

    private val _openGroup = MutableStateFlow<String?>(null)
    /** The download group shown on its own, by key, or null for the list of groups. */
    val openGroup: StateFlow<String?> = _openGroup.asStateFlow()

    /** Null until the downloads are first read. */
    val downloads: StateFlow<List<DownloadGroup>?> =
        combine(downloadManager.repository.observeAll(), watchHistory.observeAll()) { downloads, history ->
            groupDownloads(withWatchProgress(downloads, history))
        }.stateIn(scope, SharingStarted.Eagerly, initialValue = null)

    val favorites: StateFlow<List<MediaItem>> =
        favorites.observeFavorites().stateIn(scope, SharingStarted.Eagerly, emptyList())

    val watchlist: StateFlow<List<WatchlistEntry>?> =
        (watchlist?.observeAll() ?: flowOf(emptyList())).stateIn(scope, SharingStarted.Eagerly, null)

    fun showSection(section: LibrarySection) {
        _section.value = section
        _openGroup.value = null
    }

    fun openGroup(key: String) {
        _openGroup.value = key
    }

    fun closeGroup() {
        _openGroup.value = null
    }

    fun pause(item: DownloadItem) = downloadManager.pauseDownload(item.id)

    fun resume(item: DownloadItem) = downloadManager.resumeDownload(item.id)

    /** Stops [item] and deletes its files. */
    fun cancel(item: DownloadItem) = downloadManager.cancelDownload(item.id, deleteFiles = true)

    /** Deletes every download of [group]. */
    fun deleteGroup(group: DownloadGroup) {
        group.entries.forEach { cancel(it.item) }
        if (_openGroup.value == group.key) _openGroup.value = null
    }
}
