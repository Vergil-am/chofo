package dev.dogetime.ui.details

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

internal val BackArrow = ImageVector.Builder(
    name = "BackArrow",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).path(
    stroke = SolidColor(Color.White),
    strokeLineWidth = 2.2f,
    strokeLineCap = StrokeCap.Round,
    strokeLineJoin = StrokeJoin.Round,
) {
    moveTo(19f, 12f)
    horizontalLineTo(5f)
    moveTo(11f, 6f)
    lineTo(5f, 12f)
    lineTo(11f, 18f)
}.build()

internal val HeartOutline = ImageVector.Builder(
    name = "HeartOutline",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).path(
    stroke = SolidColor(Color.White),
    strokeLineWidth = 2f,
    strokeLineCap = StrokeCap.Round,
    strokeLineJoin = StrokeJoin.Round,
) {
    // Heart path
    moveTo(12f, 21f)
    curveTo(12f, 21f, 3f, 15f, 3f, 9.5f)
    curveTo(3f, 7f, 5f, 5f, 7.5f, 5f)
    curveTo(9.5f, 5f, 11f, 6.5f, 12f, 7.5f)
    curveTo(13f, 6.5f, 14.5f, 5f, 16.5f, 5f)
    curveTo(19f, 5f, 21f, 7f, 21f, 9.5f)
    curveTo(21f, 15f, 12f, 21f, 12f, 21f)
    close()
}.build()

internal val HeartFilled = ImageVector.Builder(
    name = "HeartFilled",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).path(
    fill = SolidColor(Color.White),
    stroke = SolidColor(Color.White),
    strokeLineWidth = 1.5f,
    strokeLineCap = StrokeCap.Round,
    strokeLineJoin = StrokeJoin.Round,
) {
    moveTo(12f, 21f)
    curveTo(12f, 21f, 3f, 15f, 3f, 9.5f)
    curveTo(3f, 7f, 5f, 5f, 7.5f, 5f)
    curveTo(9.5f, 5f, 11f, 6.5f, 12f, 7.5f)
    curveTo(13f, 6.5f, 14.5f, 5f, 16.5f, 5f)
    curveTo(19f, 5f, 21f, 7f, 21f, 9.5f)
    curveTo(21f, 15f, 12f, 21f, 12f, 21f)
    close()
}.build()

internal val PlayIcon = ImageVector.Builder(
    name = "Play",
    defaultWidth = 20.dp,
    defaultHeight = 20.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).path(
    fill = SolidColor(Color.White),
) {
    moveTo(6f, 4f)
    lineTo(20f, 12f)
    lineTo(6f, 20f)
    close()
}.build()
