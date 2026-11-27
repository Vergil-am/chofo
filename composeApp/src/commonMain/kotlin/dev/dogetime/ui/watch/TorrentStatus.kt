package dev.dogetime.ui.watch

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.dogetime.core.TorrentProgress
import dev.dogetime.ui.components.formatBytes
import dev.dogetime.ui.components.formatSpeed

/** "12 peers · 2.5 MB/s · 37%": how the torrent is doing, over the video. */
@Composable
internal fun TorrentStatsChip(progress: TorrentProgress, modifier: Modifier = Modifier) {
    Text(
        text = torrentStats(progress),
        style = MaterialTheme.typography.labelMedium,
        color = Color.White,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

internal fun torrentStats(progress: TorrentProgress): String {
    val peers = if (progress.peers == 1) "1 peer" else "${progress.peers} peers"
    val percent = (progress.progress * 100).toInt().coerceIn(0, 100)
    return "$peers · ${formatSpeed(progress.downloadRate)} · $percent%"
}

/** How many bytes to download before playing: [bufferMb], or the whole file if it is smaller. */
internal fun bufferTarget(bufferMb: Int, progress: TorrentProgress): Long {
    val wanted = bufferMb * 1024L * 1024L
    return if (progress.size > 0) minOf(wanted, progress.size) else wanted
}

/** Waiting for [bufferMb] of the torrent before the player opens; [onPlayNow] skips the wait. */
@Composable
internal fun TorrentBuffering(progress: TorrentProgress, bufferMb: Int, onPlayNow: () -> Unit) {
    val target = bufferTarget(bufferMb, progress)
    val buffered = progress.bufferedBytes.coerceAtMost(target)
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        Text("Buffering ${formatBytes(buffered)} of ${formatBytes(target)}…", textAlign = TextAlign.Center)
        LinearProgressIndicator(
            progress = { if (target > 0) buffered.toFloat() / target else 0f },
            modifier = Modifier.widthIn(max = 320.dp).fillMaxWidth(),
        )
        Text(
            torrentStats(progress),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = onPlayNow) { Text("Play now") }
    }
}
