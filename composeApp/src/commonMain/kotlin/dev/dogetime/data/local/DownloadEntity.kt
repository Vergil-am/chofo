package dev.dogetime.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import dev.dogetime.domain.DownloadItem
import dev.dogetime.domain.DownloadStatus
import dev.dogetime.domain.MediaType

@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    val mediaType: String?,
    val mediaId: Int?,
    val season: Int?,
    val episode: Int?,
    val posterUrl: String?,
    val backdropUrl: String?,
    val magnet: String,
    val infoHash: String,
    val fileName: String?,
    val fileIndex: Int?,
    val quality: String,
    val totalBytes: Long,
    val downloadedBytes: Long,
    val progress: Float,
    val downloadRate: Long,
    val peers: Int,
    val status: String,
    val errorMessage: String?,
    val videoFilePath: String?,
    val subtitleFilePath: String?,
    val subtitleLanguage: String?,
    val createdAt: Long,
) {
    fun toDomain(): DownloadItem = DownloadItem(
        id = id,
        title = title,
        mediaType = mediaType?.let { runCatching { MediaType.valueOf(it) }.getOrNull() },
        mediaId = mediaId,
        season = season,
        episode = episode,
        posterUrl = posterUrl,
        backdropUrl = backdropUrl,
        magnet = magnet,
        infoHash = infoHash,
        fileName = fileName,
        fileIndex = fileIndex,
        quality = quality,
        totalBytes = totalBytes,
        downloadedBytes = downloadedBytes,
        progress = progress,
        downloadRate = downloadRate,
        peers = peers,
        status = runCatching { DownloadStatus.valueOf(status) }.getOrDefault(DownloadStatus.QUEUED),
        errorMessage = errorMessage,
        videoFilePath = videoFilePath,
        subtitleFilePath = subtitleFilePath,
        subtitleLanguage = subtitleLanguage,
        createdAt = createdAt,
    )

    companion object {
        fun fromDomain(item: DownloadItem): DownloadEntity = DownloadEntity(
            id = item.id,
            title = item.title,
            mediaType = item.mediaType?.name,
            mediaId = item.mediaId,
            season = item.season,
            episode = item.episode,
            posterUrl = item.posterUrl,
            backdropUrl = item.backdropUrl,
            magnet = item.magnet,
            infoHash = item.infoHash,
            fileName = item.fileName,
            fileIndex = item.fileIndex,
            quality = item.quality,
            totalBytes = item.totalBytes,
            downloadedBytes = item.downloadedBytes,
            progress = item.progress,
            downloadRate = item.downloadRate,
            peers = item.peers,
            status = item.status.name,
            errorMessage = item.errorMessage,
            videoFilePath = item.videoFilePath,
            subtitleFilePath = item.subtitleFilePath,
            subtitleLanguage = item.subtitleLanguage,
            createdAt = item.createdAt,
        )
    }
}
