package dev.dogetime.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import dev.dogetime.Mocha
import dev.dogetime.domain.MediaItem
import dev.dogetime.ui.player.PlayerIcons
import kotlin.math.roundToInt

@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }

@OptIn(ExperimentalSharedTransitionApi::class)
val LocalNavAnimatedVisibilityScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/** Set of media IDs that have completed offline downloads. */
val LocalDownloadedMediaIds = compositionLocalOf<Set<Int>> { emptySet() }

/** Set of (showId to (season to episode)) that have completed offline downloads. */
val LocalDownloadedEpisodes = compositionLocalOf<Set<Pair<Int, Pair<Int, Int>>>> { emptySet() }

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun MediaPoster(
    url: String?,
    title: String,
    modifier: Modifier = Modifier,
    sharedKey: String? = null,
) {
    val sharedScope = LocalSharedTransitionScope.current
    val animScope = LocalNavAnimatedVisibilityScope.current

    val sharedModifier = if (sharedScope != null && animScope != null && sharedKey != null) {
        with(sharedScope) {
            Modifier.sharedElement(
                rememberSharedContentState(key = sharedKey),
                animatedVisibilityScope = animScope,
                clipInOverlayDuringTransition = OverlayClip(RoundedCornerShape(12.dp)),
            )
        }
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .then(sharedModifier)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(8.dp),
            )
        }
    }
}

@Composable
fun MediaCard(item: MediaItem, onClick: (MediaItem) -> Unit, modifier: Modifier = Modifier, showTitle: Boolean = true) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val scale by animateFloatAsState(
        targetValue = if (isHovered) 1.06f else 1f,
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
    )
    val isDownloaded = item.id in LocalDownloadedMediaIds.current

    Column(
        modifier = modifier
            .width(140.dp)
            .zIndex(if (isHovered) 1f else 0f)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .hoverable(interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
            ) { onClick(item) },
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f),
        ) {
            MediaPoster(
                url = item.posterUrl,
                title = item.title,
                sharedKey = "poster-${item.type}-${item.id}",
                modifier = Modifier.fillMaxSize(),
            )
            if (isDownloaded) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Mocha.Green.copy(alpha = 0.92f))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Icon(
                        imageVector = PlayerIcons.Download,
                        contentDescription = "Downloaded",
                        tint = Mocha.Crust,
                        modifier = Modifier.size(10.dp),
                    )
                    Text(
                        text = "Downloaded",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        fontWeight = FontWeight.Bold,
                        color = Mocha.Crust,
                    )
                }
            }
        }
        if (!showTitle) return@Column
        Spacer(Modifier.height(8.dp))
        Text(
            text = item.title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        val meta = listOfNotNull(item.ratingText(), item.year).joinToString(" · ")
        if (meta.isNotEmpty()) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = meta,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** A landscape 16:9 card showing the backdrop with a title overlay at the bottom. */
@Composable
fun BackdropCard(item: MediaItem, onClick: (MediaItem) -> Unit, modifier: Modifier = Modifier) {
    val background = MaterialTheme.colorScheme.background
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val scale by animateFloatAsState(
        targetValue = if (isHovered) 1.06f else 1f,
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
    )
    val isDownloaded = item.id in LocalDownloadedMediaIds.current

    Box(
        modifier = modifier
            .width(260.dp)
            .aspectRatio(16f / 9f)
            .zIndex(if (isHovered) 1f else 0f)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .hoverable(interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
            ) { onClick(item) },
    ) {
        val url = item.backdropUrl ?: item.posterUrl
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (isDownloaded) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Mocha.Green.copy(alpha = 0.92f))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Icon(
                    imageVector = PlayerIcons.Download,
                    contentDescription = "Downloaded",
                    tint = Mocha.Crust,
                    modifier = Modifier.size(11.dp),
                )
                Text(
                    text = "Downloaded",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = Mocha.Crust,
                )
            }
        }
        // Bottom gradient scrim for text readability.
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.5f to Color.Transparent,
                        1f to background.copy(alpha = 0.9f),
                    ),
                ),
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(12.dp),
        ) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = Mocha.Text,
            )
            val meta = listOfNotNull(item.ratingText(), item.year).joinToString(" · ")
            if (meta.isNotEmpty()) {
                Text(
                    text = meta,
                    style = MaterialTheme.typography.labelSmall,
                    color = Mocha.Subtext0,
                )
            }
        }
    }
}

/** A pill-shaped genre tag. */
@Composable
fun GenrePill(genre: String, modifier: Modifier = Modifier) {
    Text(
        text = genre,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier
            .background(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                shape = RoundedCornerShape(50),
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

/**
 * A circular progress ring that fills to represent the rating (0-10 scale),
 * with the numeric score displayed in the centre.
 */
@Composable
fun RatingRing(
    rating: Double,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    strokeWidth: Dp = 4.dp,
) {
    if (rating <= 0) return

    val progress = (rating / 10.0).toFloat().coerceIn(0f, 1f)
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 800),
    )
    val ringColor = when {
        rating >= 7.0 -> Mocha.Green
        rating >= 5.0 -> Mocha.Peach
        else -> Mocha.Red
    }
    val trackColor = ringColor.copy(alpha = 0.2f)
    val score = oneDecimal(rating)

    Box(
        modifier = modifier
            .size(size)
            .background(Mocha.Crust.copy(alpha = 0.7f), CircleShape)
            .drawBehind {
                val stroke = strokeWidth.toPx()
                val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
                val topLeft = Offset(stroke / 2, stroke / 2)
                // Track
                drawArc(
                    color = trackColor,
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
                // Progress
                drawArc(
                    color = ringColor,
                    startAngle = -90f,
                    sweepAngle = animatedProgress * 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = score,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = if (size >= 48.dp) 14.sp else 10.sp,
            ),
            color = Color.White,
        )
    }
}

private val ChevronRightIcon = ImageVector.Builder(
    name = "ChevronRight",
    defaultWidth = 16.dp,
    defaultHeight = 16.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).path(
    stroke = SolidColor(Color.White),
    strokeLineWidth = 2.4f,
    strokeLineCap = StrokeCap.Round,
    strokeLineJoin = StrokeJoin.Round,
) {
    moveTo(9f, 18f)
    lineTo(15f, 12f)
    lineTo(9f, 6f)
}.build()

@Composable
fun MediaRow(
    title: String,
    mediaItems: List<MediaItem>,
    onItemClick: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
    onViewAll: (() -> Unit)? = null,
) {
    Column(modifier = modifier) {
        SectionHeader(title, onSeeAll = onViewAll)
        Spacer(Modifier.height(8.dp))
        val rowState = rememberLazyListState()
        LazyRow(
            state = rowState,
            modifier = Modifier.dragToScroll(rowState),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(mediaItems, key = { "${it.type}-${it.id}" }) { item ->
                MediaCard(item = item, onClick = onItemClick, showTitle = false)
            }
        }
    }
}

/** A row of landscape backdrop cards. */
@Composable
fun BackdropRow(
    title: String,
    mediaItems: List<MediaItem>,
    onItemClick: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Spacer(Modifier.height(8.dp))
        val rowState = rememberLazyListState()
        LazyRow(
            state = rowState,
            modifier = Modifier.dragToScroll(rowState),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(mediaItems, key = { "${it.type}-${it.id}" }) { item ->
                BackdropCard(item = item, onClick = onItemClick)
            }
        }
    }
}

/** Row of recently watched titles with episode coordinates and progress indicators. */
@Composable
fun ContinueWatchingRow(
    title: String,
    historyItems: List<dev.dogetime.domain.WatchHistoryItem>,
    onItemClick: (dev.dogetime.domain.WatchHistoryItem) -> Unit,
    modifier: Modifier = Modifier,
    actions: ContinueWatchingActions? = null,
) {
    Column(modifier = modifier) {
        SectionHeader(title)
        Spacer(Modifier.height(4.dp))
        val rowState = rememberLazyListState()
        LazyRow(
            state = rowState,
            modifier = Modifier.dragToScroll(rowState),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(historyItems, key = { "${it.mediaItem.type}-${it.mediaItem.id}" }) { item ->
                ContinueWatchingCard(item = item, onClick = { onItemClick(item) }, actions = actions)
            }
        }
    }
}

/** What the ⋮ menu on a Continue Watching card offers. */
class ContinueWatchingActions(
    val onDetails: (dev.dogetime.domain.WatchHistoryItem) -> Unit,
    val onStartOver: (dev.dogetime.domain.WatchHistoryItem) -> Unit,
    val onRemove: (dev.dogetime.domain.WatchHistoryItem) -> Unit,
)

@Composable
fun ContinueWatchingCard(
    item: dev.dogetime.domain.WatchHistoryItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    actions: ContinueWatchingActions? = null,
) {
    // The menu opens from the ⋮ button, a long press (touch) or a right click (mouse).
    var menuOpen by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val scale by animateFloatAsState(
        targetValue = if (isHovered) 1.04f else 1f,
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
    )
    val isEpisode = item.season != null && item.episode != null

    Column(
        modifier = modifier
            .width(272.dp)
            .zIndex(if (isHovered) 1f else 0f)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .hoverable(interactionSource)
            .then(
                if (actions == null) Modifier else Modifier.pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.type == PointerEventType.Press && event.buttons.isSecondaryPressed) {
                                event.changes.forEach { it.consume() }
                                menuOpen = true
                            }
                        }
                    }
                },
            )
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                onLongClick = if (actions != null) ({ menuOpen = true }) else null,
            ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            val url = item.mediaItem.backdropUrl ?: item.mediaItem.posterUrl
            if (url != null) {
                AsyncImage(
                    model = url,
                    contentDescription = item.mediaItem.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(0.45f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.85f)),
                ),
            )
            // A play mark in the middle: tapping plays, it doesn't open the page.
            Box(
                Modifier
                    .align(Alignment.Center)
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.45f))
                    .border(1.dp, Color.White.copy(alpha = 0.35f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(PlayerIcons.Play, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
            }
            val isItemDownloaded = if (isEpisode) {
                (item.mediaItem.id to (item.season!! to item.episode!!)) in LocalDownloadedEpisodes.current
            } else {
                item.mediaItem.id in LocalDownloadedMediaIds.current
            }
            if (isItemDownloaded) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Mocha.Green.copy(alpha = 0.92f))
                        .padding(horizontal = 5.dp, vertical = 3.dp),
                ) {
                    Icon(PlayerIcons.Download, contentDescription = "Downloaded", tint = Mocha.Crust, modifier = Modifier.size(11.dp))
                }
            }
            if (actions != null) {
                ContinueWatchingMenu(item, actions, menuOpen, onOpenChange = { menuOpen = it }, modifier = Modifier.align(Alignment.TopEnd).padding(6.dp))
            }
            Column(
                Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Text(
                    text = item.mediaItem.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val remainingMin = ((item.durationMs - item.positionMs) / 60_000L).takeIf { item.durationMs > 0 && item.positionMs > 0 }
                val detail = listOfNotNull(
                    if (isEpisode) "S${item.season} · E${item.episode}" else null,
                    when {
                        remainingMin != null && remainingMin > 0 -> "${remainingMin}m left"
                        item.positionMs == 0L && isEpisode -> "Up next"
                        else -> null
                    },
                ).joinToString("  •  ")
                if (detail.isNotEmpty()) {
                    Text(
                        text = detail,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.75f),
                        maxLines = 1,
                    )
                }
                if (item.progress > 0f) {
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { item.progress },
                        modifier = Modifier.fillMaxWidth().height(3.dp).clip(CircleShape),
                        color = Mocha.Mauve,
                        trackColor = Color.White.copy(alpha = 0.25f),
                        drawStopIndicator = {},
                        gapSize = 0.dp,
                    )
                }
            }
        }
    }
}

@Composable
private fun ContinueWatchingMenu(
    item: dev.dogetime.domain.WatchHistoryItem,
    actions: ContinueWatchingActions,
    open: Boolean,
    onOpenChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier) {
        Box(
            Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.45f))
                .clickable { onOpenChange(true) },
            contentAlignment = Alignment.Center,
        ) {
            Icon(PlayerIcons.More, contentDescription = "More options", tint = Color.White, modifier = Modifier.size(18.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { onOpenChange(false) }) {
            val isEpisode = item.season != null && item.episode != null
            DropdownMenuItem(
                text = { Text(if (isEpisode) "Go to show" else "Go to details") },
                onClick = {
                    onOpenChange(false)
                    actions.onDetails(item)
                },
            )
            DropdownMenuItem(
                text = { Text(if (isEpisode) "Start episode from the beginning" else "Start from the beginning") },
                onClick = {
                    onOpenChange(false)
                    actions.onStartOver(item)
                },
            )
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text("Remove from Continue Watching", color = MaterialTheme.colorScheme.error) },
                onClick = {
                    onOpenChange(false)
                    actions.onRemove(item)
                },
            )
        }
    }
}

/** Adaptive poster grid. [footer] adds extra cells after the items, e.g. a loading spinner. */
@Composable
fun MediaGrid(
    mediaItems: List<MediaItem>,
    onItemClick: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
    gridState: LazyGridState = rememberLazyGridState(),
    bottomInset: Dp = 0.dp,
    footer: LazyGridScope.() -> Unit = {},
) {
    val uniqueItems = remember(mediaItems) {
        mediaItems.distinctBy { "${it.type}-${it.id}" }
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 140.dp),
        state = gridState,
        modifier = modifier,
        contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 16.dp + bottomInset),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(uniqueItems, key = { "${it.type}-${it.id}" }) { item ->
            MediaCard(item = item, onClick = onItemClick)
        }
        footer()
    }
}

@Composable
fun ErrorBox(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = message,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRetry) {
            Text("Retry")
        }
    }
}

@Composable
fun LoadingBox(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

/** "★ 7.8" when the title has a rating, otherwise null. */
fun MediaItem.ratingText(): String? =
    if (rating > 0) "★ ${oneDecimal(rating)}" else null

fun oneDecimal(value: Double): String =
    ((value * 10).roundToInt() / 10.0).toString()

/**
 * A title's logo art, sized to fit [maxWidth] by [maxHeight] without stretching. Without a logo
 * the title is written out instead, once [showTextFallback] says the lookup is done.
 */
@Composable
fun TitleLogo(
    logoUrl: String?,
    title: String,
    showTextFallback: Boolean,
    maxWidth: Dp,
    maxHeight: Dp,
    modifier: Modifier = Modifier,
    textAlign: TextAlign = TextAlign.Start,
    isWide: Boolean = false,
) {
    var failed by remember(logoUrl) { mutableStateOf(false) }
    if (logoUrl != null && !failed) {
        AsyncImage(
            model = logoUrl,
            contentDescription = title,
            contentScale = ContentScale.Fit,
            alignment = if (textAlign == TextAlign.Center) Alignment.Center else Alignment.CenterStart,
            onError = { failed = true },
            modifier = modifier.widthIn(max = maxWidth).heightIn(max = maxHeight).fillMaxWidth().height(maxHeight),
        )
    } else if (showTextFallback || failed) {
        Text(
            text = title,
            style = if (isWide) MaterialTheme.typography.displaySmall else MaterialTheme.typography.headlineMedium,
            color = Mocha.Text,
            textAlign = textAlign,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = modifier.widthIn(max = maxWidth),
        )
    } else {
        Spacer(modifier.height(maxHeight))
    }
}

/** A translucent pill button for use over artwork (e.g. "+ Watchlist" next to the main action). */
@Composable
fun GlassPillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = Color.White.copy(alpha = 0.14f),
        contentColor = Mocha.Text,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** A round translucent icon button for use over artwork. */
@Composable
fun GlassIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Mocha.Text,
    size: Dp = 46.dp,
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = Color.White.copy(alpha = 0.14f),
        contentColor = tint,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
        modifier = modifier.size(size),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = contentDescription, modifier = Modifier.size(size * 0.46f))
        }
    }
}

/** A row's heading, with an optional "See all" on the right. */
@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, onSeeAll: (() -> Unit)? = null) {
    Row(
        modifier = modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = title, style = MaterialTheme.typography.titleLarge, color = Mocha.Text)
        if (onSeeAll != null) {
            TextButton(onClick = onSeeAll) {
                Text("See all", style = MaterialTheme.typography.labelLarge, color = Mocha.Subtext0)
            }
        }
    }
}

/** One option in a row of choices (seasons, add-ons, filters): white when picked, glass otherwise. */
@Composable
fun ChoicePill(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = if (selected) Mocha.Text else Color.White.copy(alpha = 0.08f),
        contentColor = if (selected) Mocha.Crust else Mocha.Text,
        border = if (selected) null else BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
        modifier = modifier,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
        )
    }
}

/** A translucent rounded panel for lists over artwork (stream rows, settings groups). */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    val colors = Color.White.copy(alpha = 0.06f)
    val border = BorderStroke(1.dp, Color.White.copy(alpha = 0.09f))
    if (onClick != null) {
        Surface(onClick = onClick, shape = shape, color = colors, contentColor = Mocha.Text, border = border, modifier = modifier) { content() }
    } else {
        Surface(shape = shape, color = colors, contentColor = Mocha.Text, border = border, modifier = modifier) { content() }
    }
}
