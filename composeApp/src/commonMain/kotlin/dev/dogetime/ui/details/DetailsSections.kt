package dev.dogetime.ui.details

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.dogetime.Mocha
import dev.dogetime.domain.CastMember
import dev.dogetime.domain.Episode
import dev.dogetime.domain.MediaDetails
import dev.dogetime.domain.MediaItem
import dev.dogetime.domain.MediaType
import dev.dogetime.domain.WatchStatus
import dev.dogetime.domain.WatchTarget
import dev.dogetime.ui.components.GlassIconButton
import dev.dogetime.ui.components.LocalDownloadedEpisodes
import dev.dogetime.ui.components.LocalDownloadedMediaIds
import dev.dogetime.ui.components.MediaRow
import dev.dogetime.ui.components.TitleLogo
import dev.dogetime.ui.components.dragToScroll
import dev.dogetime.ui.components.oneDecimal
import dev.dogetime.ui.player.PlayerIcons

/**
 * Backdrop with poster and title, the action buttons, genres and the overview. With
 * [drawBackdrop] false the hero leaves the backdrop to the caller, and reports its height
 * through [onHeroHeightChanged] so the caller can size it. [heroHeight] controls the
 * backdrop height — on medium+ screens it fills the screen, on narrow/portrait it's 70%.
 * When [includeOverviewInHero] is true the genres, season info, and overview are rendered
 * inside the hero backdrop instead of as separate items below it.
 */
@OptIn(ExperimentalLayoutApi::class)
internal fun LazyListScope.detailsSection(
    details: MediaDetails,
    onWatch: (target: WatchTarget, title: String) -> Unit,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    watchlist: WatchlistControl?,
    playButton: PlayButton? = null,
    drawBackdrop: Boolean = true,
    onHeroHeightChanged: (Int) -> Unit = {},
    heroHeight: Dp = 400.dp,
    includeOverviewInHero: Boolean = false,
    episodeProgress: Map<Pair<Int, Int>, Float> = emptyMap(),
) {
    val media = details.item
    item {
        // Hero: the backdrop fills from the top down to the buttons.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(heroHeight)
                .onSizeChanged { onHeroHeightChanged(it.height) },
        ) {
            if (drawBackdrop) {
                media.backdropUrl?.let { url -> Backdrop(url, media.title, Modifier.matchParentSize()) }
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .statusBarsPadding()
                    .padding(bottom = 16.dp),
            ) {
                // Logo (or title), then one line of facts. The artwork above does the rest.
                Column(modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 18.dp)) {
                    TitleLogo(
                        logoUrl = details.logoUrl,
                        title = media.title,
                        showTextFallback = true,
                        maxWidth = 380.dp,
                        maxHeight = 120.dp,
                    )
                    Spacer(Modifier.height(14.dp))
                    val facts = buildList {
                        media.year?.let(::add)
                        details.runtime?.let(::add)
                        if (media.rating > 0) add("★ ${oneDecimal(media.rating)}")
                        details.seasonCount?.let { add(if (it == 1) "1 season" else "$it seasons") }
                        details.status?.takeIf { it != "Released" }?.let(::add)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (media.id in LocalDownloadedMediaIds.current) {
                            Row(
                                modifier = Modifier
                                    .padding(end = 10.dp)
                                    .background(Mocha.Green.copy(alpha = 0.18f), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 6.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Icon(PlayerIcons.Download, contentDescription = "Downloaded", tint = Mocha.Green, modifier = Modifier.size(11.dp))
                                Text("Downloaded", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Mocha.Green)
                            }
                        }
                        Text(
                            text = facts.joinToString("  •  "),
                            style = MaterialTheme.typography.labelLarge,
                            color = Mocha.Text.copy(alpha = 0.85f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (details.genres.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = details.genres.joinToString("  ·  "),
                            style = MaterialTheme.typography.labelMedium,
                            color = Mocha.Subtext0,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                // Action buttons. On phones the main one gets its own full-width row, so long labels
                // ("Watch Offline (S2E3)", "✓ Plan to watch") never push the others off screen.
                BoxWithConstraints(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 20.dp)) {
                    val narrow = maxWidth < 560.dp
                    val primaryButton: @Composable (Modifier) -> Unit = { buttonModifier ->
                    if (media.type == MediaType.Movie || (media.type == MediaType.Anime && details.seasons.isEmpty())) {
                        val isDownloaded = media.id in LocalDownloadedMediaIds.current
                        Button(
                            onClick = { onWatch(WatchTarget.Movie(media.id, kitsuId = details.kitsuId), media.title) },
                            shape = RoundedCornerShape(50),
                            contentPadding = PaddingValues(horizontal = 28.dp, vertical = 12.dp),
                            modifier = buttonModifier,
                            colors = if (isDownloaded) {
                                ButtonDefaults.buttonColors(
                                    containerColor = Mocha.Green,
                                    contentColor = Mocha.Crust,
                                )
                            } else ButtonDefaults.buttonColors(),
                        ) {
                            Icon(if (isDownloaded) PlayerIcons.Download else PlayIcon, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(if (isDownloaded) "Watch Offline" else "Watch Now", fontWeight = FontWeight.SemiBold)
                        }
                    } else if (details.seasons.isNotEmpty()) {
                        val downloadedEpisodes = LocalDownloadedEpisodes.current
                        val downloadedForShow = downloadedEpisodes.filter { it.first == media.id }
                        // Watch history decides first: the episode in progress, or the next one.
                        // Without any, the earliest downloaded episode, else the first.
                        val firstSeason = details.seasons.map { it.number }.filter { it > 0 }.minOrNull() ?: 1
                        val targetEp = playButton?.let { it.season to it.episode }
                            ?: downloadedForShow.map { it.second }.minWithOrNull(compareBy({ it.first }, { it.second }))
                            ?: (firstSeason to 1)
                        val isDownloaded = (media.id to targetEp) in downloadedEpisodes
                        val epLabel = "S${targetEp.first}E${targetEp.second}"
                        val verb = when (playButton?.kind) {
                            PlayButton.Kind.Continue -> "Continue"
                            PlayButton.Kind.Rewatch -> "Rewatch"
                            else -> "Watch"
                        }
                        val label = "${media.title} $epLabel"
                        Button(
                            onClick = { onWatch(WatchTarget.Episode(media.id, targetEp.first, targetEp.second), label) },
                            shape = RoundedCornerShape(50),
                            contentPadding = PaddingValues(horizontal = 28.dp, vertical = 12.dp),
                            modifier = buttonModifier,
                            colors = if (isDownloaded) {
                                ButtonDefaults.buttonColors(
                                    containerColor = Mocha.Green,
                                    contentColor = Mocha.Crust,
                                )
                            } else ButtonDefaults.buttonColors(),
                        ) {
                            Icon(if (isDownloaded) PlayerIcons.Download else PlayIcon, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (isDownloaded) "$verb Offline ($epLabel)" else "$verb $epLabel",
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                            )
                        }
                    }
                    }
                    val otherButtons: @Composable () -> Unit = {
                    GlassIconButton(
                        icon = if (isFavorite) HeartFilled else HeartOutline,
                        contentDescription = if (isFavorite) "Remove from favorites" else "Add to favorites",
                        onClick = onToggleFavorite,
                        tint = if (isFavorite) Mocha.Red else Mocha.Text,
                    )
                    watchlist?.let { WatchlistButton(it) }
                    }
                    if (narrow) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            primaryButton(Modifier.fillMaxWidth())
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                otherButtons()
                            }
                        }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            primaryButton(Modifier)
                            otherButtons()
                        }
                    }
                }

                // On medium+ widths, genres + overview sit inside the hero backdrop.
                // Genres and seasons are in the facts line above; wide screens add the overview here.
                if (includeOverviewInHero) {
                    Text(
                        text = media.overview.ifBlank { "No overview available." },
                        style = MaterialTheme.typography.bodyMedium,
                        color = Mocha.Subtext0,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 20.dp).widthIn(max = 640.dp),
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }

    // On narrow screens the genres, season info, and overview are separate items below the hero.
    if (!includeOverviewInHero) {
        // Overview with expand/collapse.
        item {
            ExpandableOverview(
                text = media.overview.ifBlank { "No overview available." },
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }
    }
}

/** Shows a 3-line overview with a "Read more" / "Show less" toggle for long text. */
@Composable
private fun ExpandableOverview(text: String, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    var hasOverflow by remember { mutableStateOf(false) }

    Column(modifier = modifier.animateContentSize()) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = Mocha.Subtext0,
            maxLines = if (expanded) Int.MAX_VALUE else 3,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { result ->
                if (!expanded) hasOverflow = result.hasVisualOverflow
            },
        )
        if (hasOverflow || expanded) {
            Text(
                text = if (expanded) "Show less" else "Read more",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(top = 4.dp)
                    .clickable { expanded = !expanded },
            )
        }
    }
}

internal fun LazyListScope.castSection(details: MediaDetails) {
    if (details.cast.isNotEmpty()) {
        item {
            SectionTitle("Cast")
            val castRowState = rememberLazyListState()
            LazyRow(
                state = castRowState,
                modifier = Modifier.dragToScroll(castRowState),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(details.cast, key = { it.name + it.character }) { member ->
                    CastCard(member)
                }
            }
        }
    }
}

/** Recommended and similar titles, as poster rows. Empty lists are left out. */
internal fun LazyListScope.relatedSection(details: MediaDetails, onOpen: (MediaItem) -> Unit) {
    if (details.recommendations.isNotEmpty()) {
        item {
            MediaRow("You might also like", details.recommendations, onOpen, Modifier.padding(top = 24.dp))
        }
    }
    if (details.similar.isNotEmpty()) {
        item {
            MediaRow("Similar titles", details.similar, onOpen, Modifier.padding(top = 24.dp))
        }
    }
}

@Composable
internal fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.padding(start = 20.dp, top = 24.dp, end = 20.dp, bottom = 12.dp),
    )
}

/** Cast member with a circular photo instead of a square poster. */
@Composable
private fun CastCard(member: CastMember) {
    Column(
        modifier = Modifier.width(100.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(88.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (member.photoUrl != null) {
                AsyncImage(
                    model = member.photoUrl,
                    contentDescription = member.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text(
                    text = member.name.take(1),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = member.name,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        if (member.character.isNotBlank()) {
            Text(
                text = member.character,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** The title's watchlist status, and how to change it (null takes it off the list). */
internal class WatchlistControl(val status: WatchStatus?, val onSet: (WatchStatus?) -> Unit)

/** A pill showing the watchlist status; tapping it opens the statuses to pick from. */
@Composable
private fun WatchlistButton(control: WatchlistControl) {
    var open by remember { mutableStateOf(false) }
    val status = control.status
    Box {
        Surface(
            onClick = { open = true },
            shape = RoundedCornerShape(50),
            color = if (status != null) Mocha.Mauve.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.14f),
            contentColor = if (status != null) Mocha.Mauve else Mocha.Text,
            border = BorderStroke(1.dp, if (status != null) Mocha.Mauve.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.18f)),
            modifier = Modifier.height(46.dp),
        ) {
            Box(Modifier.padding(horizontal = 18.dp), contentAlignment = Alignment.Center) {
                Text(
                    text = if (status != null) "✓ ${status.label}" else "+ Watchlist",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            WatchStatus.entries.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Text(
                            if (option == status) "✓ ${option.label}" else option.label,
                            fontWeight = if (option == status) FontWeight.SemiBold else null,
                        )
                    },
                    onClick = {
                        open = false
                        control.onSet(option)
                    },
                )
            }
            if (status != null) {
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text("Remove from watchlist", color = MaterialTheme.colorScheme.error) },
                    onClick = {
                        open = false
                        control.onSet(null)
                    },
                )
            }
        }
    }
}
