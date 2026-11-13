package dev.dogetime.torrent

internal actual object DownloadNotifier {
    actual fun notifyProgress(
        id: String,
        title: String,
        progress: Float,
        downloadRate: Long,
        peers: Int,
    ) {
        // No-op for desktop
    }

    actual fun notifyCompleted(id: String, title: String) {
        // No-op for desktop
    }

    actual fun notifyFailed(id: String, title: String, error: String?) {
        // No-op for desktop
    }

    actual fun cancel(id: String) {
        // No-op for desktop
    }
}
