package dev.dogetime.core

import androidx.compose.runtime.Composable

/**
 * Calls [onBackground] when the app leaves the screen: on Android when its activity stops, on
 * desktop when the window is minimized. Closing the desktop window goes through [AppShutdown].
 */
@Composable
expect fun OnAppBackground(onBackground: () -> Unit)

/**
 * Work to finish before the process exits. Desktop runs it when the window closes, for at most
 * a few seconds; Android needs nothing here (its activity stops first, which [OnAppBackground]
 * already covers).
 */
object AppShutdown {
    private val hooks = mutableListOf<suspend () -> Unit>()

    fun register(hook: suspend () -> Unit) {
        hooks += hook
    }

    suspend fun run() {
        hooks.forEach { runCatching { it() } }
    }
}
