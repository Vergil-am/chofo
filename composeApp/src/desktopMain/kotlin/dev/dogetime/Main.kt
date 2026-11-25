package dev.dogetime

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import dev.dogetime.core.AppShutdown
import dev.dogetime.core.DesktopWebEngine
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import dev.dogetime.ui.player.LocalWindowState

fun main() = application {
    val windowState = rememberWindowState(size = DpSize(420.dp, 720.dp))
    Window(
        onCloseRequest = {
            // Give unsynced watch progress a few seconds to reach the account before exiting.
            runBlocking { withTimeoutOrNull(3_000) { AppShutdown.run() } }
            DesktopWebEngine.dispose()
            exitApplication()
        },
        title = "Chofo",
        state = windowState,
    ) {
        CompositionLocalProvider(LocalWindowState provides windowState) {
            App()
        }
    }
}
