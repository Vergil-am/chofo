package dev.dogetime.core

import dev.dogetime.data.DownloadRepository
import dev.dogetime.data.SubtitleDownloader
import dev.dogetime.domain.DownloadItem
import dev.dogetime.domain.Subtitle

expect class TorrentDownloadManager(
    repository: DownloadRepository,
    subtitleDownloader: SubtitleDownloader,
) {
    val isSupported: Boolean
    val repository: DownloadRepository

    fun startDownload(
        item: DownloadItem,
        selectedSubtitle: Subtitle? = null,
    )

    fun pauseDownload(id: String)
    fun resumeDownload(id: String)
    fun cancelDownload(id: String, deleteFiles: Boolean = true)

    /**
     * Returns [item] with its video and subtitle paths checked against the disk, finding the
     * files again if they moved. Does file I/O, so it suspends and runs off the main thread.
     */
    suspend fun resolveOfflinePlayback(item: DownloadItem): DownloadItem
}
