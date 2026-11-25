package dev.dogetime.core

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import dev.dogetime.ui.player.LocalWindowState
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

@Composable
actual fun OnAppBackground(onBackground: () -> Unit) {
    val window = LocalWindowState.current ?: return
    val callback by rememberUpdatedState(onBackground)
    LaunchedEffect(window) {
        snapshotFlow { window.isMinimized }.distinctUntilChanged().filter { it }.collect { callback() }
    }
}
