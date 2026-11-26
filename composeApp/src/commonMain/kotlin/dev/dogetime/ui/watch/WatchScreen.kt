package dev.dogetime.ui.watch

import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Icon
import androidx.compose.material3.ButtonDefaults
import dev.dogetime.Mocha
import dev.dogetime.ui.player.PlayerIcons
import dev.dogetime.ui.components.ChoicePill
import dev.dogetime.ui.components.GlassCard
import dev.dogetime.ui.components.GlassIconButton
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.multiplatform.webview.web.rememberWebViewNavigator
import com.multiplatform.webview.web.rememberWebViewState
import dev.dogetime.core.CapturedStream
import dev.dogetime.core.ExternalPlayer
import dev.dogetime.core.Torrents
import dev.dogetime.core.WebEngineGate
import dev.dogetime.data.addons.StreamAddon
import dev.dogetime.domain.MediaItem
import dev.dogetime.domain.RememberedStream
import dev.dogetime.domain.VideoSource
import dev.dogetime.domain.WatchTarget
import dev.dogetime.ui.player.PlaybackSource
import dev.dogetime.ui.player.PlayerViewModelFactory
import dev.dogetime.ui.player.VideoPlayerScreen
import kotlinx.coroutines.delay

/** How long the auto-play script gets to start the video before the page is shown. */
private const val AUTO_PLAY_TIMEOUT_MS = 20_000L
private const val MESSAGE_DURATION_MS = 4_000L

/**
 * Watches [target]: the user picks a [VideoSource] first, then that source's page is played with
 * [SourcePlayer], which can go back to the source menu if that source doesn't work. Torrents are
 * picked from a provider's list for [target] and played with [MagnetPlayer]. The step shown, the
 * torrent list and downloads are in [viewModel].
 */
@Composable
fun WatchScreen(
    viewModel: WatchViewModel,
    target: WatchTarget,
    title: String,
    createPlayerViewModel: PlayerViewModelFactory,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    backdropUrl: String? = null,
    mediaItem: MediaItem? = null,
) {
    val state by viewModel.state.collectAsState()

    // Opened from Continue Watching: a spinner while the last stream is found, not the menu.
    if (state.step == WatchStep.SourceMenu && (state.isCheckingRemembered || (state.isResuming && state.remembered != null))) {
        Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            if (backdropUrl != null) BlurredBackdrop(backdropUrl, Modifier.fillMaxSize())
            WatchPage(title = title, pageShown = false, onBack = onBack, onChangeSource = viewModel::backToTorrents) {
                Column(
                    Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
                ) {
                    CircularProgressIndicator()
                    Text(state.remembered?.label?.let { "Opening $it…" } ?: "Opening…")
                }
            }
        }
        return
    }

    when (val step = state.step) {
        is WatchStep.Torrent -> key(step.pick) {
            MagnetPlayer(
                pick = step.pick,
                title = title,
                onBack = onBack,
                // Back to the torrent list.
                onChangeSource = viewModel::backToTorrents,
                modifier = modifier,
                backdropUrl = backdropUrl,
                target = target,
                mediaItem = mediaItem,
                createPlayerViewModel = createPlayerViewModel,
            )
        }
        WatchStep.Torrents -> Box(modifier = Modifier.fillMaxSize()) {
            TorrentPicker(
                title = title,
                backdropUrl = backdropUrl,
                addons = state.addons,
                provider = state.provider,
                torrents = state.torrents,
                downloadChoice = state.downloadChoice,
                onSelectProvider = viewModel::selectProvider,
                onRetry = viewModel::loadTorrents,
                onBack = onBack,
                onPick = viewModel::playTorrent,
                remembered = state.remembered,
                isResuming = state.isResuming,
                onResume = viewModel::resumeRemembered,
                onChooseDownload = if (viewModel.canDownload) viewModel::chooseDownload else null,
                onDismissDownload = viewModel::dismissDownload,
                onDownload = viewModel::download,
                modifier = modifier,
            )
            state.notice?.let {
                MessageChip(it, Modifier.align(Alignment.BottomCenter).padding(bottom = 96.dp))
            }
        }
        // Only shown when no add-on can list streams for this title: there's no source menu any more.
        WatchStep.SourceMenu -> NoStreamSources(title = title, backdropUrl = backdropUrl, onBack = onBack, modifier = modifier)
        // Keyed so a newly picked source starts with a fresh page and no captured stream.
        is WatchStep.Source -> key(step.source) {
            SourcePlayer(
                url = step.source.url(target),
                remembered = RememberedStream.of(step.source),
                title = title,
                onBack = onBack,
                onChangeSource = viewModel::backToSourceMenu,
                modifier = modifier,
                backdropUrl = backdropUrl,
                target = target,
                mediaItem = mediaItem,
                createPlayerViewModel = createPlayerViewModel,
            )
        }
    }
}

/** Nothing to pick from: no installed stream add-on serves this title, or torrents don't work here. */
@Composable
private fun NoStreamSources(title: String, backdropUrl: String?, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (backdropUrl != null) BlurredBackdrop(backdropUrl, Modifier.fillMaxSize())
        WatchPage(title = title, pageShown = false, onBack = onBack) {
            Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text(
                    text = if (Torrents.isSupported) {
                        "None of your stream add-ons can play this title. Install one in Settings, under Stream add-ons."
                    } else {
                        "Streams aren't supported on this device yet."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = Mocha.Subtext0,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(max = 360.dp),
                )
            }
        }
    }
}

/**
 * Loads [url] hidden in an invisible WebView, looking for the stream URL it requests (e.g. An
 * .m3u8 playlist or .mp4 file). Once found, hands it to [VideoPlayerScreen] to play directly.
 */
@Composable
private fun SourcePlayer(
    url: String,
    remembered: RememberedStream,
    title: String,
    onBack: () -> Unit,
    onChangeSource: () -> Unit,
    modifier: Modifier = Modifier,
    backdropUrl: String? = null,
    target: WatchTarget? = null,
    mediaItem: MediaItem? = null,
    createPlayerViewModel: PlayerViewModelFactory,
) {
    val allowedHost = remember(url) { hostOf(url) }
    val webState = rememberWebViewState(url)
    // Block redirects and pop-unders that try to take the main page off the player's site.
    val navigator = rememberWebViewNavigator(
        requestInterceptor = remember(allowedHost) { PlayerRequestInterceptor(allowedHost) },
    )
    // Embedded players need JavaScript, DOM storage and autoplay to work.
    webState.webSettings.apply {
        isJavaScriptEnabled = true
        androidWebSettings.domStorageEnabled = true
        androidWebSettings.mediaPlaybackRequiresUserGesture = false
        iOSWebSettings.mediaPlaybackRequiresUserGesture = false
        desktopWebSettings.disablePopupWindows = true
    }

    // Desktop and Android: the first stream the page requests is played in the app's player.
    var stream by remember(url) { mutableStateOf<CapturedStream?>(null) }
    var message by remember(url) { mutableStateOf<String?>(null) }
    LaunchedEffect(message) {
        if (message != null) {
            delay(MESSAGE_DURATION_MS)
            message = null
        }
    }

    val found = stream
    if (found != null) {
        Box(modifier.fillMaxSize()) {
            VideoPlayerScreen(
                source = PlaybackSource.Stream(found, title, target, mediaItem, remembered),
                createViewModel = createPlayerViewModel,
                onBack = onBack,
                onOpenExternal = ExternalPlayer.name?.let {
                    { message = ExternalPlayer.play(found, title) ?: "Opening in $it…" }
                },
            )
            message?.let { MessageChip(it, Modifier.align(Alignment.BottomCenter).padding(bottom = 96.dp)) }
        }
        return
    }

    // The page is kept hidden behind a loading screen while the auto-play script starts the video.
    // It is shown if that takes too long (or on request), so the user can press play or solve a
    // challenge themselves.
    var pageShown by remember(url) { mutableStateOf(!ExternalPlayer.isSupported) }
    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (!pageShown && backdropUrl != null) {
            BlurredBackdrop(backdropUrl, Modifier.fillMaxSize())
        }
        WatchPage(
            title = title,
            pageShown = pageShown,
            onBack = onBack,
            onChangeSource = onChangeSource,
        ) {
            WebEngineGate {
                LaunchedEffect(Unit) {
                    delay(AUTO_PLAY_TIMEOUT_MS)
                    pageShown = true
                }
                // The page keeps running while hidden: removing it would stop the player from
                // loading. The desktop browser draws its own native window, which can't be made
                // transparent or covered, so the page is also shrunk to a single dot.
                PlayerWebView(
                    state = webState,
                    navigator = navigator,
                    allowedHost = allowedHost,
                    onStreamFound = { captured -> if (stream == null) stream = captured },
                    modifier = if (pageShown) Modifier.fillMaxSize() else Modifier.size(1.dp).alpha(0f),
                )
                if (!pageShown) {
                    LoadingScreen(onShowPage = { pageShown = true }, modifier = Modifier.fillMaxSize())
                }
            }
        }
    }
}

/**
 * Back button and title above [content], which fills the rest of the screen. With
 * [onChangeSource], there is also a button back to the source menu.
 */
@Composable
internal fun WatchPage(
    title: String,
    pageShown: Boolean,
    onBack: () -> Unit,
    onChangeSource: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            GlassIconButton(icon = PlayerIcons.Back, contentDescription = "Back", onClick = onBack, size = 42.dp)
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            onChangeSource?.let {
                ChoicePill("Change source", selected = false, onClick = it)
            }
        }
        if (pageShown && ExternalPlayer.isSupported) {
            Text(
                text = "Press play in the player below.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            content()
        }
    }
}

/** The title's backdrop, blurred and darkened, behind the loading screen. */
@Composable
internal fun BlurredBackdrop(url: String, modifier: Modifier = Modifier) {
    val background = MaterialTheme.colorScheme.background
    Box(modifier) {
        AsyncImage(
            model = url,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().blur(32.dp),
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to background.copy(alpha = 0.55f),
                        0.5f to background.copy(alpha = 0.35f),
                        1f to background.copy(alpha = 0.8f),
                    ),
                ),
        )
    }
}

@Composable
private fun LoadingScreen(onShowPage: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CircularProgressIndicator()
            Text("Finding the video…")
            TextButton(onClick = onShowPage) {
                Text("Show page")
            }
        }
    }
}

@Composable
internal fun MessageChip(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.inverseOnSurface,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.inverseSurface)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    )
}
