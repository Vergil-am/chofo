package dev.dogetime.core

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember

/**
 * A ViewModel for one screen: made by [create] the first time, and again whenever [key] changes.
 * It is cleared when the screen leaves composition or [key] changes.
 */
@Composable
fun <VM : ViewModel> rememberViewModel(key: Any?, create: () -> VM): VM {
    val viewModel = remember(key) { create() }
    DisposableEffect(viewModel) {
        onDispose { viewModel.clear() }
    }
    return viewModel
}
