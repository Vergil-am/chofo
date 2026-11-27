package dev.dogetime.ui.watch

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import dev.dogetime.Mocha
import dev.dogetime.core.UiState
import dev.dogetime.domain.Episode
import dev.dogetime.domain.TorrentOption
import dev.dogetime.domain.WatchTarget
import dev.dogetime.ui.details.episodesSection
import dev.dogetime.ui.player.PlayerIcons

/** About one episode card's height (its 16:9 thumbnail and padding), to scroll the playing one into view. */
private val EpisodeCardHeight = 100.dp

/** A side panel's title and close button. */
@Composable
private fun PanelHeader(title: String, onClose: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        IconButton(onClick = onClose) {
            Icon(PlayerIcons.Close, contentDescription = "Close", tint = Mocha.Text)
        }
    }
}

/**
 * The player's sources panel: the stream list of the chosen add-on, to switch streams without
 * leaving the player. Picking one closes the panel.
 */
@Composable
internal fun SourcesPanel(
    state: WatchUiState,
    onLoad: () -> Unit,
    onSelectProvider: (dev.dogetime.data.addons.StreamAddon) -> Unit,
    onRetry: () -> Unit,
    onPick: (TorrentOption) -> Unit,
    onClose: () -> Unit,
) {
    LaunchedEffect(Unit) { onLoad() }
    Column(Modifier.fillMaxSize()) {
        PanelHeader("Sources", onClose)
        SourcesTab(state, onSelectProvider, onRetry, onPick = { onPick(it); onClose() })
    }
}

/**
 * The player's episodes panel: the show's seasons and episodes, drawn like on its details page,
 * with the playing one marked. Picking another closes the panel and plays it.
 */
@Composable
internal fun EpisodesPanel(
    panel: EpisodePanelState,
    episodeProgress: Map<Pair<Int, Int>, Float>,
    current: WatchTarget.Episode?,
    onLoad: () -> Unit,
    onSelectSeason: (Int) -> Unit,
    onPick: (target: WatchTarget, title: String) -> Unit,
    onClose: () -> Unit,
) {
    LaunchedEffect(Unit) { onLoad() }
    Column(Modifier.fillMaxSize()) {
        PanelHeader("Episodes", onClose)
        when (val details = panel.details) {
            UiState.Loading -> Centered { CircularProgressIndicator() }
            is UiState.Error -> Centered { PanelMessage(details.message, onLoad) }
            is UiState.Success -> {
                val listState = rememberLazyListState()
                val density = LocalDensity.current
                // The episodes are one list item after the season chips: scroll into it to the
                // playing episode, one card above it showing.
                val episodes = (panel.episodes.content as? UiState.Success)?.data
                LaunchedEffect(episodes) {
                    if (episodes != null && current != null) {
                        val index = episodes.indexOfFirst { it.seasonNumber == current.season && it.number == current.episode }
                        if (index > 0) {
                            val offset = with(density) { (EpisodeCardHeight * (index - 1)).roundToPx() }
                            listState.scrollToItem(1, scrollOffset = offset)
                        }
                    }
                }
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
                    episodesSection(
                        details = details.data,
                        episodes = panel.episodes,
                        episodeProgress = episodeProgress,
                        onSeasonSelected = onSelectSeason,
                        onWatch = { target, title -> onPick(target, title); onClose() },
                        playing = current?.let { it.season to it.episode },
                    )
                }
            }
        }
    }
}

@Composable
private fun SourcesTab(
    state: WatchUiState,
    onSelectProvider: (dev.dogetime.data.addons.StreamAddon) -> Unit,
    onRetry: () -> Unit,
    onPick: (TorrentOption) -> Unit,
) {
    val playing = (state.step as? WatchStep.Torrent)?.pick
    val providerName = state.provider?.name ?: "this add-on"
    Column(Modifier.fillMaxSize()) {
        if (state.addons.size > 1) ProviderChips(addons = state.addons, selected = state.provider, onSelect = onSelectProvider)
        when (val torrents = state.torrents) {
            UiState.Loading -> Centered { CircularProgressIndicator() }
            is UiState.Error -> Centered {
                PanelMessage(torrents.message, onRetry)
            }
            is UiState.Success -> if (torrents.data.isEmpty()) {
                Centered { PanelMessage("No streams found on $providerName for this title.", onRetry) }
            } else {
                // Start on the quality of what's playing, so it's in view.
                val playingGroup = remember(torrents.data, playing) {
                    playing?.let { pick -> torrents.data.firstOrNull { pick.isSameStream(it) }?.group }
                }
                var group by remember(state.provider, playingGroup) { mutableStateOf(playingGroup ?: QualityGroup.FullHd) }
                val shown = remember(torrents.data, group) { torrents.data.filter { it.group == group } }
                QualityChips(selected = group, onSelect = { group = it })
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (shown.isEmpty()) {
                        item {
                            Text(
                                text = "No ${group.label} streams on $providerName for this title.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(24.dp),
                            )
                        }
                    }
                    items(shown) { option ->
                        TorrentRow(
                            option = option,
                            onClick = { onPick(option) },
                            isPlaying = playing?.isSameStream(option) == true,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) { content() }
}

@Composable
private fun PanelMessage(text: String, onRetry: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(text, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
        Button(onClick = onRetry) { Text("Try again") }
    }
}
