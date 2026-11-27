package dev.dogetime.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import dev.dogetime.core.CapturedStream
import dev.dogetime.core.rememberViewModel
import kotlinx.coroutines.delay

private const val CONTROLS_HIDE_DELAY_MS = 3_500L
private const val FEEDBACK_DURATION_MS = 700L
internal const val DOUBLE_TAP_SEEK_MS = 10_000L
private const val KEY_SEEK_MS = 5_000L
private const val PROGRESS_SAVE_INTERVAL_MS = 5_000L
/** Builds the [PlayerViewModel] for a [PlaybackSource]. Provided by the app's container. */
typealias PlayerViewModelFactory = (PlaybackSource) -> PlayerViewModel

/**
 * Full-screen player for [source] with auto-hiding controls: a top bar (back, title, speed,
 * external player), large center buttons, and a seek bar. On touch screens a tap shows or hides
 * the controls and a double tap on either side seeks; with a mouse a click pauses and a double
 * click toggles fullscreen. [onOpenExternal] adds a button that hands the stream to another app.
 * [status] is shown under the top bar with the controls, and under the spinner while the video
 * stalls (for a torrent: its peers, speed and progress).
 */
@Composable
fun VideoPlayerScreen(
    source: PlaybackSource,
    createViewModel: PlayerViewModelFactory,
    onBack: () -> Unit,
    onOpenExternal: (() -> Unit)?,
    modifier: Modifier = Modifier,
    status: (@Composable () -> Unit)? = null,
) {
    // Sources are data classes, so an equal source on recomposition keeps the same ViewModel.
    val viewModel = rememberViewModel(source) { createViewModel(source) }
    val state by viewModel.state.collectAsState()

    PlayerSystemUiEffect()
    val playback = state.playback
    if (playback == null) {
        // A download's files are still being looked up, or turned out to be missing.
        Box(modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            val error = state.loadError
            if (error != null) {
                ErrorPanel(message = error, actionLabel = "Back", onAction = onBack, onOpenExternal = null)
            } else {
                CircularProgressIndicator(color = ControlTint)
            }
        }
        return
    }

    // "Try again" starts a fresh player.
    var attempt by remember(playback.stream) { mutableIntStateOf(0) }
    key(playback.stream, attempt) {
        PlayerContent(
            viewModel = viewModel,
            state = state,
            stream = playback.stream,
            title = playback.title,
            onBack = onBack,
            onOpenExternal = onOpenExternal,
            onRetry = { attempt++ },
            modifier = modifier,
            status = status,
        )
    }
}

/** Transient label shown over the video: a double-tap seek, or the new fit mode. */
internal data class Feedback(val text: String, val alignment: Alignment, val id: Int, val seekMs: Long = 0L)

@Composable
private fun PlayerContent(
    viewModel: PlayerViewModel,
    state: PlayerUiState,
    stream: CapturedStream,
    title: String,
    onBack: () -> Unit,
    onOpenExternal: (() -> Unit)?,
    onRetry: () -> Unit,
    modifier: Modifier,
    status: (@Composable () -> Unit)?,
) {
    val controller = rememberVideoPlayerController(stream, title)
    val fullscreen = rememberFullscreenController()
    var fit by remember { mutableStateOf(VideoFit.Fit) }

    var controlsVisible by remember { mutableStateOf(true) }
    var interactions by remember { mutableIntStateOf(0) }
    var scrubbing by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    fun showControls() {
        controlsVisible = true
        interactions++
    }
    // Controls fade out while playing, unless the user is using them.
    LaunchedEffect(controlsVisible, controller.isPlaying, interactions, scrubbing, menuOpen) {
        if (controlsVisible && controller.isPlaying && !scrubbing && !menuOpen) {
            delay(CONTROLS_HIDE_DELAY_MS)
            controlsVisible = false
        }
    }

    var feedback by remember { mutableStateOf<Feedback?>(null) }
    LaunchedEffect(feedback) {
        if (feedback != null) {
            delay(FEEDBACK_DURATION_MS)
            feedback = null
        }
    }

    val isReady = controller.durationMs > 0L

    // Load the ViewModel's subtitle file once the video is ready. A fresh controller (after
    // "Try again") starts at -1, so it loads the current file again.
    val subtitleFile = state.subtitleFile
    var loadedSubtitleId by remember { mutableIntStateOf(-1) }
    LaunchedEffect(subtitleFile, isReady) {
        if (subtitleFile != null && isReady && subtitleFile.id != loadedSubtitleId) {
            loadedSubtitleId = subtitleFile.id
            controller.addExternalSubtitle(subtitleFile.path, subtitleFile.language, subtitleFile.language)
            if (subtitleFile.announce) {
                feedback = Feedback(subtitleFile.language, Alignment.Center, (feedback?.id ?: 0) + 1)
            }
        }
    }

    // Resume from the saved position once the duration is known.
    LaunchedEffect(isReady) {
        if (isReady) {
            viewModel.resumePosition(controller.durationMs)?.let(controller::seekTo)
        }
    }

    fun currentPosition(): Long {
        val dur = controller.durationMs
        return if (controller.isEnded && dur > 0) dur else controller.positionMs
    }

    // Save the position every few seconds while the player is open.
    LaunchedEffect(controller) {
        while (true) {
            delay(PROGRESS_SAVE_INTERVAL_MS)
            viewModel.saveProgress(currentPosition(), controller.durationMs)
        }
    }

    // Mark the video finished as soon as it ends, and sync it.
    LaunchedEffect(controller.isEnded) {
        val dur = controller.durationMs
        if (controller.isEnded && dur > 0) viewModel.saveProgress(dur, dur, urgent = true)
    }

    // Play and pause save the position and sync it to the account (a few seconds later, so a
    // quick pause and resume is one request).
    LaunchedEffect(controller.isPlaying) {
        val dur = controller.durationMs
        if (dur > 0 && !controller.isEnded) viewModel.saveProgress(currentPosition(), dur, urgent = true)
    }

    // Save the final position on exit. It is read now, before the controller is released, and
    // saved on the app scope, since the ViewModel is being cleared too.
    DisposableEffect(controller) {
        onDispose { viewModel.saveFinalProgress(currentPosition(), controller.durationMs) }
    }

    fun onTurnOffSubtitles() {
        controller.selectSubtitleTrack(null)
        viewModel.onSubtitlesOff()
    }
    fun seekWithFeedback(deltaMs: Long) {
        val previous = feedback
        val forward = deltaMs > 0
        // Repeated taps on the same side add up, like in most mobile players.
        val total = if (previous != null && previous.seekMs != 0L && (previous.seekMs > 0) == forward) {
            previous.seekMs + deltaMs
        } else {
            deltaMs
        }
        controller.seekBy(deltaMs)
        feedback = Feedback(
            text = if (forward) "+${total / 1000}s" else "−${-total / 1000}s",
            alignment = if (forward) Alignment.CenterEnd else Alignment.CenterStart,
            id = (previous?.id ?: 0) + 1,
            seekMs = total,
        )
    }
    fun cycleFit() {
        fit = VideoFit.entries[(fit.ordinal + 1) % VideoFit.entries.size]
        feedback = Feedback(fit.label, Alignment.Center, (feedback?.id ?: 0) + 1)
        showControls()
    }

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                handleKey(
                    event = event,
                    controller = controller,
                    fullscreen = fullscreen,
                    onBack = onBack,
                    onCycleFit = ::cycleFit,
                    onInteraction = ::showControls,
                )
            }
            .pointerInput(Unit) {
                // Any mouse movement brings the controls back.
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (playerUsesMouse && event.type == PointerEventType.Move) showControls()
                    }
                }
            },
    ) {
        VideoSurface(controller = controller, fit = fit, modifier = Modifier.fillMaxSize())

        // Gestures on the video itself, below the controls.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(fullscreen) {
                    detectTapGestures(
                        onTap = {
                            if (playerUsesMouse) {
                                controller.togglePlay()
                                showControls()
                            } else if (controlsVisible) {
                                controlsVisible = false
                            } else {
                                showControls()
                            }
                        },
                        onDoubleTap = { offset ->
                            when {
                                playerUsesMouse -> fullscreen?.toggle?.invoke()
                                offset.x < size.width / 3f -> seekWithFeedback(-DOUBLE_TAP_SEEK_MS)
                                offset.x > size.width * 2f / 3f -> seekWithFeedback(DOUBLE_TAP_SEEK_MS)
                                else -> controller.togglePlay()
                            }
                        },
                    )
                },
        )

        if (controller.isBuffering && controller.error == null && !controlsVisible) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                CircularProgressIndicator(color = ControlTint, modifier = Modifier.size(48.dp))
                status?.invoke()
            }
        }

        feedback?.let { FeedbackBubble(it) }

        AnimatedVisibility(
            visible = controlsVisible || controller.error != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(Modifier.fillMaxSize()) {
                Box(
                    Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .height(140.dp)
                        .background(Brush.verticalGradient(listOf(Scrim, Color.Transparent))),
                )
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(170.dp)
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Scrim))),
                )

                Column(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
                ) {
                    TopBar(
                        title = title,
                        controller = controller,
                        subtitles = state,
                        onSearchSubdl = viewModel::searchSubdl,
                        onSearchOpenSubtitles = viewModel::searchOpenSubtitles,
                        onSearchYtsSubs = viewModel::searchYtsSubs,
                        onPickSubtitle = viewModel::pickSubtitle,
                        onTurnOffSubtitles = ::onTurnOffSubtitles,
                        onMenuOpenChange = { menuOpen = it; showControls() },
                        onBack = onBack,
                        onOpenExternal = onOpenExternal?.let { open -> { controller.pause(); open() } },
                    )
                    if (status != null) {
                        Box(Modifier.padding(start = 16.dp)) { status() }
                    }
                }

                val error = controller.error
                if (error != null) {
                    ErrorPanel(
                        message = error,
                        onAction = onRetry,
                        onOpenExternal = onOpenExternal,
                        modifier = Modifier.align(Alignment.Center),
                    )
                } else {
                    CenterControls(
                        controller = controller,
                        onSeek = { controller.seekBy(it); showControls() },
                        onTogglePlay = { controller.togglePlay(); showControls() },
                        modifier = Modifier.align(Alignment.Center),
                    )
                }

                BottomBar(
                    controller = controller,
                    fit = fit,
                    fullscreen = fullscreen,
                    onScrubbingChange = { scrubbing = it; showControls() },
                    onCycleFit = ::cycleFit,
                    onInteraction = ::showControls,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)),
                )
            }
        }
    }
}

private fun handleKey(
    event: KeyEvent,
    controller: VideoPlayerController,
    fullscreen: FullscreenController?,
    onBack: () -> Unit,
    onCycleFit: () -> Unit,
    onInteraction: () -> Unit,
): Boolean {
    if (event.type != KeyEventType.KeyDown) return false
    when (event.key) {
        Key.Spacebar, Key.K, Key.MediaPlayPause -> controller.togglePlay()
        Key.DirectionLeft -> controller.seekBy(-KEY_SEEK_MS)
        Key.DirectionRight -> controller.seekBy(KEY_SEEK_MS)
        Key.J -> controller.seekBy(-DOUBLE_TAP_SEEK_MS)
        Key.L -> controller.seekBy(DOUBLE_TAP_SEEK_MS)
        Key.M -> controller.setVolumeMuted(!controller.isMuted)
        Key.F -> fullscreen?.toggle?.invoke() ?: return false
        Key.A -> onCycleFit()
        Key.S, Key.C -> {
            if (controller.subtitleTracks.isNotEmpty()) {
                val currentIndex = controller.subtitleTracks.indexOf(controller.selectedSubtitleTrack)
                val next = if (currentIndex == controller.subtitleTracks.lastIndex) {
                    null
                } else {
                    controller.subtitleTracks[currentIndex + 1]
                }
                controller.selectSubtitleTrack(next)
            }
        }
        Key.Escape -> if (fullscreen?.isFullscreen == true) fullscreen.toggle() else onBack()
        else -> return false
    }
    onInteraction()
    return true
}
