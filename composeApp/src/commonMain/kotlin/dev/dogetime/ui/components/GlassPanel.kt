package dev.dogetime.ui.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.glass.GlassStyle

/** Shape of the frosted panels: the details screen's episodes and the player's side panels. */
val GlassPanelShape = RoundedCornerShape(24.dp)

/** Frosted glass for a panel over a backdrop or the video, tinted with the theme's surface. */
@OptIn(ExperimentalHazeApi::class)
@Composable
fun rememberGlassPanelStyle(): GlassStyle {
    val bg = MaterialTheme.colorScheme.surface
    return remember(bg) {
        GlassStyle.regular.then {
            shape(GlassPanelShape)
            tint(bg.copy(alpha = 0.35f))
        }
    }
}
