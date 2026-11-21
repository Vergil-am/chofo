package dev.dogetime.ui.downloads

import dev.dogetime.core.TorrentDownloadManager
import dev.dogetime.core.ViewModel
import dev.dogetime.data.WatchHistoryRepository
import dev.dogetime.domain.DownloadItem
import dev.dogetime.domain.MediaType
import dev.dogetime.domain.WatchHistoryItem
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** A download, with how far it has been watched if it has watch history. */
data class DownloadEntry(val item: DownloadItem, val watchProgress: WatchHistoryItem?)

/** State behind [DownloadsScreen]: every download with its watch progress, and the actions on them. */
class DownloadsViewModel(
    private val downloadManager: TorrentDownloadManager,
    watchHistory: WatchHistoryRepository,
) : ViewModel() {

    /** Null until the downloads are first read from the database. */
    val downloads: StateFlow<List<DownloadEntry>?> =
        combine(downloadManager.repository.observeAll(), watchHistory.observeAll()) { downloads, history ->
            withWatchProgress(downloads, history)
        }.stateIn(scope, SharingStarted.Eagerly, initialValue = null)

    fun pause(item: DownloadItem) = downloadManager.pauseDownload(item.id)

    fun resume(item: DownloadItem) = downloadManager.resumeDownload(item.id)

    /** Stops [item] and deletes its files. */
    fun cancel(item: DownloadItem) = downloadManager.cancelDownload(item.id, deleteFiles = true)
}

/** Pairs each download with its entry in [history], matched by watch-history key. */
internal fun withWatchProgress(downloads: List<DownloadItem>, history: List<WatchHistoryItem>): List<DownloadEntry> {
    val byKey = history.associateBy { it.watchKey }
    return downloads.map { DownloadEntry(it, it.watchKey()?.let(byKey::get)) }
}

/** The watch-history key for this download, or null when it has no TMDB id (and so no history). */
internal fun DownloadItem.watchKey(): String? {
    val resolvedId = mediaId ?: return null
    val resolvedType = mediaType ?: if (season != null) MediaType.Tv else MediaType.Movie
    return if (season != null && episode != null) {
        "${resolvedType.name}:$resolvedId:$season:$episode"
    } else {
        "${resolvedType.name}:$resolvedId"
    }
}
