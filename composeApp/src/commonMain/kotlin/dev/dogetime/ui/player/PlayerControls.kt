package dev.dogetime.ui.player

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.dogetime.domain.Subtitle

private val SPEEDS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)

internal val Scrim = Color.Black.copy(alpha = 0.72f)

internal val ControlTint = Color.White

@Composable
internal fun TopBar(
    title: String,
    controller: VideoPlayerController,
    subtitles: PlayerUiState,
    onSearchSubdl: () -> Unit,
    onSearchOpenSubtitles: () -> Unit,
    onSearchYtsSubs: () -> Unit,
    onPickSubtitle: (Subtitle) -> Unit,
    onTurnOffSubtitles: () -> Unit,
    onMenuOpenChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    onOpenExternal: (() -> Unit)?,
    modifier: Modifier = Modifier,
    /** Buttons for the side panels (label and icon); [onOpenPanel] gets the index of the one pressed. */
    panels: List<Pair<String, ImageVector>> = emptyList(),
    onOpenPanel: (Int) -> Unit = {},
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PlayerIconButton(PlayerIcons.Back, "Back", onClick = onBack)
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = ControlTint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
        )
        if (controller.qualities.isNotEmpty()) {
            val playing = controller.videoHeight.takeIf { it > 0 }?.let { "${it}p" }
            val selected = controller.selectedQuality
            // null stands for "Auto".
            PillMenu(
                icon = PlayerIcons.Quality,
                label = selected?.label ?: listOfNotNull("Auto", playing).joinToString(" · "),
                options = listOf<VideoQuality?>(null) + controller.qualities,
                optionLabel = { it?.label ?: "Auto" },
                isSelected = { it == selected },
                onSelect = controller::selectQuality,
                onOpenChange = onMenuOpenChange,
            )
        }
        // Only worth a menu when the file has a choice, like a dub and the original.
        if (controller.audioTracks.size > 1) {
            val selected = controller.selectedAudioTrack
            PillMenu(
                icon = PlayerIcons.Audio,
                label = selected?.shortLabel ?: "Audio",
                options = controller.audioTracks,
                optionLabel = { it.label },
                isSelected = { it.id == selected?.id },
                onSelect = controller::selectAudioTrack,
                onOpenChange = onMenuOpenChange,
            )
        }
        val hasSubs = controller.subtitleTracks.isNotEmpty() ||
            subtitles.canSearchSubdl || subtitles.canSearchOpenSubtitles || subtitles.canSearchYtsSubs ||
            subtitles.subtitleGroups.isNotEmpty() || subtitles.isLoadingAnySubtitles
        if (hasSubs) {
            SubtitlesMenu(
                controller = controller,
                subtitles = subtitles,
                onSearchSubdl = onSearchSubdl,
                onSearchOpenSubtitles = onSearchOpenSubtitles,
                onSearchYtsSubs = onSearchYtsSubs,
                onPickSubtitle = onPickSubtitle,
                onTurnOffSubtitles = onTurnOffSubtitles,
                onOpenChange = onMenuOpenChange,
            )
        }
        PillMenu(
            icon = PlayerIcons.Speed,
            label = formatSpeed(controller.speed),
            options = SPEEDS,
            optionLabel = ::formatSpeed,
            isSelected = { it == controller.speed },
            onSelect = controller::setPlaybackSpeed,
            onOpenChange = onMenuOpenChange,
        )
        panels.forEachIndexed { index, (label, icon) ->
            Pill(onClick = { onOpenPanel(index) }) {
                Icon(icon, contentDescription = null, tint = ControlTint, modifier = Modifier.size(16.dp))
                Text(label, color = ControlTint, style = MaterialTheme.typography.labelLarge)
            }
        }
        if (onOpenExternal != null) {
            PlayerIconButton(PlayerIcons.OpenExternal, "Open in another player", onClick = onOpenExternal)
        }
    }
}

/** A [Pill] that opens a menu of [options], with the selected one highlighted. */
@Composable
private fun <T> PillMenu(
    icon: ImageVector,
    label: String,
    options: List<T>,
    optionLabel: (T) -> String,
    isSelected: (T) -> Boolean,
    onSelect: (T) -> Unit,
    onOpenChange: (Boolean) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    fun setOpen(value: Boolean) {
        open = value
        onOpenChange(value)
    }
    Box {
        Pill(onClick = { setOpen(true) }) {
            Icon(icon, contentDescription = null, tint = ControlTint, modifier = Modifier.size(16.dp))
            Text(label, color = ControlTint, style = MaterialTheme.typography.labelLarge)
        }
        DropdownMenu(expanded = open, onDismissRequest = { setOpen(false) }) {
            options.forEach { option ->
                val selected = isSelected(option)
                DropdownMenuItem(
                    text = {
                        Text(
                            text = optionLabel(option),
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        )
                    },
                    onClick = {
                        onSelect(option)
                        setOpen(false)
                    },
                )
            }
        }
    }
}

@Composable
internal fun CenterControls(
    controller: VideoPlayerController,
    onSeek: (Long) -> Unit,
    onTogglePlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(36.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SeekButton(PlayerIcons.Replay, "Back 10 seconds", onClick = { onSeek(-DOUBLE_TAP_SEEK_MS) })
        Box(
            modifier = Modifier
                .size(76.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.35f))
                .clickable(onClick = onTogglePlay),
            contentAlignment = Alignment.Center,
        ) {
            if (controller.isBuffering) {
                CircularProgressIndicator(color = ControlTint, strokeWidth = 3.dp, modifier = Modifier.size(44.dp))
            } else {
                val playing = controller.isPlaying && !controller.isEnded
                Icon(
                    imageVector = if (playing) PlayerIcons.Pause else PlayerIcons.Play,
                    contentDescription = if (playing) "Pause" else "Play",
                    tint = ControlTint,
                    modifier = Modifier.size(44.dp),
                )
            }
        }
        SeekButton(PlayerIcons.Forward, "Forward 10 seconds", onClick = { onSeek(DOUBLE_TAP_SEEK_MS) })
    }
}

/** Circular arrow with "10" in the middle. */
@Composable
private fun SeekButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(56.dp).clip(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = ControlTint, modifier = Modifier.size(36.dp))
        Text(
            text = "10",
            color = ControlTint,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
internal fun BottomBar(
    controller: VideoPlayerController,
    fit: VideoFit,
    fullscreen: FullscreenController?,
    onScrubbingChange: (Boolean) -> Unit,
    onCycleFit: () -> Unit,
    onInteraction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // While scrubbing, the bar and the time follow the finger instead of playback.
    var scrubMs by remember { mutableStateOf<Long?>(null) }
    val shownMs = scrubMs ?: controller.positionMs
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TimeLabel(shownMs)
            if (controller.durationMs > 0) {
                SeekBar(
                    positionMs = shownMs,
                    durationMs = controller.durationMs,
                    bufferedMs = controller.bufferedMs,
                    onScrub = { ms ->
                        if (scrubMs == null) onScrubbingChange(true)
                        scrubMs = ms
                    },
                    onScrubEnd = { ms ->
                        controller.seekTo(ms)
                        scrubMs = null
                        onScrubbingChange(false)
                    },
                    modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                )
                TimeLabel(controller.durationMs)
            } else {
                Spacer(Modifier.weight(1f))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            PlayerIconButton(
                icon = if (controller.isMuted) PlayerIcons.VolumeOff else PlayerIcons.Volume,
                description = if (controller.isMuted) "Unmute" else "Mute",
                onClick = { controller.setVolumeMuted(!controller.isMuted); onInteraction() },
            )
            Spacer(Modifier.weight(1f))
            Pill(onClick = onCycleFit) {
                Icon(PlayerIcons.AspectRatio, contentDescription = "Change fit", tint = ControlTint, modifier = Modifier.size(16.dp))
                Text(fit.label, color = ControlTint, style = MaterialTheme.typography.labelLarge)
            }
            if (fullscreen != null) {
                PlayerIconButton(
                    icon = if (fullscreen.isFullscreen) PlayerIcons.FullscreenExit else PlayerIcons.Fullscreen,
                    description = if (fullscreen.isFullscreen) "Exit fullscreen" else "Fullscreen",
                    onClick = { fullscreen.toggle(); onInteraction() },
                )
            }
        }
    }
}

/** Thin seek bar with the downloaded part shown behind the played part. It thickens while dragged. */
@Composable
private fun SeekBar(
    positionMs: Long,
    durationMs: Long,
    bufferedMs: Long,
    onScrub: (Long) -> Unit,
    onScrubEnd: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    val trackHeight by animateDpAsState(if (dragging) 6.dp else 4.dp)
    val thumbRadius by animateDpAsState(if (dragging) 9.dp else 6.dp)
    val accent = MaterialTheme.colorScheme.primary
    fun msAt(fraction: Float) = (fraction.coerceIn(0f, 1f) * durationMs).toLong()

    Canvas(
        modifier = modifier
            .height(28.dp)
            .pointerInput(durationMs) {
                detectTapGestures { offset -> onScrubEnd(msAt(offset.x / size.width)) }
            }
            .pointerInput(durationMs) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        dragging = true
                        dragFraction = offset.x / size.width
                        onScrub(msAt(dragFraction))
                    },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        dragFraction = change.position.x / size.width
                        onScrub(msAt(dragFraction))
                    },
                    onDragEnd = {
                        dragging = false
                        onScrubEnd(msAt(dragFraction))
                    },
                    onDragCancel = {
                        dragging = false
                        onScrubEnd(msAt(dragFraction))
                    },
                )
            },
    ) {
        val played = (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
        val buffered = (bufferedMs.toFloat() / durationMs).coerceIn(0f, 1f)
        val height = trackHeight.toPx()
        val top = (size.height - height) / 2
        val radius = CornerRadius(height / 2)
        fun bar(fraction: Float, color: Color) = drawRoundRect(
            color = color,
            topLeft = Offset(0f, top),
            size = Size(size.width * fraction, height),
            cornerRadius = radius,
        )
        bar(1f, Color.White.copy(alpha = 0.25f))
        bar(buffered, Color.White.copy(alpha = 0.45f))
        bar(played, accent)
        drawCircle(color = accent, radius = thumbRadius.toPx(), center = Offset(size.width * played, size.height / 2))
    }
}

@Composable
private fun TimeLabel(ms: Long) {
    Text(
        text = formatTime(ms),
        color = ControlTint,
        style = MaterialTheme.typography.labelMedium,
        fontFamily = FontFamily.Monospace,
    )
}

@Composable
internal fun ErrorPanel(
    message: String,
    onAction: () -> Unit,
    onOpenExternal: (() -> Unit)?,
    modifier: Modifier = Modifier,
    actionLabel: String = "Try again",
) {
    Column(
        modifier = modifier
            .widthIn(max = 420.dp)
            .padding(24.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.92f))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Couldn't play this video", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onAction) { Text(actionLabel) }
            if (onOpenExternal != null) {
                OutlinedButton(onClick = onOpenExternal) { Text("Other player") }
            }
        }
    }
}

@Composable
internal fun FeedbackBubble(feedback: Feedback) {
    Box(Modifier.fillMaxSize().padding(horizontal = 48.dp), contentAlignment = feedback.alignment) {
        Text(
            text = feedback.text,
            color = ControlTint,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(Color.Black.copy(alpha = 0.55f))
                .padding(horizontal = 18.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun PlayerIconButton(icon: ImageVector, description: String, size: Dp = 24.dp, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(icon, contentDescription = description, tint = ControlTint, modifier = Modifier.size(size))
    }
}

/** Small rounded button with an icon and a label, e.g. the speed and fit toggles. */
@Composable
internal fun Pill(onClick: () -> Unit, content: @Composable () -> Unit) {
    Row(
        modifier = Modifier
            .padding(horizontal = 4.dp)
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.14f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        content()
    }
}

private fun formatSpeed(speed: Float): String {
    val text = if (speed * 100 % 10 == 0f) {
        ((speed * 10).toInt() / 10.0).toString()
    } else {
        ((speed * 100).toInt() / 100.0).toString()
    }
    return "${text}×"
}

private fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = totalSeconds % 3600 / 60
    val seconds = totalSeconds % 60
    fun two(n: Long) = n.toString().padStart(2, '0')
    return if (hours > 0) "$hours:${two(minutes)}:${two(seconds)}" else "$minutes:${two(seconds)}"
}
