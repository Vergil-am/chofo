package dev.dogetime.ui.watch

import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.ButtonDefaults
import dev.dogetime.domain.RememberedStream
import dev.dogetime.Mocha
import androidx.compose.ui.text.font.FontWeight
import dev.dogetime.ui.components.GlassIconButton
import dev.dogetime.ui.components.GlassCard
import dev.dogetime.ui.components.ChoicePill
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.dogetime.core.UiState
import dev.dogetime.data.addons.StreamAddon
import dev.dogetime.domain.Subtitle
import dev.dogetime.domain.TorrentOption
import dev.dogetime.ui.components.dragToScroll
import dev.dogetime.ui.player.PlayerIcons

/** The quality groups the torrent list is filtered by. */
private enum class QualityGroup(val label: String) {
    UltraHd("4K"),
    FullHd("Full HD"),
    Hd("HD"),
    Sd("SD"),
    Unknown("Unknown"),
}

/** Groups quality labels: "4k HDR" is 4K, "720p" is HD, "480p" is SD, and "DVDRip" or no label is Unknown. */
private val TorrentOption.group: QualityGroup
    get() = when (quality.substringBefore(' ').lowercase()) {
        "4k", "2160p" -> QualityGroup.UltraHd
        "1080p" -> QualityGroup.FullHd
        "720p" -> QualityGroup.Hd
        "576p", "480p", "360p", "240p" -> QualityGroup.Sd
        else -> QualityGroup.Unknown
    }

/**
 * The [torrents] [provider] lists for the title, best quality first, filtered by [QualityGroup]
 * (Full HD at first); picking one plays it. With [onChooseDownload], each row also has a download
 * button, which opens the download dialog for [downloadChoice].
 */
@Composable
internal fun TorrentPicker(
    title: String,
    backdropUrl: String?,
    addons: List<StreamAddon>,
    provider: StreamAddon?,
    torrents: UiState<List<TorrentOption>>,
    downloadChoice: DownloadChoice?,
    onSelectProvider: (StreamAddon) -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    onPick: (TorrentOption) -> Unit,
    onChooseDownload: ((TorrentOption) -> Unit)?,
    onDismissDownload: () -> Unit,
    onDownload: (TorrentOption, Subtitle?) -> Unit,
    modifier: Modifier = Modifier,
    /** The stream last played for this title, offered at the top to carry on with. */
    remembered: RememberedStream? = null,
    isResuming: Boolean = false,
    onResume: () -> Unit = {},
) {
    var group by remember(provider) { mutableStateOf(QualityGroup.FullHd) }
    val providerName = provider?.name ?: "this add-on"

    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (backdropUrl != null) BlurredBackdrop(backdropUrl, Modifier.fillMaxSize())
        WatchPage(title = title, pageShown = false, onBack = onBack) {
            Column(Modifier.fillMaxSize()) {
                if (remembered != null) ContinueRow(remembered, isResuming, onResume)
                ProviderChips(addons = addons, selected = provider, onSelect = onSelectProvider)
                when (val state = torrents) {
                    UiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    is UiState.Error -> Message(state.message, onRetry = onRetry)
                    is UiState.Success -> if (state.data.isEmpty()) {
                        Message("No streams found on $providerName for this title.", onRetry = onRetry)
                    } else {
                        val shown = remember(state.data, group) { state.data.filter { it.group == group } }
                        QualityChips(selected = group, onSelect = { group = it })
                        if (shown.isEmpty()) {
                            Text(
                                text = "No ${group.label} streams on $providerName for this title.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(24.dp),
                            )
                        }
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            items(shown) { option ->
                                TorrentRow(
                                    option = option,
                                    onClick = { onPick(option) },
                                    // Direct links play, but only torrents can be downloaded.
                                    onDownloadClick = onChooseDownload?.takeIf { option.isTorrent }?.let { choose -> { choose(option) } },
                                )
                            }
                        }
                    }
                }
            }
        }

        downloadChoice?.let { choice ->
            DownloadTorrentDialog(
                choice = choice,
                title = title,
                onDismissRequest = onDismissDownload,
                onConfirm = onDownload,
            )
        }
    }
}

@Composable
private fun ProviderChips(addons: List<StreamAddon>, selected: StreamAddon?, onSelect: (StreamAddon) -> Unit) {
    val rowState = rememberLazyListState()
    LazyRow(
        state = rowState,
        modifier = Modifier.fillMaxWidth().dragToScroll(rowState),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        items(addons, key = { it.id }) { provider ->
            ChoicePill(provider.name, selected = provider.id == selected?.id, onClick = { onSelect(provider) })
        }
    }
}

@Composable
private fun QualityChips(selected: QualityGroup, onSelect: (QualityGroup) -> Unit) {
    val rowState = rememberLazyListState()
    LazyRow(
        state = rowState,
        modifier = Modifier.fillMaxWidth().dragToScroll(rowState),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        items(QualityGroup.entries) { group ->
            ChoicePill(group.label, selected = group == selected, onClick = { onSelect(group) })
        }
    }
}

@Composable
private fun TorrentRow(
    option: TorrentOption,
    onClick: () -> Unit,
    onDownloadClick: (() -> Unit)? = null,
) {
    GlassCard(onClick = onClick, modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth()) {
    Row(
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = option.quality,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = Mocha.Crust,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (option.group == QualityGroup.UltraHd) Mocha.Peach else Mocha.Text)
                        .padding(horizontal = 7.dp, vertical = 2.dp),
                )
                Text(
                    text = listOfNotNull(
                        if (option.isTorrent) null else "Direct link",
                        option.seeders?.let { if (it == 1) "1 seeder" else "$it seeders" },
                        option.size?.takeIf { it.isNotBlank() },
                        option.site,
                    ).joinToString("  •  "),
                    style = MaterialTheme.typography.labelMedium,
                    color = Mocha.Subtext0,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = option.release,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            // A season pack: say which file in it is this episode.
            option.fileName?.takeIf { it.substringBeforeLast('.') != option.release.substringBeforeLast('.') }?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (onDownloadClick != null) {
            GlassIconButton(
                icon = PlayerIcons.Download,
                contentDescription = "Download torrent with subtitles",
                onClick = onDownloadClick,
                size = 40.dp,
            )
        }
    }
    }
}

@Composable
private fun Message(text: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        Text(text, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 360.dp))
        Button(onClick = onRetry) {
            Text("Try again")
        }
    }
}

/** "Continue with the same stream": the one last played for this title, here or on another device. */
@Composable
private fun ContinueRow(remembered: RememberedStream, isResuming: Boolean, onResume: () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 4.dp), contentAlignment = Alignment.Center) {
        Button(
            onClick = onResume,
            enabled = !isResuming,
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Mocha.Text, contentColor = Mocha.Crust),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth(),
        ) {
            if (isResuming) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Mocha.Crust)
            } else {
                Icon(PlayerIcons.Play, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Continue with the same stream", style = MaterialTheme.typography.titleSmall, maxLines = 1)
                    Text(
                        text = listOfNotNull(remembered.label?.takeIf { it.isNotBlank() }, remembered.fileName).joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
