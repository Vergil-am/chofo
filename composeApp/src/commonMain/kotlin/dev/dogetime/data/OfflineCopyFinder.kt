package dev.dogetime.data

import dev.dogetime.core.TorrentDownloadManager
import dev.dogetime.domain.DownloadItem
import dev.dogetime.domain.DownloadStatus
import dev.dogetime.domain.MediaType
import dev.dogetime.domain.WatchTarget

/** Finds a finished download to play instead of streaming. */
class OfflineCopyFinder(
    private val downloads: DownloadRepository,
    private val downloadManager: TorrentDownloadManager,
) {
    /**
     * The newest finished download of [target] whose video file is still on disk, with its file
     * paths checked, or null to stream instead. [title] and [type] are the title being watched.
     */
    suspend fun findOfflineCopy(target: WatchTarget, title: String, type: MediaType?): DownloadItem? {
        val match = downloads.getByStatus(DownloadStatus.COMPLETED)
            .firstOrNull { it.isCopyOf(target, title, type) }
            ?: return null
        return downloadManager.resolveOfflinePlayback(match).takeIf { it.videoFilePath != null }
    }
}

/**
 * Whether this download is [target]. Downloads are matched by media id, or by [title] for those
 * saved without one; episodes must also be the same season and episode.
 */
internal fun DownloadItem.isCopyOf(target: WatchTarget, title: String, type: MediaType?): Boolean = when (target) {
    is WatchTarget.Movie ->
        isFor(type, target.id) || (season == null && episode == null && this.title.equals(title, ignoreCase = true))
    is WatchTarget.Episode ->
        (isFor(type, target.showId) || this.title.contains(title, ignoreCase = true)) &&
            season == target.season &&
            episode == target.episode
}

/**
 * Whether this download is of the title with [id]. The type has to match too when both sides know
 * it: anime ids come from Kitsu and TV and movie ids from TMDB, so the same number can be two titles.
 */
private fun DownloadItem.isFor(type: MediaType?, id: Int): Boolean =
    mediaId == id && (type == null || mediaType == null || mediaType == type)
