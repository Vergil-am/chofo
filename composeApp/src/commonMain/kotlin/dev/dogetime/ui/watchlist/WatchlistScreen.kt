package dev.dogetime.ui.watchlist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.dogetime.domain.MediaItem
import dev.dogetime.domain.WatchStatus
import dev.dogetime.domain.WatchlistEntry
import dev.dogetime.ui.components.ChoicePill
import dev.dogetime.ui.components.MediaGrid
import dev.dogetime.ui.components.dragToScroll

/** The watchlist, one status at a time: the Library's Watchlist section. */
@Composable
fun WatchlistContent(
    entries: List<WatchlistEntry>,
    onOpen: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
    bottomInset: Dp = 0.dp,
) {
    val counts = entries.groupingBy { it.status }.eachCount()
    // Opens on the first status that has something, Watching first.
    var picked by rememberSaveable { mutableStateOf<WatchStatus?>(null) }
    val status = picked ?: WatchStatus.entries.firstOrNull { (counts[it] ?: 0) > 0 } ?: WatchStatus.Watching
    val shown = entries.filter { it.status == status }.map { it.item }

    Column(modifier.fillMaxSize()) {
        val chipsState = rememberLazyListState()
        LazyRow(
            state = chipsState,
            modifier = Modifier.dragToScroll(chipsState),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(WatchStatus.entries, key = { it.name }) { s ->
                val count = counts[s] ?: 0
                ChoicePill(if (count > 0) "${s.label}  $count" else s.label, selected = s == status, onClick = { picked = s })
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (shown.isEmpty()) {
                Text(
                    text = if (entries.isEmpty()) {
                        "Nothing here yet. Add titles from their page with the Watchlist button."
                    } else {
                        "Nothing marked ${status.label.lowercase()}."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center).widthIn(max = 320.dp).padding(16.dp),
                )
            } else {
                MediaGrid(mediaItems = shown, onItemClick = onOpen, modifier = Modifier.fillMaxSize(), bottomInset = bottomInset)
            }
        }
    }
}
