package dev.dogetime.ui.home

import dev.dogetime.ui.components.windowWideImageUrl
import androidx.compose.foundation.layout.FlowRow
import dev.dogetime.ui.player.PlayerIcons
import androidx.compose.material3.Button
import dev.dogetime.ui.components.oneDecimal
import dev.dogetime.ui.components.GlassPillButton
import dev.dogetime.ui.components.TitleLogo
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.dogetime.Mocha
import dev.dogetime.core.UiState
import dev.dogetime.domain.BrowseCategory
import dev.dogetime.domain.MediaItem
import dev.dogetime.domain.MediaType
import dev.dogetime.domain.WatchHistoryItem
import dev.dogetime.ui.components.ContinueWatchingActions
import dev.dogetime.domain.browseCategories
import dev.dogetime.domain.popularCategory
import dev.dogetime.ui.components.ErrorBox
import dev.dogetime.ui.components.LoadingBox
import dev.dogetime.ui.components.MediaRow
import dev.dogetime.ui.components.RatingRing
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

import dev.dogetime.ui.components.ContinueWatchingRow

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onOpen: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
    bottomInset: Dp = 0.dp,
    isWide: Boolean? = null,
    onNavigateToBrowse: (MediaType, BrowseCategory?) -> Unit = { _, _ -> },
    onOpenWatchlist: () -> Unit = {},
    /** A Continue Watching card: plays that movie or episode again. */
    onContinue: (WatchHistoryItem) -> Unit = { onOpen(it.mediaItem) },
    /** The ⋮ menu on each Continue Watching card. */
    continueActions: ContinueWatchingActions? = null,
    /** Room for the side rail: the hero runs under it, everything else starts after it. */
    startInset: Dp = 0.dp,
) {
    val content by viewModel.state.collectAsState()
    val watchlist by viewModel.watchlist.collectAsState()
    val favorites by viewModel.favorites.collectAsState()
    val watchHistory by viewModel.watchHistory.collectAsState()
    val featuredExtras by viewModel.featured.collectAsState()
    val onWatchlist by viewModel.onWatchlist.collectAsState()

    if (!content.hasAnyContent && content.isAnyLoading) {
        LoadingBox(modifier)
    } else if (!content.hasAnyContent && content.globalError != null) {
        ErrorBox(content.globalError ?: "Failed to load content", onRetry = viewModel::load, modifier = modifier)
    } else {
        BoxWithConstraints(modifier = modifier.fillMaxSize()) {
            val wide = isWide ?: (maxWidth > 839.dp)
            val rowModifier = Modifier.padding(start = startInset)
            // Immersive: the hero takes most of the first screen, under the status bar.
            val bannerHeight = if (wide) maxHeight * 0.86f else (maxHeight * 0.72f).coerceAtLeast(520.dp)

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp + bottomInset),
                verticalArrangement = Arrangement.spacedBy(30.dp),
            ) {
                // Featured hero banner — cycles through trending movies if available, else popular movies
                val featured = (content.trendingMovies.ifEmpty { content.popularMovies }).take(FEATURED_COUNT)
                if (featured.isNotEmpty()) {
                    item {
                        FeaturedBanner(
                            items = featured,
                            onOpen = onOpen,
                            extras = featuredExtras,
                            onWatchlist = onWatchlist,
                            onToggleWatchlist = viewModel::toggleWatchlist,
                            bannerHeight = bannerHeight,
                            startInset = startInset,
                            bottomInset = bottomInset,
                            isWide = wide,
                        )
                    }
                }

            if (watchHistory.isNotEmpty()) {
                item {
                    ContinueWatchingRow("Continue Watching", watchHistory, onContinue, modifier = rowModifier, actions = continueActions)
                }
            }

            if (watchlist.isNotEmpty()) {
                item {
                    MediaRow("Your Watchlist", watchlist, onOpen, modifier = rowModifier, onViewAll = onOpenWatchlist)
                }
            }

            if (favorites.isNotEmpty()) {
                item {
                    MediaRow("Your Favorites", favorites, onOpen, modifier = rowModifier)
                }
            }
            if (content.trendingMovies.isNotEmpty()) {
                item {
                    val cat = remember { browseCategories(MediaType.Movie).firstOrNull { it.path == "now_playing" } ?: popularCategory }
                    MediaRow(
                        title = "Trending Movies",
                        mediaItems = content.trendingMovies,
                        onItemClick = onOpen,
                        modifier = rowModifier,
                        onViewAll = { onNavigateToBrowse(MediaType.Movie, cat) },
                    )
                }
            }
            if (content.trendingTv.isNotEmpty()) {
                item {
                    val cat = remember { browseCategories(MediaType.Tv).firstOrNull { it.path == "on_the_air" } ?: popularCategory }
                    MediaRow(
                        title = "Trending TV Shows",
                        mediaItems = content.trendingTv,
                        onItemClick = onOpen,
                        modifier = rowModifier,
                        onViewAll = { onNavigateToBrowse(MediaType.Tv, cat) },
                    )
                }
            }
            if (content.popularMovies.isNotEmpty()) {
                item {
                    MediaRow(
                        title = "Popular Movies",
                        mediaItems = content.popularMovies,
                        onItemClick = onOpen,
                        modifier = rowModifier,
                        onViewAll = { onNavigateToBrowse(MediaType.Movie, popularCategory) },
                    )
                }
            }
            if (content.popularTv.isNotEmpty()) {
                item {
                    MediaRow(
                        title = "Popular TV Shows",
                        mediaItems = content.popularTv,
                        onItemClick = onOpen,
                        modifier = rowModifier,
                        onViewAll = { onNavigateToBrowse(MediaType.Tv, popularCategory) },
                    )
                }
            }
            if (content.trendingAnime.isNotEmpty()) {
                item {
                    val cat = remember { browseCategories(MediaType.Anime).firstOrNull { it.path == "current" } ?: popularCategory }
                    MediaRow(
                        title = "Trending Anime",
                        mediaItems = content.trendingAnime,
                        onItemClick = onOpen,
                        modifier = rowModifier,
                        onViewAll = { onNavigateToBrowse(MediaType.Anime, cat) },
                    )
                }
            }
            if (content.popularAnime.isNotEmpty()) {
                item {
                    MediaRow(
                        title = "Popular Anime",
                        mediaItems = content.popularAnime,
                        onItemClick = onOpen,
                        modifier = rowModifier,
                        onViewAll = { onNavigateToBrowse(MediaType.Anime, popularCategory) },
                    )
                }
            }
            item { Spacer(Modifier.height(8.dp)) }
            }
        }
    }
}

/**
 * Full-width hero banner that auto-pages through [items], showing the backdrop,
 * a gradient scrim, the title, rating ring, year, overview snippet, and dot indicators.
 * Supports mouse drag and scroll-wheel on desktop.
 */
private val ChevronLeftIcon = ImageVector.Builder(
    name = "ChevronLeft",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).path(
    stroke = SolidColor(Color.White),
    strokeLineWidth = 2.2f,
    strokeLineCap = StrokeCap.Round,
    strokeLineJoin = StrokeJoin.Round,
) {
    moveTo(15f, 18f)
    lineTo(9f, 12f)
    lineTo(15f, 6f)
}.build()

private val ChevronRightIcon = ImageVector.Builder(
    name = "ChevronRight",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).path(
    stroke = SolidColor(Color.White),
    strokeLineWidth = 2.2f,
    strokeLineCap = StrokeCap.Round,
    strokeLineJoin = StrokeJoin.Round,
) {
    moveTo(9f, 18f)
    lineTo(15f, 12f)
    lineTo(9f, 6f)
}.build()

@Composable
private fun FeaturedBanner(
    items: List<MediaItem>,
    onOpen: (MediaItem) -> Unit,
    extras: Map<String, FeaturedExtras>,
    onWatchlist: Set<String>,
    onToggleWatchlist: (MediaItem) -> Unit,
    bannerHeight: Dp = 420.dp,
    startInset: Dp = 0.dp,
    bottomInset: Dp = 0.dp,
    isWide: Boolean = false,
) {
    val pagerState = rememberPagerState(pageCount = { items.size })
    // A short hero (landscape phones): no overview or arrows, a smaller logo.
    val compact = bannerHeight < 480.dp
    val background = MaterialTheme.colorScheme.background
    val scope = rememberCoroutineScope()

    // Auto-advance every 6 seconds.
    LaunchedEffect(pagerState) {
        while (true) {
            delay(6_000)
            val next = (pagerState.currentPage + 1) % items.size
            pagerState.animateScrollToPage(next, animationSpec = tween(600))
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(bannerHeight),
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                // Mouse click-and-drag → swipe between pages.
                .pointerInput(pagerState, items.size) {
                    var totalDrag = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { totalDrag = 0f },
                        onDragEnd = {
                            val threshold = size.width * 0.15f
                            val target = when {
                                totalDrag < -threshold ->
                                    (pagerState.currentPage + 1).coerceAtMost(items.size - 1)
                                totalDrag > threshold ->
                                    (pagerState.currentPage - 1).coerceAtLeast(0)
                                else -> pagerState.currentPage
                            }
                            scope.launch {
                                pagerState.animateScrollToPage(target, animationSpec = tween(400))
                            }
                        },
                        onDragCancel = { totalDrag = 0f },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            totalDrag += dragAmount
                        },
                    )
                }
                // Mouse scroll-wheel → page forward / backward.
                .pointerInput(pagerState, items.size) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.type == PointerEventType.Scroll) {
                                val scrollY = event.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                                if (scrollY != 0f) {
                                    val target = if (scrollY > 0) {
                                        (pagerState.currentPage + 1).coerceAtMost(items.size - 1)
                                    } else {
                                        (pagerState.currentPage - 1).coerceAtLeast(0)
                                    }
                                    if (target != pagerState.currentPage) {
                                        scope.launch {
                                            pagerState.animateScrollToPage(
                                                target,
                                                animationSpec = tween(400),
                                            )
                                        }
                                    }
                                    event.changes.forEach { it.consume() }
                                }
                            }
                        }
                    }
                },
        ) { page ->
            val item = items[page]
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onOpen(item) },
            ) {
                // Backdrop image.
                val url = item.backdropUrl ?: item.posterUrl
                if (url != null) {
                    AsyncImage(
                        model = windowWideImageUrl(url),
                        contentDescription = item.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                // Cinematic gradient overlay.
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0f to background.copy(alpha = 0.45f),
                                0.18f to Color.Transparent,
                                0.5f to Color.Transparent,
                                0.78f to background.copy(alpha = 0.75f),
                                1f to background,
                            ),
                        ),
                )
                if (isWide) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(
                                Brush.horizontalGradient(
                                    0f to background.copy(alpha = 0.9f),
                                    0.35f to background.copy(alpha = 0.55f),
                                    0.65f to Color.Transparent,
                                    1f to Color.Transparent,
                                ),
                            ),
                    )
                }

                HeroInfo(
                    item = item,
                    extras = extras["${item.type}:${item.id}"],
                    isOnWatchlist = "${item.type}:${item.id}" in onWatchlist,
                    onOpen = { onOpen(item) },
                    onToggleWatchlist = { onToggleWatchlist(item) },
                    isWide = isWide,
                    compact = compact,
                    modifier = Modifier
                        .align(if (isWide) Alignment.BottomStart else Alignment.BottomCenter)
                        .padding(start = startInset + if (isWide) 40.dp else 24.dp, end = 24.dp, bottom = if (compact) 36.dp else if (isWide) 64.dp else 44.dp),
                )
            }
        }

        if (isWide && !compact && items.size > 1) {
            IconButton(
                onClick = {
                    scope.launch {
                        val prev = (pagerState.currentPage - 1 + items.size) % items.size
                        pagerState.animateScrollToPage(prev, animationSpec = tween(400))
                    }
                },
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = startInset + 16.dp)
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Mocha.Crust.copy(alpha = 0.55f)),
            ) {
                Icon(ChevronLeftIcon, contentDescription = "Previous", tint = Mocha.Text)
            }

            IconButton(
                onClick = {
                    scope.launch {
                        val next = (pagerState.currentPage + 1) % items.size
                        pagerState.animateScrollToPage(next, animationSpec = tween(400))
                    }
                },
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 16.dp)
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Mocha.Crust.copy(alpha = 0.55f)),
            ) {
                Icon(ChevronRightIcon, contentDescription = "Next", tint = Mocha.Text)
            }
        }

        // Indicators: selected as pill, unselected as dots.
        val dotsBottomPadding = if (isWide) 16.dp else 12.dp
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = dotsBottomPadding),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(items.size) { index ->
                val isSelected = pagerState.currentPage == index
                val color by animateColorAsState(
                    targetValue = if (isSelected) Mocha.Text else Mocha.Text.copy(alpha = 0.3f),
                    animationSpec = tween(300),
                )
                val width by animateDpAsState(
                    targetValue = if (isSelected) 20.dp else 6.dp,
                    animationSpec = tween(300),
                )
                Box(
                    modifier = Modifier
                        .height(6.dp)
                        .width(width)
                        .clip(CircleShape)
                        .background(color),
                )
            }
        }
    }
}

/**
 * The hero's text: the title's logo art (its name in text until that loads, or when there is
 * none), a line of facts, a short overview on wide screens, and the two actions.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun HeroInfo(
    item: MediaItem,
    extras: FeaturedExtras?,
    isOnWatchlist: Boolean,
    onOpen: () -> Unit,
    onToggleWatchlist: () -> Unit,
    isWide: Boolean,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val align = if (isWide) Alignment.Start else Alignment.CenterHorizontally
    val textAlign = if (isWide) TextAlign.Start else TextAlign.Center
    Column(
        modifier = modifier.widthIn(max = if (isWide) 560.dp else 420.dp),
        horizontalAlignment = align,
    ) {
        TitleLogo(
            logoUrl = extras?.logoUrl,
            title = item.title,
            // Text only once the lookup is done and found no logo, so the logo doesn't pop in over it.
            showTextFallback = extras != null,
            maxWidth = if (compact) 280.dp else if (isWide) 440.dp else 300.dp,
            maxHeight = if (compact) 72.dp else if (isWide) 150.dp else 110.dp,
            textAlign = textAlign,
            isWide = isWide,
        )
        Spacer(Modifier.height(14.dp))
        val facts = buildList {
            add(if (item.type == MediaType.Movie) "Movie" else if (item.type == MediaType.Tv) "Series" else "Anime")
            item.year?.let(::add)
            if (item.rating > 0) add("★ ${oneDecimal(item.rating)}")
            // One genre on phones, so the line fits.
            extras?.genres?.takeIf { it.isNotEmpty() }?.let { add(it.take(if (isWide) 3 else 1).joinToString(", ")) }
        }
        Text(
            text = facts.joinToString("  •  "),
            style = MaterialTheme.typography.labelLarge,
            color = Mocha.Text.copy(alpha = 0.85f),
            textAlign = textAlign,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (isWide && !compact && item.overview.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = item.overview,
                style = MaterialTheme.typography.bodyMedium,
                color = Mocha.Subtext0,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(if (compact) 12.dp else 20.dp))
        // Wraps onto two lines on very narrow phones rather than running off the edge.
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp, if (isWide) Alignment.Start else Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Button(
                onClick = onOpen,
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(containerColor = Mocha.Text, contentColor = Mocha.Crust),
                contentPadding = PaddingValues(horizontal = 26.dp, vertical = 12.dp),
            ) {
                Icon(PlayerIcons.Play, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("More info", style = MaterialTheme.typography.labelLarge)
            }
            GlassPillButton(
                text = if (isOnWatchlist) "✓  Watchlist" else "+  Watchlist",
                onClick = onToggleWatchlist,
            )
        }
    }
}
