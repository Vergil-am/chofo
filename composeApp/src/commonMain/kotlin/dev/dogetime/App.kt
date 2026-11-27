package dev.dogetime

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.network.ktor3.KtorNetworkFetcherFactory
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import dev.dogetime.core.PlatformBackHandler
import dev.dogetime.core.rememberViewModel
import dev.dogetime.di.AppContainer
import dev.dogetime.domain.DownloadStatus
import dev.dogetime.domain.MediaItem
import dev.dogetime.domain.WatchTarget
import dev.dogetime.navigation.Destination
import dev.dogetime.navigation.rememberNavigator
import dev.dogetime.ui.account.AccountViewModel
import dev.dogetime.ui.account.AuthScreen
import dev.dogetime.ui.browse.BrowseScreen
import dev.dogetime.ui.browse.BrowseViewModel
import dev.dogetime.ui.components.LocalDownloadedEpisodes
import dev.dogetime.ui.components.LocalDownloadedMediaIds
import dev.dogetime.ui.components.LocalNavAnimatedVisibilityScope
import dev.dogetime.ui.components.LocalSharedTransitionScope
import dev.dogetime.ui.details.DetailsScreen
import dev.dogetime.ui.home.HomeScreen
import dev.dogetime.ui.home.HomeViewModel
import dev.dogetime.ui.library.LibraryScreen
import dev.dogetime.ui.library.LibrarySection
import dev.dogetime.ui.library.LibraryViewModel
import dev.dogetime.ui.player.PlaybackSource
import dev.dogetime.ui.player.VideoPlayerScreen
import dev.dogetime.ui.search.SearchScreen
import dev.dogetime.ui.search.SearchViewModel
import dev.dogetime.ui.settings.SettingsScreen
import dev.dogetime.ui.settings.SettingsViewModel
import dev.dogetime.ui.watch.WatchScreen
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@Composable
fun App() {
    val container = remember { AppContainer() }
    // Null until the saved theme is read, so the app never shows the wrong theme first.
    val savedTheme by remember {
        container.preferences.observeThemeId().map { id -> AppTheme.entries.firstOrNull { it.id == id } ?: AppTheme.Midnight }
    }.collectAsState(initial = null)
    val currentTheme = savedTheme ?: return
    val selectTheme = { theme: AppTheme -> container.preferences.setThemeId(theme.id) }
    val torrentBufferMb by remember { container.preferences.observeTorrentBufferMb() }.collectAsState(initial = 0)

    ChofoTheme(
        theme = currentTheme,
        onThemeChange = selectTheme,
    ) {
        // Coil needs a network fetcher before the first poster loads.
        setSingletonImageLoaderFactory { context ->
            ImageLoader.Builder(context)
                .components { add(KtorNetworkFetcherFactory()) }
                .build()
        }

        val navigator = rememberNavigator()
        val appUiScope = rememberCoroutineScope()
        var offlineLookup by remember { mutableStateOf<Job?>(null) }

        // remember: observeAll() makes a new Flow each call, and collectAsState restarts on a new one.
        val completedDownloads by remember { container.downloadManager.repository.observeAll() }
            .collectAsState(initial = emptyList())
        val downloadedMediaIds = remember(completedDownloads) {
            completedDownloads.filter { it.status == DownloadStatus.COMPLETED }.mapNotNull { it.mediaId }.toSet()
        }
        val downloadedEpisodes = remember(completedDownloads) {
            completedDownloads.filter { it.status == DownloadStatus.COMPLETED && it.mediaId != null && it.season != null && it.episode != null }
                .map { (it.mediaId!! to (it.season!! to it.episode!!)) }.toSet()
        }

        // Tab ViewModels live here so switching tabs keeps their state.
        val homeViewModel = remember { HomeViewModel(container.repository, container.favorites, container.watchHistory, container.watchSync::refresh, container.watchlist, container.nextEpisodes) }
        val accountViewModel = remember { AccountViewModel(container.accounts, container.watchSync) }
        val streamAddonsViewModel = remember { dev.dogetime.ui.settings.StreamAddonsViewModel(container.streamAddons) }
        val browseViewModel = remember { BrowseViewModel(container.repository) }
        val searchViewModel = remember { SearchViewModel(container.repository) }
        val libraryViewModel = remember { LibraryViewModel(container.downloadManager, container.watchHistory, container.favorites, container.watchlist) }
        val settingsViewModel = remember { SettingsViewModel(container.updateChecker) }
        DisposableEffect(Unit) {
            onDispose {
                homeViewModel.clear()
                browseViewModel.clear()
                searchViewModel.clear()
                libraryViewModel.clear()
                settingsViewModel.clear()
                accountViewModel.clear()
                streamAddonsViewModel.clear()
            }
        }

        // First launch: offer to sign in, or to use the app locally. Null until the choice is read.
        val onboardingDone by remember { container.accounts.onboardingDone }.collectAsState(initial = null)
        // Sign-in opened from Settings.
        var showSignIn by remember { mutableStateOf(false) }
        val googleSignIn = dev.dogetime.data.sync.rememberGoogleSignIn(container.chofoApi)
        // Send unsynced watch progress when the app leaves the screen or (desktop) closes.
        dev.dogetime.core.OnAppBackground { container.watchSync.onBackground() }
        DisposableEffect(container) {
            dev.dogetime.core.AppShutdown.register { container.watchSync.flush() }
            onDispose { }
        }
        when (onboardingDone) {
            null -> return@ChofoTheme
            false -> {
                AuthScreen(viewModel = accountViewModel, googleSignIn = googleSignIn, isWelcome = true, onDone = {}, onDismiss = {})
                return@ChofoTheme
            }
            true -> Unit
        }

        PlatformBackHandler(enabled = showSignIn) { showSignIn = false }
        PlatformBackHandler(enabled = navigator.canGoBack && !showSignIn) {
            navigator.back()
        }

        val current = navigator.current
        val showTabs = current !is Destination.Details && current !is Destination.Watch && current !is Destination.OfflineWatch
        val hazeState = rememberHazeState()

        BoxWithConstraints(Modifier.fillMaxSize()) {
            // Desktop and landscape: a side rail instead of the bottom bar.
            val isWide = maxWidth > 839.dp || maxWidth > maxHeight
            val isShort = maxHeight < 520.dp
            val railInset = WindowInsets.safeDrawing.only(WindowInsetsSides.Start).asPaddingValues().calculateStartPadding(LocalLayoutDirection.current)
            val startInset = if (isWide && showTabs) railInset + NavBarWidth else 0.dp

            Scaffold { innerPadding ->
                val bottomInset = if (isWide || !showTabs) 0.dp else innerPadding.calculateBottomPadding() + NavBarHeight + NavBarMargin * 2
                fun screenModifier(dest: Destination): Modifier = when {
                    // Home's hero runs under the status bar and the transparent rail; Home insets its own content.
                    dest == Destination.Home -> Modifier
                    dest !is Destination.Details && dest !is Destination.Watch && dest !is Destination.OfflineWatch -> Modifier
                        .padding(top = innerPadding.calculateTopPadding())
                        .padding(start = startInset)
                    dest is Destination.Details -> Modifier.padding(bottom = innerPadding.calculateBottomPadding())
                    dest is Destination.Watch || dest is Destination.OfflineWatch -> Modifier
                    else -> Modifier.padding(innerPadding)
                }
                val openDetails = { item: MediaItem ->
                    navigator.open(Destination.Details(item.type, item.id, item))
                }

                Box(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize().hazeSource(hazeState)) {
                        @OptIn(ExperimentalSharedTransitionApi::class)
                        SharedTransitionLayout {
                            AnimatedContent(
                                targetState = current,
                                transitionSpec = {
                                    fadeIn(animationSpec = tween(300)) togetherWith fadeOut(animationSpec = tween(300))
                                },
                                label = "ScreenTransition",
                            ) { targetDestination ->
                                CompositionLocalProvider(
                                    LocalSharedTransitionScope provides this@SharedTransitionLayout,
                                    LocalNavAnimatedVisibilityScope provides this@AnimatedContent,
                                    LocalDownloadedMediaIds provides downloadedMediaIds,
                                    LocalDownloadedEpisodes provides downloadedEpisodes,
                                ) {
                                    val destModifier = screenModifier(targetDestination)
                                    when (targetDestination) {
                                        Destination.Home -> HomeScreen(
                                            viewModel = homeViewModel,
                                            onOpen = openDetails,
                                            modifier = destModifier,
                                            bottomInset = bottomInset,
                                            isWide = isWide,
                                            startInset = startInset,
                                            onNavigateToBrowse = { type, category ->
                                                browseViewModel.selectFilter(type, category)
                                                navigator.selectTab(Destination.Browse)
                                            },
                                            onOpenWatchlist = {
                                                libraryViewModel.showSection(LibrarySection.Watchlist)
                                                navigator.selectTab(Destination.Library)
                                            },
                                            onContinue = { history ->
                                                if (offlineLookup?.isActive != true) {
                                                    offlineLookup = appUiScope.launch {
                                                        navigator.open(continueWatching(container, history))
                                                    }
                                                }
                                            },
                                            continueActions = dev.dogetime.ui.components.ContinueWatchingActions(
                                                onDetails = { openDetails(it.mediaItem) },
                                                onStartOver = { history ->
                                                    if (offlineLookup?.isActive != true) {
                                                        offlineLookup = appUiScope.launch {
                                                            navigator.open(continueWatching(container, history, fromBeginning = true))
                                                        }
                                                    }
                                                },
                                                onRemove = { history ->
                                                    appUiScope.launch {
                                                        container.watchHistory.deleteByMedia(history.mediaItem.type, history.mediaItem.id)
                                                    }
                                                },
                                            ),
                                        )
                                        Destination.Browse -> BrowseScreen(
                                            viewModel = browseViewModel,
                                            onOpen = openDetails,
                                            modifier = destModifier,
                                            bottomInset = bottomInset,
                                        )
                                        Destination.Search -> SearchScreen(
                                            viewModel = searchViewModel,
                                            onOpen = openDetails,
                                            modifier = destModifier,
                                            bottomInset = bottomInset,
                                        )
                                        Destination.Library -> LibraryScreen(
                                            viewModel = libraryViewModel,
                                            onOpen = openDetails,
                                            onPlayDownload = { item ->
                                                navigator.open(Destination.OfflineWatch(item))
                                            },
                                            modifier = destModifier,
                                            bottomInset = bottomInset,
                                        )
                                        Destination.Settings -> SettingsScreen(
                                            currentTheme = currentTheme,
                                            onThemeSelect = selectTheme,
                                            torrentBufferMb = torrentBufferMb,
                                            onTorrentBufferChange = container.preferences::setTorrentBufferMb,
                                            viewModel = settingsViewModel,
                                            accountViewModel = accountViewModel,
                                            streamAddonsViewModel = streamAddonsViewModel,
                                            onSignIn = {
                                                accountViewModel.setMode(dev.dogetime.ui.account.AuthMode.SignIn)
                                                showSignIn = true
                                            },
                                            modifier = destModifier,
                                            bottomInset = bottomInset,
                                        )
                                        is Destination.Details -> DetailsScreen(
                                            type = targetDestination.type,
                                            id = targetDestination.id,
                                            initialItem = targetDestination.initialItem,
                                            repository = container.repository,
                                            favorites = container.favorites,
                                            watchHistory = container.watchHistory,
                                            watchlist = container.watchlist,
                                            nextEpisodes = container.nextEpisodes,
                                            onBack = { navigator.back() },
                                            onWatch = { target, title, backdropUrl, mediaItem, resume ->
                                                // The lookup reads the database and disk, so it runs off the main
                                                // thread. The guard stops a double tap from opening the player twice.
                                                if (offlineLookup?.isActive != true) {
                                                    offlineLookup = appUiScope.launch {
                                                        val copy = container.offlineCopies.findOfflineCopy(target, title, mediaItem?.type)
                                                        navigator.open(
                                                            if (copy != null) {
                                                                Destination.OfflineWatch(copy)
                                                            } else {
                                                                Destination.Watch(target, title, backdropUrl, mediaItem, resumeLastStream = resume)
                                                            },
                                                        )
                                                    }
                                                }
                                            },
                                            onOpen = openDetails,
                                            modifier = destModifier,
                                        )
                                        is Destination.Watch -> WatchScreen(
                                            viewModel = rememberViewModel(targetDestination) {
                                                container.watchViewModel(targetDestination)
                                            },
                                            target = targetDestination.target,
                                            title = targetDestination.title,
                                            torrentBufferMb = torrentBufferMb,
                                            // "Start from the beginning" applies to the first player only, not
                                            // after a change of source mid-way.
                                            createPlayerViewModel = remember(targetDestination) {
                                                var fromBeginning = targetDestination.fromBeginning
                                                val factory: dev.dogetime.ui.player.PlayerViewModelFactory = { source ->
                                                    container.playerViewModel(source, startFromBeginning = fromBeginning).also { fromBeginning = false }
                                                }
                                                factory
                                            },
                                            backdropUrl = targetDestination.backdropUrl,
                                            mediaItem = targetDestination.mediaItem,
                                            onBack = { navigator.back() },
                                            modifier = destModifier,
                                        )
                                        is Destination.OfflineWatch -> VideoPlayerScreen(
                                            source = PlaybackSource.Download(targetDestination.item),
                                            createViewModel = { source -> container.playerViewModel(source, targetDestination.fromBeginning) },
                                            onBack = { navigator.back() },
                                            onOpenExternal = null,
                                            modifier = destModifier,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (showTabs) {
                        if (isWide) {
                            FloatingNavBar(
                                root = navigator.root,
                                onSelect = navigator::selectTab,
                                hazeState = hazeState,
                                isVertical = true,
                                // Phones in landscape are short: tighter tabs so all five fit.
                                compact = isShort,
                                modifier = Modifier
                                    .align(Alignment.CenterStart)
                                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Start + WindowInsetsSides.Vertical)),
                            )
                        } else {
                            FloatingNavBar(
                                root = navigator.root,
                                onSelect = navigator::selectTab,
                                hazeState = hazeState,
                                isVertical = false,
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = innerPadding.calculateBottomPadding() + NavBarMargin),
                            )
                        }
                    }

                    if (showSignIn) {
                        AuthScreen(
                            viewModel = accountViewModel,
                            googleSignIn = googleSignIn,
                            isWelcome = false,
                            onDone = { showSignIn = false },
                            onDismiss = { showSignIn = false },
                        )
                    }
                }
            }
        }
    }
}

/**
 * Where a Continue Watching card leads: the movie or episode itself, playing the stream it was
 * last played with (or the downloaded copy), rather than the title's page. The title is looked
 * up first for its IMDb and Kitsu ids, which the add-ons and web sources need.
 */
private suspend fun continueWatching(
    container: AppContainer,
    history: dev.dogetime.domain.WatchHistoryItem,
    fromBeginning: Boolean = false,
): Destination {
    val media = history.mediaItem
    val details = try {
        kotlinx.coroutines.withTimeoutOrNull(8_000) { container.repository.details(media.type, media.id) }
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (_: Exception) {
        null // offline: the remembered stream may still play
    }
    val season = history.season
    val episode = history.episode
    val target = if (season != null && episode != null) {
        WatchTarget.Episode(media.id, season, episode, imdbId = details?.imdbId, kitsuId = details?.kitsuId)
    } else {
        WatchTarget.Movie(media.id, imdbId = details?.imdbId, kitsuId = details?.kitsuId)
    }
    val title = if (season != null && episode != null) "${media.title} S${season}E$episode" else media.title
    val item = details?.item ?: media
    container.offlineCopies.findOfflineCopy(target, title, media.type)?.let { return Destination.OfflineWatch(it, fromBeginning) }
    return Destination.Watch(target, title, item.backdropUrl, item, resumeLastStream = true, fromBeginning = fromBeginning)
}
