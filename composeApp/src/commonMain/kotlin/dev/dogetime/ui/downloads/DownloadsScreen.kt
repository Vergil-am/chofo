package dev.dogetime.ui.downloads

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.dogetime.domain.FINISHED_FRACTION
import dev.dogetime.domain.DownloadItem
import dev.dogetime.domain.DownloadStatus
import dev.dogetime.domain.WatchHistoryItem
import dev.dogetime.ui.player.PlayerIcons

/** One download with its progress and actions: play, pause or resume, cancel or delete. */
@Composable
internal fun DownloadCard(
    item: DownloadItem,
    watchProgress: WatchHistoryItem? = null,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    /** Shown instead of the download's title, e.g. "Episode 3" inside its show. */
    label: String? = null,
    showThumbnail: Boolean = true,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f),
        ),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Thumbnail
                val imageUrl = item.posterUrl ?: item.backdropUrl
                if (!showThumbnail) {
                    // Inside its show: the poster is the same for every episode.
                } else if (imageUrl != null) {
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = item.title,
                        modifier = Modifier
                            .size(width = 60.dp, height = 80.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(width = 60.dp, height = 80.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = PlayerIcons.Play,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }

                // Info
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = label ?: item.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = item.quality,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.primary)
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        )

                        if (!item.subtitleLanguage.isNullOrBlank() || item.subtitleFilePath != null) {
                            Text(
                                text = "CC ${item.subtitleLanguage ?: "Subtitles"}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }

                    item.fileName?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            // Status & Progress Bar
            when (item.status) {
                DownloadStatus.DOWNLOADING -> {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        LinearProgressIndicator(
                            progress = { item.progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "${(item.progress * 100).toInt()}% · ${formatBytes(item.downloadedBytes)} / ${formatBytes(item.totalBytes)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = "${formatSpeed(item.downloadRate)} · ${item.peers} peers",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedButton(
                            onClick = onPause,
                            shape = RoundedCornerShape(10.dp),
                        ) {
                            Icon(PlayerIcons.Pause, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Pause")
                        }
                        OutlinedButton(
                            onClick = onCancel,
                            shape = RoundedCornerShape(10.dp),
                        ) {
                            Text("Cancel")
                        }
                    }
                }

                DownloadStatus.PAUSED -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "${(item.progress * 100).toInt()}% · Paused",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = onResume,
                                shape = RoundedCornerShape(10.dp),
                            ) {
                                Icon(PlayerIcons.Play, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Resume")
                            }
                            OutlinedButton(
                                onClick = onCancel,
                                shape = RoundedCornerShape(10.dp),
                            ) {
                                Text("Delete")
                            }
                        }
                    }
                }

                DownloadStatus.COMPLETED -> {
                    val progress = watchProgress?.progress ?: 0f
                    val isFinished = progress >= FINISHED_FRACTION
                    val isPartiallyWatched = progress >= 0.01f && !isFinished

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (isPartiallyWatched) {
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp)),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                            )
                        }

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            val statusText = when {
                                isFinished -> "${formatBytes(item.totalBytes)} · Watched"
                                isPartiallyWatched -> "${formatBytes(item.totalBytes)} · ${(progress * 100).toInt()}% watched"
                                else -> "${formatBytes(item.totalBytes)} · Ready to play offline"
                            }
                            val statusColor = when {
                                isFinished || !isPartiallyWatched -> Color(0xFFA6E3A1)
                                else -> MaterialTheme.colorScheme.primary
                            }

                            Text(
                                text = statusText,
                                style = MaterialTheme.typography.bodySmall,
                                color = statusColor,
                                fontWeight = FontWeight.Medium,
                            )
                            // Under the status, so both buttons keep their size on narrow screens.
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                Button(
                                    onClick = onPlay,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Icon(
                                        imageVector = if (isFinished) PlayerIcons.Replay else PlayerIcons.Play,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        when {
                                            isFinished -> "Watch Again"
                                            isPartiallyWatched -> "Resume"
                                            else -> "Play Offline"
                                        }
                                    )
                                }
                                OutlinedButton(
                                    onClick = onCancel,
                                    shape = RoundedCornerShape(10.dp),
                                ) {
                                    Text("Delete")
                                }
                            }
                        }
                    }
                }

                DownloadStatus.ERROR -> {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = item.errorMessage ?: "Download encountered an error.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Button(
                                onClick = onResume,
                                shape = RoundedCornerShape(10.dp),
                            ) {
                                Icon(PlayerIcons.Replay, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Retry")
                            }
                            OutlinedButton(
                                onClick = onCancel,
                                shape = RoundedCornerShape(10.dp),
                            ) {
                                Text("Delete")
                            }
                        }
                    }
                }

                DownloadStatus.QUEUED -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Queued...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OutlinedButton(
                            onClick = onCancel,
                            shape = RoundedCornerShape(10.dp),
                        ) {
                            Text("Cancel")
                        }
                    }
                }
            }
        }
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes <= 0L -> "0 B"
    bytes < 1024L -> "$bytes B"
    bytes < 1024L * 1024L -> "${(bytes / 1024.0).toInt()} KB"
    bytes < 1024L * 1024L * 1024L -> "${(bytes / (1024.0 * 1024.0)).format(1)} MB"
    else -> "${(bytes / (1024.0 * 1024.0 * 1024.0)).format(2)} GB"
}

private fun formatSpeed(bytesPerSec: Long): String = when {
    bytesPerSec <= 0L -> "0 KB/s"
    bytesPerSec < 1024L * 1024L -> "${(bytesPerSec / 1024.0).toInt()} KB/s"
    else -> "${(bytesPerSec / (1024.0 * 1024.0)).format(1)} MB/s"
}

private fun Double.format(decimals: Int): String {
    val factor = when (decimals) {
        1 -> 10.0
        2 -> 100.0
        else -> 1.0
    }
    val rounded = (this * factor).toLong() / factor
    return rounded.toString()
}
