package dev.dogetime.torrent

internal expect object DownloadNotifier {
    fun notifyProgress(
        id: String,
        title: String,
        progress: Float,
        downloadRate: Long,
        peers: Int,
    )

    fun notifyCompleted(id: String, title: String)

    fun notifyFailed(id: String, title: String, error: String?)

    fun cancel(id: String)
}
