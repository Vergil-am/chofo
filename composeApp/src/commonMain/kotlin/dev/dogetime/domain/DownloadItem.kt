package dev.dogetime.domain

enum class DownloadStatus {
    QUEUED,
    DOWNLOADING,
    PAUSED,
    COMPLETED,
    ERROR,
}

data class DownloadItem(
    val id: String,
    val title: String,
    val mediaType: MediaType? = null,
    val mediaId: Int? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val posterUrl: String? = null,
    val backdropUrl: String? = null,
    val magnet: String,
    val infoHash: String,
    val fileName: String? = null,
    val fileIndex: Int? = null,
    val quality: String = "Unknown",
    val totalBytes: Long = 0L,
    val downloadedBytes: Long = 0L,
    val progress: Float = 0f,
    val downloadRate: Long = 0L,
    val peers: Int = 0,
    val status: DownloadStatus = DownloadStatus.QUEUED,
    val errorMessage: String? = null,
    val videoFilePath: String? = null,
    val subtitleFilePath: String? = null,
    val subtitleLanguage: String? = null,
    val createdAt: Long = 0L,
)
