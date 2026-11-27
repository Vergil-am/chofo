package dev.dogetime

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import dev.dogetime.navigation.Destination

internal data class Tab(val destination: Destination, val label: String, val icon: ImageVector)

/** Thin outline icons, tinted by the tab's selection color. */
private fun lineIcon(name: String, pathBuilder: PathBuilder.() -> Unit): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 1.8f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
        pathBuilder = pathBuilder,
    ).build()

private val HomeIcon = lineIcon("Home") {
    // House outline with a door.
    moveTo(4f, 10.5f)
    lineTo(12f, 4f)
    lineTo(20f, 10.5f)
    verticalLineTo(19.5f)
    horizontalLineTo(14.5f)
    verticalLineTo(14.5f)
    horizontalLineTo(9.5f)
    verticalLineTo(19.5f)
    horizontalLineTo(4f)
    close()
}

private val BrowseIcon = lineIcon("Browse") {
    // Compass: a circle with a tilted needle.
    moveTo(21f, 12f)
    arcTo(9f, 9f, 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = 3f, y1 = 12f)
    arcTo(9f, 9f, 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = 21f, y1 = 12f)
    close()
    moveTo(15.5f, 8.5f)
    lineTo(13.5f, 13.5f)
    lineTo(8.5f, 15.5f)
    lineTo(10.5f, 10.5f)
    close()
}

private val SearchIcon = lineIcon("Search") {
    // Magnifying glass.
    moveTo(17.5f, 11f)
    arcTo(6.5f, 6.5f, 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = 4.5f, y1 = 11f)
    arcTo(6.5f, 6.5f, 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = 17.5f, y1 = 11f)
    close()
    moveTo(15.8f, 15.8f)
    lineTo(20f, 20f)
}

private val LibraryIcon = lineIcon("Library") {
    // A stack: a framed card with two shelves behind it.
    moveTo(4f, 10f)
    horizontalLineTo(20f)
    verticalLineTo(20f)
    horizontalLineTo(4f)
    close()
    moveTo(6f, 7f)
    horizontalLineTo(18f)
    moveTo(8f, 4f)
    horizontalLineTo(16f)
    // A play mark on the card.
    moveTo(10.5f, 12.8f)
    lineTo(14.2f, 15f)
    lineTo(10.5f, 17.2f)
    close()
}

private val SettingsIcon = lineIcon("Settings") {
    // Sliders / settings icon
    moveTo(4f, 6f)
    lineTo(20f, 6f)
    moveTo(14f, 3.5f)
    verticalLineTo(8.5f)

    moveTo(4f, 12f)
    lineTo(20f, 12f)
    moveTo(8f, 9.5f)
    verticalLineTo(14.5f)

    moveTo(4f, 18f)
    lineTo(20f, 18f)
    moveTo(16f, 15.5f)
    verticalLineTo(20.5f)
}

internal val tabs = listOf(
    Tab(Destination.Home, "Home", HomeIcon),
    Tab(Destination.Browse, "Browse", BrowseIcon),
    Tab(Destination.Search, "Search", SearchIcon),
    Tab(Destination.Library, "Library", LibraryIcon),
    Tab(Destination.Settings, "Settings", SettingsIcon),
)
