package dev.dogetime.core

import dev.dogetime.data.DownloadRepository
import dev.dogetime.data.SubtitleDownloader
import dev.dogetime.domain.DownloadItem
import dev.dogetime.domain.Subtitle

actual class TorrentDownloadManager(
    actual val repository: DownloadRepository,
    val subtitleDownloader: SubtitleDownloader,
) {
    actual val isSupported: Boolean = false

    actual fun startDownload(item: DownloadItem, selectedSubtitle: Subtitle?) = Unit
    actual fun pauseDownload(id: String) = Unit
    actual fun resumeDownload(id: String) = Unit
    actual fun cancelDownload(id: String, deleteFiles: Boolean) = Unit
    actual fun resolveOfflinePlayback(item: DownloadItem): DownloadItem = item
}
