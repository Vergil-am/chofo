package dev.dogetime.ui.details

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.dogetime.Mocha
import dev.dogetime.core.UiState
import dev.dogetime.domain.Episode
import dev.dogetime.domain.MediaDetails
import dev.dogetime.domain.Season
import dev.dogetime.domain.WatchTarget
import dev.dogetime.ui.components.ChoicePill
import dev.dogetime.ui.components.LocalDownloadedEpisodes
import dev.dogetime.ui.components.MediaPoster
import dev.dogetime.ui.components.dragToScroll
import dev.dogetime.ui.components.oneDecimal
import dev.dogetime.ui.player.PlayerIcons

/**
 * Season picker and the selected season's episodes. Nothing for movies. [playing] (season to
 * episode) marks the episode the player has open.
 */
internal fun LazyListScope.episodesSection(
    details: MediaDetails,
    episodes: EpisodesState,
    episodeProgress: Map<Pair<Int, Int>, Float>,
    onSeasonSelected: (Int) -> Unit,
    onWatch: (target: WatchTarget, title: String) -> Unit,
    playing: Pair<Int, Int>? = null,
) {
    if (details.seasons.isEmpty()) return
    item {
        SectionTitle("Seasons")
        SeasonChips(
            seasons = details.seasons,
            selectedSeason = episodes.seasonNumber,
            onSelect = onSeasonSelected,
        )
    }
    item {
        EpisodeList(
            showTitle = details.item.title,
            showId = details.item.id,
            episodes = episodes,
            episodeProgress = episodeProgress,
            onRetry = { episodes.seasonNumber?.let(onSeasonSelected) },
            onWatch = onWatch,
            playing = playing,
        )
    }
}

@Composable
private fun SeasonChips(
    seasons: List<Season>,
    selectedSeason: Int?,
    onSelect: (Int) -> Unit,
) {
    val rowState = rememberLazyListState()
    LazyRow(
        state = rowState,
        modifier = Modifier.dragToScroll(rowState),
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(seasons, key = { it.number }) { season ->
            ChoicePill(season.name, selected = selectedSeason == season.number, onClick = { onSelect(season.number) })
        }
    }
}

@Composable
private fun EpisodeList(
    showTitle: String,
    showId: Int,
    episodes: EpisodesState,
    episodeProgress: Map<Pair<Int, Int>, Float>,
    onRetry: () -> Unit,
    onWatch: (target: WatchTarget, title: String) -> Unit,
    playing: Pair<Int, Int>?,
) {
    when (val content = episodes.content) {
        UiState.Loading -> Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator()
        }
        is UiState.Error -> Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(content.message, style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onRetry) {
                Text("Retry")
            }
        }
        // Rows carry 8dp of padding themselves, so their press highlight has room around the content.
        is UiState.Success -> Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            val downloadedEpisodes = LocalDownloadedEpisodes.current
            content.data.forEach { episode ->
                val progress = episodeProgress[episode.seasonNumber to episode.number]
                val isDownloaded = (showId to (episode.seasonNumber to episode.number)) in downloadedEpisodes
                EpisodeRow(
                    episode = episode,
                    progress = progress,
                    isDownloaded = isDownloaded,
                    isPlaying = playing == (episode.seasonNumber to episode.number),
                    onWatch = {
                        val label = "$showTitle S${episode.seasonNumber}E${episode.number}"
                        onWatch(WatchTarget.Episode(showId, episode.seasonNumber, episode.number), label)
                    },
                )
            }
        }
    }
}

@Composable
private fun EpisodeRow(
    episode: Episode,
    progress: Float? = null,
    isDownloaded: Boolean = false,
    isPlaying: Boolean = false,
    onWatch: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isPlaying) Color.White.copy(alpha = 0.1f) else Color.Transparent)
            .clickable(onClick = onWatch)
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Thumbnail with play overlay and progress bar.
        Box(
            modifier = Modifier
                .width(150.dp)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(10.dp)),
        ) {
            MediaPoster(
                url = episode.stillUrl,
                title = episode.name,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(32.dp)
                    .background(Mocha.Crust.copy(alpha = 0.6f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    PlayIcon,
                    contentDescription = "Play",
                    tint = Mocha.Text,
                    modifier = Modifier.size(14.dp),
                )
            }
            if (isDownloaded) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(4.dp)
                        .background(Mocha.Crust.copy(alpha = 0.85f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 5.dp, vertical = 2.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Icon(
                            PlayerIcons.Download,
                            contentDescription = "Downloaded",
                            tint = Mocha.Green,
                            modifier = Modifier.size(10.dp),
                        )
                        Text(
                            text = "Downloaded",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                            color = Mocha.Green,
                        )
                    }
                }
            }
            if (progress != null && progress > 0f) {
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .align(Alignment.BottomCenter),
                    color = Mocha.Mauve,
                    trackColor = Color.White.copy(alpha = 0.2f),
                    drawStopIndicator = {},
                    gapSize = 0.dp,
                )
            }
        }
        Column(modifier = Modifier.weight(1f).padding(top = 2.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (isPlaying) {
                    Text(
                        text = "Playing",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Mocha.Crust,
                        modifier = Modifier
                            .background(Mocha.Green, RoundedCornerShape(4.dp))
                            .padding(horizontal = 5.dp, vertical = 1.dp),
                    )
                }
                Text(
                    text = "${episode.number}. ${episode.name}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
            val meta = listOfNotNull(
                episode.airDate,
                episode.runtime,
                episode.rating.takeIf { it > 0 }?.let { "★ ${oneDecimal(it)}" },
            ).joinToString(" · ")
            if (meta.isNotEmpty()) {
                Text(
                    text = meta,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (episode.overview.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = episode.overview,
                    style = MaterialTheme.typography.bodySmall,
                    color = Mocha.Subtext0,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
