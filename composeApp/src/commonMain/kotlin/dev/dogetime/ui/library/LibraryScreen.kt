package dev.dogetime.ui.library

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.dogetime.Mocha
import dev.dogetime.core.PlatformBackHandler
import dev.dogetime.domain.DownloadItem
import dev.dogetime.domain.DownloadStatus
import dev.dogetime.domain.MediaItem
import dev.dogetime.ui.components.ChoicePill
import dev.dogetime.ui.components.GlassIconButton
import dev.dogetime.ui.components.MediaGrid
import dev.dogetime.ui.components.MediaPoster
import dev.dogetime.ui.downloads.DownloadCard
import dev.dogetime.ui.player.PlayerIcons
import dev.dogetime.ui.watchlist.WatchlistContent

/** The Library tab: downloads grouped by title, favorites and the watchlist. */
@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    onOpen: (MediaItem) -> Unit,
    onPlayDownload: (DownloadItem) -> Unit,
    modifier: Modifier = Modifier,
    bottomInset: Dp = 0.dp,
) {
    val section by viewModel.section.collectAsState()
    val openKey by viewModel.openGroup.collectAsState()
    val groups by viewModel.downloads.collectAsState()
    val open = openKey?.let { key -> groups?.firstOrNull { it.key == key } }
    PlatformBackHandler(enabled = open != null) { viewModel.closeGroup() }

    if (section == LibrarySection.Downloads && open != null) {
        DownloadGroupScreen(
            group = open,
            onBack = viewModel::closeGroup,
            onPlay = onPlayDownload,
            onPause = viewModel::pause,
            onResume = viewModel::resume,
            onCancel = viewModel::cancel,
            modifier = modifier,
            bottomInset = bottomInset,
        )
        return
    }

    Column(modifier.fillMaxSize()) {
        Text(
            text = "Library",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp),
        )
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LibrarySection.entries.forEach { s ->
                ChoicePill(s.label, selected = s == section, onClick = { viewModel.showSection(s) })
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (section) {
                LibrarySection.Downloads -> {
                    val loaded = groups ?: return@Box
                    if (loaded.isEmpty()) {
                        EmptyNote("No downloads yet. When picking a torrent, tap its download button to save it, with subtitles, for watching offline.")
                    } else {
                        DownloadGroupGrid(loaded, onOpenGroup = { viewModel.openGroup(it.key) }, bottomInset = bottomInset)
                    }
                }
                LibrarySection.Favorites -> {
                    val favorites by viewModel.favorites.collectAsState()
                    if (favorites.isEmpty()) {
                        EmptyNote("No favorites yet. Tap the heart on a title's page to keep it here.")
                    } else {
                        MediaGrid(favorites, onItemClick = onOpen, modifier = Modifier.fillMaxSize(), bottomInset = bottomInset)
                    }
                }
                LibrarySection.Watchlist -> {
                    val entries by viewModel.watchlist.collectAsState()
                    entries?.let { WatchlistContent(it, onOpen = onOpen, bottomInset = bottomInset) }
                }
            }
        }
    }
}

@Composable
private fun EmptyNote(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = Mocha.Subtext0,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 340.dp).padding(24.dp),
        )
    }
}

/** One card per movie or show. */
@Composable
private fun DownloadGroupGrid(groups: List<DownloadGroup>, onOpenGroup: (DownloadGroup) -> Unit, bottomInset: Dp) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 140.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 16.dp + bottomInset),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        items(groups, key = { it.key }) { group -> DownloadGroupCard(group, onClick = { onOpenGroup(group) }) }
    }
}

@Composable
private fun DownloadGroupCard(group: DownloadGroup, onClick: () -> Unit) {
    Column(Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick)) {
        Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f)) {
            MediaPoster(url = group.posterUrl ?: group.backdropUrl, title = group.title, modifier = Modifier.fillMaxSize())
            if (group.isShow) {
                Text(
                    text = "${group.entries.size}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = Mocha.Crust,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .clip(CircleShape)
                        .background(Mocha.Text)
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
            val unfinished = group.unfinished
            if (unfinished.isNotEmpty()) {
                Column(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                ) {
                    val failed = unfinished.any { it.status == DownloadStatus.ERROR }
                    val paused = unfinished.all { it.status == DownloadStatus.PAUSED }
                    Text(
                        text = when {
                            failed -> "Failed"
                            paused -> "Paused · ${(group.unfinishedProgress * 100).toInt()}%"
                            else -> "Downloading · ${(group.unfinishedProgress * 100).toInt()}%"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (failed) MaterialTheme.colorScheme.error else Color.White,
                    )
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { group.unfinishedProgress },
                        modifier = Modifier.fillMaxWidth().height(3.dp).clip(CircleShape),
                        color = Mocha.Mauve,
                        trackColor = Color.White.copy(alpha = 0.25f),
                        drawStopIndicator = {},
                        gapSize = 0.dp,
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(group.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            text = listOfNotNull(
                if (group.isShow) (if (group.entries.size == 1) "1 episode" else "${group.entries.size} episodes") else "Movie",
                group.totalBytes.takeIf { it > 0 }?.let(::formatSize),
            ).joinToString(" · "),
            style = MaterialTheme.typography.labelSmall,
            color = Mocha.Subtext0,
        )
    }
}

/** One title's downloads: a movie's, or a show's episodes under their seasons. */
@Composable
private fun DownloadGroupScreen(
    group: DownloadGroup,
    onBack: () -> Unit,
    onPlay: (DownloadItem) -> Unit,
    onPause: (DownloadItem) -> Unit,
    onResume: (DownloadItem) -> Unit,
    onCancel: (DownloadItem) -> Unit,
    modifier: Modifier,
    bottomInset: Dp,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 24.dp + bottomInset),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                GlassIconButton(icon = PlayerIcons.Back, contentDescription = "Back", onClick = onBack, size = 42.dp)
                Column(Modifier.weight(1f)) {
                    Text(group.title, style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        text = listOfNotNull(
                            if (group.isShow) (if (group.entries.size == 1) "1 episode" else "${group.entries.size} episodes") else null,
                            group.totalBytes.takeIf { it > 0 }?.let(::formatSize),
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.labelMedium,
                        color = Mocha.Subtext0,
                    )
                }
            }
        }
        val bySeason = group.entries.groupBy { it.item.season }
        bySeason.forEach { (season, entries) ->
            if (season != null && group.isShow) {
                item(key = "season-$season") {
                    Text(
                        text = if (season == 0) "Specials" else "Season $season",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 8.dp, start = 4.dp),
                    )
                }
            }
            items(entries, key = { it.item.id }) { (item, progress) ->
                DownloadCard(
                    item = item,
                    watchProgress = progress,
                    onPlay = { onPlay(item) },
                    onPause = { onPause(item) },
                    onResume = { onResume(item) },
                    onCancel = { onCancel(item) },
                    label = item.episode?.takeIf { group.isShow }?.let { "Episode $it" },
                    showThumbnail = !group.isShow,
                )
            }
        }
    }
}

internal fun formatSize(bytes: Long): String = when {
    bytes >= 1L shl 30 -> "${(bytes * 10 / (1L shl 30)) / 10.0} GB"
    bytes >= 1L shl 20 -> "${bytes / (1L shl 20)} MB"
    else -> "${bytes / 1024} KB"
}
