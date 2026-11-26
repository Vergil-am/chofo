package dev.dogetime.ui.details

import dev.dogetime.data.NextEpisodeFinder
import dev.dogetime.ui.components.ChoicePill
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.BorderStroke
import dev.dogetime.ui.components.GlassIconButton
import dev.dogetime.ui.components.TitleLogo
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.glass.GlassStyle
import dev.chrisbanes.haze.glass.hazeGlass
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import dev.dogetime.domain.FINISHED_FRACTION
import dev.dogetime.Mocha
import dev.dogetime.core.UiState
import androidx.compose.material3.Surface
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import dev.dogetime.data.WatchlistRepository
import dev.dogetime.domain.WatchStatus
import dev.dogetime.data.FavoritesRepository
import dev.dogetime.data.MediaRepository
import dev.dogetime.data.WatchHistoryRepository
import dev.dogetime.domain.CastMember
import dev.dogetime.domain.Episode
import dev.dogetime.domain.MediaDetails
import dev.dogetime.domain.MediaItem
import dev.dogetime.domain.MediaType
import dev.dogetime.domain.Season
import dev.dogetime.domain.WatchTarget
import dev.dogetime.domain.withMetadata
import dev.dogetime.ui.components.ErrorBox
import dev.dogetime.ui.components.GenrePill
import dev.dogetime.ui.components.LocalDownloadedEpisodes
import dev.dogetime.ui.components.LocalDownloadedMediaIds
import dev.dogetime.ui.components.MediaPoster
import dev.dogetime.ui.components.MediaRow
import dev.dogetime.ui.components.RatingRing
import dev.dogetime.ui.components.dragToScroll
import dev.dogetime.ui.components.oneDecimal
import dev.dogetime.ui.components.ratingText
import dev.dogetime.ui.player.PlayerIcons

@Composable
fun DetailsScreen(
    type: MediaType,
    id: Int,
    repository: MediaRepository,
    favorites: FavoritesRepository,
    onBack: () -> Unit,
    /** [resume]: play straight away, the way Continue Watching does, instead of listing streams. */
    onWatch: (target: WatchTarget, title: String, backdropUrl: String?, mediaItem: MediaItem?, resume: Boolean) -> Unit,
    onOpen: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
    watchHistory: WatchHistoryRepository? = null,
    initialItem: MediaItem? = null,
    watchlist: WatchlistRepository? = null,
    nextEpisodes: NextEpisodeFinder? = null,
) {
    // Keyed by type and id, so opening a different title gets a fresh ViewModel.
    val viewModel = remember(type, id) { DetailsViewModel(repository, favorites, type, id, watchHistory, watchlist, nextEpisodes) }
    DisposableEffect(viewModel) {
        onDispose { viewModel.clear() }
    }
    val state by viewModel.state.collectAsState()
    val episodes by viewModel.episodes.collectAsState()
    val isFavorite by viewModel.isFavorite.collectAsState()
    val episodeProgress by viewModel.episodeProgress.collectAsState()
    val watchStatus by viewModel.watchStatus.collectAsState()
    val playButton by viewModel.playButton.collectAsState()

    val detailsData = (state as? UiState.Success)?.data ?: initialItem?.let {
        MediaDetails(
            item = it,
            tagline = null,
            status = null,
            genres = emptyList(),
            runtime = null,
            voteCount = 0,
            seasonCount = null,
            episodeCount = null,
            seasons = emptyList(),
            cast = emptyList(),
            recommendations = emptyList(),
            similar = emptyList(),
        )
    }

    Box(modifier = modifier.fillMaxSize()) {
        when {
            detailsData != null -> DetailsContent(
                details = detailsData,
                episodes = episodes,
                episodeProgress = episodeProgress,
                onSeasonSelected = viewModel::selectSeason,
                // The watch screen shows the title's backdrop while the video loads.
                onWatch = { target, title ->
                    // The episode to continue, or the next one after a finished episode, plays at once.
                    val resume = playButton?.let { button ->
                        button.kind == PlayButton.Kind.Continue || button.kind == PlayButton.Kind.Next
                    } == true && target is WatchTarget.Episode &&
                        target.season == playButton?.season && target.episode == playButton?.episode
                    onWatch(
                        target.withMetadata(imdbId = detailsData.imdbId, kitsuId = detailsData.kitsuId),
                        title,
                        detailsData.item.backdropUrl,
                        detailsData.item,
                        resume,
                    )
                },
                isFavorite = isFavorite,
                onToggleFavorite = viewModel::toggleFavorite,
                watchlist = if (watchlist != null && state is UiState.Success) {
                    WatchlistControl(watchStatus, viewModel::setWatchStatus)
                } else null,
                playButton = playButton,
                onOpen = onOpen,
                isLoading = state is UiState.Loading,
            )
            state is UiState.Error -> ErrorBox((state as UiState.Error).message, onRetry = viewModel::load)
            else -> CircularProgressIndicator(Modifier.align(Alignment.Center))
        }

        // Floats over the backdrop; the scrim keeps it readable on bright images.
        IconButton(
            onClick = onBack,
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = Mocha.Crust.copy(alpha = 0.5f),
                contentColor = Mocha.Text,
            ),
            modifier = Modifier
                .statusBarsPadding()
                .padding(8.dp),
        ) {
            Icon(BackArrow, contentDescription = "Back")
        }
    }
}

private val BackArrow = ImageVector.Builder(
    name = "BackArrow",
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
    moveTo(19f, 12f)
    horizontalLineTo(5f)
    moveTo(11f, 6f)
    lineTo(5f, 12f)
    lineTo(11f, 18f)
}.build()

private val HeartOutline = ImageVector.Builder(
    name = "HeartOutline",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).path(
    stroke = SolidColor(Color.White),
    strokeLineWidth = 2f,
    strokeLineCap = StrokeCap.Round,
    strokeLineJoin = StrokeJoin.Round,
) {
    // Heart path
    moveTo(12f, 21f)
    curveTo(12f, 21f, 3f, 15f, 3f, 9.5f)
    curveTo(3f, 7f, 5f, 5f, 7.5f, 5f)
    curveTo(9.5f, 5f, 11f, 6.5f, 12f, 7.5f)
    curveTo(13f, 6.5f, 14.5f, 5f, 16.5f, 5f)
    curveTo(19f, 5f, 21f, 7f, 21f, 9.5f)
    curveTo(21f, 15f, 12f, 21f, 12f, 21f)
    close()
}.build()

private val HeartFilled = ImageVector.Builder(
    name = "HeartFilled",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).path(
    fill = SolidColor(Color.White),
    stroke = SolidColor(Color.White),
    strokeLineWidth = 1.5f,
    strokeLineCap = StrokeCap.Round,
    strokeLineJoin = StrokeJoin.Round,
) {
    moveTo(12f, 21f)
    curveTo(12f, 21f, 3f, 15f, 3f, 9.5f)
    curveTo(3f, 7f, 5f, 5f, 7.5f, 5f)
    curveTo(9.5f, 5f, 11f, 6.5f, 12f, 7.5f)
    curveTo(13f, 6.5f, 14.5f, 5f, 16.5f, 5f)
    curveTo(19f, 5f, 21f, 7f, 21f, 9.5f)
    curveTo(21f, 15f, 12f, 21f, 12f, 21f)
    close()
}.build()

private val PlayIcon = ImageVector.Builder(
    name = "Play",
    defaultWidth = 20.dp,
    defaultHeight = 20.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).path(
    fill = SolidColor(Color.White),
) {
    moveTo(6f, 4f)
    lineTo(20f, 12f)
    lineTo(6f, 20f)
    close()
}.build()

/** Width from which the episodes move next to the details instead of below them. */
private val TwoPaneMinWidth = 840.dp

/** Landscape phones get the side-by-side layout from this width. */
private val TwoPaneLandscapeMinWidth = 600.dp

/** Material 3 medium breakpoint — at or above this the window is "medium width". */
private val MediumWidthBreakpoint = 600.dp

@Composable
private fun DetailsContent(
    details: MediaDetails,
    episodes: EpisodesState,
    episodeProgress: Map<Pair<Int, Int>, Float>,
    onSeasonSelected: (Int) -> Unit,
    onWatch: (target: WatchTarget, title: String) -> Unit,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    watchlist: WatchlistControl?,
    playButton: PlayButton?,
    onOpen: (MediaItem) -> Unit,
    isLoading: Boolean = false,
) {
    BoxWithConstraints {
        val wide = maxWidth >= TwoPaneMinWidth || (maxWidth > maxHeight && maxWidth >= TwoPaneLandscapeMinWidth)
        // Backdrop height: full screen on medium+ width, 70% in portrait/narrow.
        val isMediumOrWider = maxWidth >= MediumWidthBreakpoint
        val heroHeight = if (isMediumOrWider) maxHeight else maxHeight * 0.7f

        if (wide && details.seasons.isNotEmpty()) {
            WideDetails(details, episodes, episodeProgress, onSeasonSelected, onWatch, isFavorite, onToggleFavorite, watchlist, playButton, onOpen, heroHeight)
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                detailsSection(details, onWatch, isFavorite, onToggleFavorite, watchlist, playButton = playButton, heroHeight = heroHeight, includeOverviewInHero = isMediumOrWider, episodeProgress = episodeProgress)
                if (isLoading) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(color = Mocha.Mauve)
                        }
                    }
                }
                episodesSection(details, episodes, episodeProgress, onSeasonSelected, onWatch)
                castSection(details)
                relatedSection(details, onOpen)
            }
        }
    }
}

private val EpisodesPanelShape = RoundedCornerShape(24.dp)

@OptIn(ExperimentalHazeApi::class)
@Composable
private fun rememberEpisodesGlass(): GlassStyle {
    val bg = MaterialTheme.colorScheme.surface
    return remember(bg) {
        GlassStyle.regular.then {
            shape(EpisodesPanelShape)
            tint(bg.copy(alpha = 0.35f))
        }
    }
}

/**
 * Wide screens: details on the left and episodes on the right, each scrolling on its own.
 * The backdrop spans the full width behind both, moving with the details as they scroll, and
 * the episodes sit on a frosted-glass panel that blurs it.
 */
@OptIn(ExperimentalHazeApi::class)
@Composable
private fun WideDetails(
    details: MediaDetails,
    episodes: EpisodesState,
    episodeProgress: Map<Pair<Int, Int>, Float>,
    onSeasonSelected: (Int) -> Unit,
    onWatch: (target: WatchTarget, title: String) -> Unit,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    watchlist: WatchlistControl?,
    playButton: PlayButton?,
    onOpen: (MediaItem) -> Unit,
    heroHeight: Dp,
) {
    val hazeState = rememberHazeState()
    val detailsListState = rememberLazyListState()
    var heroContentHeightPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .hazeSource(hazeState),
        ) {
            details.item.backdropUrl?.let { url ->
                Backdrop(
                    url = url,
                    title = details.item.title,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(with(density) { heroContentHeightPx.toDp() })
                        .graphicsLayer {
                            // Scrolls away with the hero above it.
                            translationY = if (detailsListState.firstVisibleItemIndex == 0) {
                                -detailsListState.firstVisibleItemScrollOffset.toFloat()
                            } else {
                                -heroContentHeightPx.toFloat()
                            }
                        },
                )
            }
        }

        Row(Modifier.fillMaxSize()) {
            LazyColumn(
                state = detailsListState,
                contentPadding = PaddingValues(bottom = 24.dp),
                modifier = Modifier.weight(1f).fillMaxHeight(),
            ) {
                detailsSection(
                    details = details,
                    onWatch = onWatch,
                    isFavorite = isFavorite,
                    onToggleFavorite = onToggleFavorite,
                    watchlist = watchlist,
                    playButton = playButton,
                    drawBackdrop = false,
                    onHeroHeightChanged = { heroContentHeightPx = it },
                    heroHeight = heroHeight,
                    includeOverviewInHero = true,
                    episodeProgress = episodeProgress,
                )
                castSection(details)
                relatedSection(details, onOpen)
            }
            LazyColumn(
                contentPadding = PaddingValues(bottom = 16.dp),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical + WindowInsetsSides.End))
                    .padding(16.dp)
                    .clip(EpisodesPanelShape)
                    .hazeGlass(input = HazeInput.Sources(hazeState), style = rememberEpisodesGlass()),
            ) {
                episodesSection(details, episodes, episodeProgress, onSeasonSelected, onWatch)
            }
        }
    }
}

/** The title's backdrop image, darkened toward the bottom so text stays readable and it fades into the page. */
@Composable
private fun Backdrop(url: String, title: String, modifier: Modifier = Modifier) {
    val background = MaterialTheme.colorScheme.background
    Box(modifier) {
        AsyncImage(
            model = url,
            contentDescription = title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize(),
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        0f to background.copy(alpha = 0.25f),
                        0.25f to Color.Transparent,
                        0.55f to background.copy(alpha = 0.6f),
                        0.8f to background.copy(alpha = 0.9f),
                        1f to background,
                    ),
                ),
        )
    }
}

/**
 * Backdrop with poster and title, the action buttons, genres and the overview. With
 * [drawBackdrop] false the hero leaves the backdrop to the caller, and reports its height
 * through [onHeroHeightChanged] so the caller can size it. [heroHeight] controls the
 * backdrop height — on medium+ screens it fills the screen, on narrow/portrait it's 70%.
 * When [includeOverviewInHero] is true the genres, season info, and overview are rendered
 * inside the hero backdrop instead of as separate items below it.
 */
@OptIn(ExperimentalLayoutApi::class)
private fun LazyListScope.detailsSection(
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

/** Season picker and the selected season's episodes. Nothing for movies. */
private fun LazyListScope.episodesSection(
    details: MediaDetails,
    episodes: EpisodesState,
    episodeProgress: Map<Pair<Int, Int>, Float>,
    onSeasonSelected: (Int) -> Unit,
    onWatch: (target: WatchTarget, title: String) -> Unit,
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
        )
    }
}

private fun LazyListScope.castSection(details: MediaDetails) {
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
private fun LazyListScope.relatedSection(details: MediaDetails, onOpen: (MediaItem) -> Unit) {
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
    onWatch: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
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

@Composable
private fun SectionTitle(title: String) {
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
private class WatchlistControl(val status: WatchStatus?, val onSet: (WatchStatus?) -> Unit)

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
