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
import kotlin.math.abs

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

    VideoPlayerScreen(
        stream = CapturedStream(url = resolved.videoFilePath ?: "", headers = emptyMap()),
        title = resolved.title,
        onBack = onBack,
        onOpenExternal = null,
        target = resolved.watchTarget(),
        mediaItem = resolved.toMediaItem(),
        watchHistory = watchHistory,
        initialSubtitlePath = resolved.subtitleFilePath,
        initialSubtitleLanguage = resolved.subtitleLanguage,
        modifier = modifier,
    )
}

/** The TMDB id, or a stand-in derived from the info hash for downloads that have none. */
private fun DownloadItem.resolvedId(): Int =
    mediaId ?: infoHash.hashCode().let { if (it == Int.MIN_VALUE) 0 else abs(it) }

private fun DownloadItem.watchTarget(): WatchTarget =
    if (season != null && episode != null) {
        WatchTarget.Episode(showId = resolvedId(), season = season, episode = episode)
    } else {
        WatchTarget.Movie(id = resolvedId())
    }

private fun DownloadItem.toMediaItem(): MediaItem = MediaItem(
    id = resolvedId(),
    type = mediaType ?: if (season != null) MediaType.Tv else MediaType.Movie,
    title = title,
    overview = "",
    posterUrl = posterUrl,
    backdropUrl = backdropUrl,
    rating = 0.0,
    year = null,
)
