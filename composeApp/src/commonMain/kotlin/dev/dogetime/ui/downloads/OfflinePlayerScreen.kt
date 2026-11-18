package dev.dogetime.ui.downloads

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import dev.dogetime.core.CapturedStream
import dev.dogetime.core.TorrentDownloadManager
import dev.dogetime.data.WatchHistoryRepository
import dev.dogetime.domain.DownloadItem
import dev.dogetime.domain.MediaItem
import dev.dogetime.domain.MediaType
import dev.dogetime.domain.WatchTarget
import dev.dogetime.ui.player.VideoPlayerScreen

/**
 * Plays a downloaded [item]. Its file paths are checked against the disk once, off the main
 * thread, when the screen opens; a black screen with a spinner shows meanwhile.
 */
@Composable
fun OfflinePlayerScreen(
    item: DownloadItem,
    downloadManager: TorrentDownloadManager,
    watchHistory: WatchHistoryRepository,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Keyed on the item, so recompositions (e.g. other downloads ticking) don't re-run it.
    val playItem by produceState<DownloadItem?>(initialValue = null, item) {
        value = downloadManager.resolveOfflinePlayback(item)
    }

    val resolved = playItem
    if (resolved == null) {
        Box(modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color.White)
        }
        return
    }

    // Watch history is keyed by TMDB id. A download without one (e.g. added from a bare magnet)
    // isn't tracked, rather than saved under a made-up id that could collide with a real title.
    val mediaId = resolved.mediaId
    VideoPlayerScreen(
        stream = CapturedStream(url = resolved.videoFilePath ?: "", headers = emptyMap()),
        title = resolved.title,
        onBack = onBack,
        onOpenExternal = null,
        target = mediaId?.let { resolved.watchTarget(it) },
        mediaItem = mediaId?.let { resolved.toMediaItem(it) },
        watchHistory = watchHistory.takeIf { mediaId != null },
        initialSubtitlePath = resolved.subtitleFilePath,
        initialSubtitleLanguage = resolved.subtitleLanguage,
        modifier = modifier,
    )
}

private fun DownloadItem.watchTarget(mediaId: Int): WatchTarget =
    if (season != null && episode != null) {
        WatchTarget.Episode(showId = mediaId, season = season, episode = episode)
    } else {
        WatchTarget.Movie(id = mediaId)
    }

private fun DownloadItem.toMediaItem(mediaId: Int): MediaItem = MediaItem(
    id = mediaId,
    type = mediaType ?: if (season != null) MediaType.Tv else MediaType.Movie,
    title = title,
    overview = "",
    posterUrl = posterUrl,
    backdropUrl = backdropUrl,
    rating = 0.0,
    year = null,
)
