package dev.dogetime.ui.details

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.glass.GlassStyle
import dev.chrisbanes.haze.glass.hazeGlass
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import dev.dogetime.Mocha
import dev.dogetime.core.UiState
import dev.dogetime.data.FavoritesRepository
import dev.dogetime.data.MediaRepository
import dev.dogetime.data.NextEpisodeFinder
import dev.dogetime.data.WatchHistoryRepository
import dev.dogetime.data.WatchlistRepository
import dev.dogetime.domain.Episode
import dev.dogetime.domain.MediaDetails
import dev.dogetime.domain.MediaItem
import dev.dogetime.domain.MediaType
import dev.dogetime.domain.WatchTarget
import dev.dogetime.domain.withMetadata
import dev.dogetime.ui.components.ErrorBox

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
internal fun Backdrop(url: String, title: String, modifier: Modifier = Modifier) {
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
