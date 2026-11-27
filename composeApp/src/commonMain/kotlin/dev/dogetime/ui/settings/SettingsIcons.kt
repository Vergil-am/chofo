package dev.dogetime.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

private val GithubIcon = ImageVector.Builder(
    name = "Github",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).path(fill = SolidColor(Color.White)) {
    moveTo(12f, 2f)
    curveTo(6.477f, 2f, 2f, 6.484f, 2f, 12.017f)
    curveTo(2f, 16.446f, 4.843f, 20.199f, 8.796f, 21.544f)
    curveTo(9.296f, 21.64f, 9.479f, 21.32f, 9.479f, 21.054f)
    curveTo(9.479f, 20.816f, 9.47f, 20.007f, 9.466f, 19.167f)
    curveTo(6.685f, 19.771f, 6.099f, 17.999f, 6.099f, 17.999f)
    curveTo(5.644f, 16.844f, 4.989f, 16.536f, 4.989f, 16.536f)
    curveTo(4.081f, 15.915f, 5.058f, 15.928f, 5.058f, 15.928f)
    curveTo(6.061f, 15.998f, 6.59f, 16.958f, 6.59f, 16.958f)
    curveTo(7.482f, 18.487f, 8.927f, 18.046f, 9.497f, 17.789f)
    curveTo(9.588f, 17.143f, 9.847f, 16.702f, 10.133f, 16.452f)
    curveTo(7.913f, 16.2f, 5.579f, 15.343f, 5.579f, 11.518f)
    curveTo(5.579f, 10.428f, 5.968f, 9.538f, 6.608f, 8.841f)
    curveTo(6.505f, 8.588f, 6.162f, 7.572f, 6.706f, 6.205f)
    curveTo(6.706f, 6.205f, 7.544f, 5.937f, 9.452f, 7.228f)
    curveTo(10.248f, 7.006f, 11.096f, 6.895f, 11.94f, 6.891f)
    curveTo(12.784f, 6.895f, 13.633f, 7.006f, 14.43f, 7.228f)
    curveTo(16.336f, 5.937f, 17.173f, 6.205f, 17.173f, 6.205f)
    curveTo(17.719f, 7.572f, 17.376f, 8.588f, 17.273f, 8.841f)
    curveTo(17.915f, 9.538f, 18.3f, 10.428f, 18.3f, 11.518f)
    curveTo(18.3f, 15.353f, 15.961f, 16.196f, 13.733f, 16.444f)
    curveTo(14.091f, 16.752f, 14.41f, 17.362f, 14.41f, 18.293f)
    curveTo(14.41f, 19.638f, 14.398f, 20.724f, 14.398f, 21.054f)
    curveTo(14.398f, 21.323f, 14.578f, 21.646f, 15.087f, 21.543f)
    curveTo(19.037f, 20.195f, 21.878f, 16.445f, 21.878f, 12.017f)
    curveTo(21.878f, 6.484f, 17.402f, 2f, 12f, 2f)
    close()
}.build()

private val InstagramIcon = ImageVector.Builder(
    name = "Instagram",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).path(
    stroke = SolidColor(Color.White),
    strokeLineWidth = 1.9f,
    strokeLineCap = StrokeCap.Round,
    strokeLineJoin = StrokeJoin.Round,
) {
    moveTo(7f, 2f)
    lineTo(17f, 2f)
    arcTo(5f, 5f, 0f, false, true, 22f, 7f)
    lineTo(22f, 17f)
    arcTo(5f, 5f, 0f, false, true, 17f, 22f)
    lineTo(7f, 22f)
    arcTo(5f, 5f, 0f, false, true, 2f, 17f)
    lineTo(2f, 7f)
    arcTo(5f, 5f, 0f, false, true, 7f, 2f)
    close()
    moveTo(16f, 12f)
    arcTo(4f, 4f, 0f, true, true, 8f, 12f)
    arcTo(4f, 4f, 0f, true, true, 16f, 12f)
    close()
    moveTo(17.5f, 6.5f)
    lineTo(17.51f, 6.5f)
}.build()

private val TwitterIcon = ImageVector.Builder(
    name = "TwitterX",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).path(fill = SolidColor(Color.White)) {
    moveTo(18.244f, 2.25f)
    horizontalLineTo(21.552f)
    lineTo(14.325f, 10.51f)
    lineTo(22.827f, 21.75f)
    horizontalLineTo(16.17f)
    lineTo(10.956f, 14.933f)
    lineTo(4.99f, 21.75f)
    horizontalLineTo(1.68f)
    lineTo(9.41f, 12.915f)
    lineTo(1.254f, 2.25f)
    horizontalLineTo(8.08f)
    lineTo(12.793f, 8.481f)
    close()
    moveTo(17.083f, 19.77f)
    horizontalLineTo(18.916f)
    lineTo(7.084f, 4.126f)
    horizontalLineTo(5.117f)
    close()
}.build()

private val TikTokIcon = ImageVector.Builder(
    name = "TikTok",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).path(fill = SolidColor(Color.White)) {
    moveTo(19.59f, 6.69f)
    arcTo(4.83f, 4.83f, 0f, false, true, 16.14f, 5.56f)
    arcTo(4.86f, 4.86f, 0f, false, true, 14.78f, 2.37f)
    horizontalLineTo(11.02f)
    verticalLineTo(16.38f)
    arcTo(3.29f, 3.29f, 0f, false, true, 7.73f, 19.67f)
    arcTo(3.3f, 3.3f, 0f, false, true, 4.44f, 16.38f)
    arcTo(3.3f, 3.3f, 0f, false, true, 7.73f, 13.09f)
    curveTo(8.11f, 13.09f, 8.47f, 13.16f, 8.8f, 13.29f)
    verticalLineTo(9.51f)
    arcTo(7.1f, 7.1f, 0f, false, false, 7.73f, 9.42f)
    arcTo(6.96f, 6.96f, 0f, false, false, 0.77f, 16.38f)
    arcTo(6.96f, 6.96f, 0f, false, false, 7.73f, 23.34f)
    arcTo(6.96f, 6.96f, 0f, false, false, 14.69f, 16.38f)
    verticalLineTo(9.38f)
    arcTo(8.46f, 8.46f, 0f, false, false, 19.59f, 10.98f)
    close()
}.build()

internal val ExternalLinkIcon = ImageVector.Builder(
    name = "ExternalLink",
    defaultWidth = 16.dp,
    defaultHeight = 16.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).path(
    stroke = SolidColor(Color.White),
    strokeLineWidth = 2.2f,
    strokeLineCap = StrokeCap.Round,
    strokeLineJoin = StrokeJoin.Round,
) {
    moveTo(7f, 17f)
    lineTo(17f, 7f)
    moveTo(7f, 7f)
    horizontalLineTo(17f)
    verticalLineTo(17f)
}.build()

internal val RefreshIcon = ImageVector.Builder(
    name = "Refresh",
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
    moveTo(23f, 4f)
    verticalLineTo(10f)
    horizontalLineTo(17f)
    moveTo(1f, 20f)
    verticalLineTo(14f)
    horizontalLineTo(7f)
    moveTo(3.51f, 9f)
    arcTo(9f, 9f, 0f, false, true, 18.36f, 5.64f)
    lineTo(23f, 10f)
    moveTo(1f, 14f)
    lineTo(5.64f, 18.36f)
    arcTo(9f, 9f, 0f, false, false, 20.49f, 15f)
}.build()

internal val CheckIcon = ImageVector.Builder(
    name = "Check",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).path(
    stroke = SolidColor(Color.White),
    strokeLineWidth = 2.4f,
    strokeLineCap = StrokeCap.Round,
    strokeLineJoin = StrokeJoin.Round,
) {
    moveTo(20f, 6f)
    lineTo(9f, 17f)
    lineTo(4f, 12f)
}.build()

internal val DownloadIcon = ImageVector.Builder(
    name = "Download",
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
    moveTo(12f, 3f)
    lineTo(12f, 15f)
    moveTo(7f, 10f)
    lineTo(12f, 15f)
    lineTo(17f, 10f)
    moveTo(4f, 19f)
    horizontalLineTo(20f)
}.build()

internal val ShieldIcon = ImageVector.Builder(
    name = "Shield",
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
    moveTo(12f, 22f)
    curveTo(12f, 22f, 20f, 18f, 20f, 12f)
    verticalLineTo(5f)
    lineTo(12f, 2f)
    lineTo(4f, 5f)
    verticalLineTo(12f)
    curveTo(4f, 18f, 12f, 22f, 12f, 22f)
    close()
}.build()

internal data class SocialLink(
    val platform: String,
    val username: String,
    val url: String,
    val icon: ImageVector,
    val accentColor: Color,
)

internal val SocialLinks = listOf(
    SocialLink(
        platform = "GitHub",
        username = "Vergil-am",
        url = "https://github.com/Vergil-am",
        icon = GithubIcon,
        accentColor = Color(0xFFE2E8F0),
    ),
    SocialLink(
        platform = "Instagram",
        username = "@vergil_am",
        url = "https://www.instagram.com/vergil_am/",
        icon = InstagramIcon,
        accentColor = Color(0xFFE1306C),
    ),
    SocialLink(
        platform = "Twitter / X",
        username = "@Vergil_am",
        url = "https://x.com/Vergil_am",
        icon = TwitterIcon,
        accentColor = Color(0xFF38BDF8),
    ),
    SocialLink(
        platform = "TikTok",
        username = "@vergil_am",
        url = "https://www.tiktok.com/@vergil_am",
        icon = TikTokIcon,
        accentColor = Color(0xFF25F4EE),
    ),
)
