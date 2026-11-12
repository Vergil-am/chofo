package dev.dogetime.ui.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.dogetime.AppConfig
import dev.dogetime.AppTheme
import dev.dogetime.data.UpdateChecker
import dev.dogetime.data.UpdateStatus
import kotlinx.coroutines.launch

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

private val ExternalLinkIcon = ImageVector.Builder(
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

private val RefreshIcon = ImageVector.Builder(
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

private val CheckIcon = ImageVector.Builder(
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

private val DownloadIcon = ImageVector.Builder(
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

private val ShieldIcon = ImageVector.Builder(
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

private data class SocialLink(
    val platform: String,
    val username: String,
    val url: String,
    val icon: ImageVector,
    val accentColor: Color,
)

private val SocialLinks = listOf(
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

@Composable
fun SettingsScreen(
    currentTheme: AppTheme,
    onThemeSelect: (AppTheme) -> Unit,
    updateChecker: UpdateChecker? = null,
    modifier: Modifier = Modifier,
    bottomInset: Dp = 0.dp,
) {
    val scrollState = rememberScrollState()
    val uriHandler = LocalUriHandler.current
    val coroutineScope = rememberCoroutineScope()

    var updateStatus by remember { mutableStateOf<UpdateStatus>(UpdateStatus.Idle) }

    fun triggerCheck() {
        if (updateChecker != null && updateStatus !is UpdateStatus.Checking) {
            updateStatus = UpdateStatus.Checking
            coroutineScope.launch {
                updateStatus = updateChecker.checkForUpdates(AppConfig.APP_VERSION)
            }
        }
    }

    LaunchedEffect(updateChecker) {
        if (updateChecker != null && updateStatus is UpdateStatus.Idle) {
            triggerCheck()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 24.dp)
            .padding(bottom = bottomInset + 32.dp),
    ) {
        Spacer(Modifier.height(20.dp))

        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = "Preferences and application customization",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(28.dp))

        // Appearance / Themes Section
        Text(
            text = "Appearance",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Choose your color theme palette",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(14.dp))

        Column(
            modifier = Modifier.widthIn(max = 680.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AppTheme.entries.forEach { theme ->
                val isSelected = theme == currentTheme
                val borderColor by animateColorAsState(
                    if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                )
                val bgColor by animateColorAsState(
                    if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                    else MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f),
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(bgColor)
                        .border(1.5.dp, borderColor, RoundedCornerShape(16.dp))
                        .clickable { onThemeSelect(theme) }
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = theme.displayName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            )
                        }

                        Spacer(Modifier.height(4.dp))

                        Text(
                            text = theme.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        Spacer(Modifier.height(12.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            theme.previewColors.forEach { color ->
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .background(color)
                                        .border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape),
                                )
                            }
                        }
                    }

                    RadioButton(
                        selected = isSelected,
                        onClick = { onThemeSelect(theme) },
                        colors = RadioButtonDefaults.colors(
                            selectedColor = MaterialTheme.colorScheme.primary,
                            unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    )
                }
            }
        }

        Spacer(Modifier.height(36.dp))

        // Connect & Socials Section
        Text(
            text = "Connect",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Follow developer social profiles and repositories",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(14.dp))

        Column(
            modifier = Modifier.widthIn(max = 680.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SocialLinks.forEach { social ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f))
                        .border(1.dp, MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp))
                        .clickable {
                            runCatching { uriHandler.openUri(social.url) }
                        }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(social.accentColor.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = social.icon,
                            contentDescription = social.platform,
                            tint = social.accentColor,
                            modifier = Modifier.size(20.dp),
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = social.platform,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = social.username,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    Icon(
                        imageVector = ExternalLinkIcon,
                        contentDescription = "Open ${social.platform}",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }

        Spacer(Modifier.height(36.dp))

        // App Updates Section
        Text(
            text = "Updates",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Check for new releases and application updates",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(14.dp))

        Card(
            modifier = Modifier
                .widthIn(max = 680.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = when (updateStatus) {
                    is UpdateStatus.UpdateAvailable -> MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                    else -> MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f)
                },
            ),
            border = if (updateStatus is UpdateStatus.UpdateAvailable) {
                BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
            } else {
                BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
            },
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when (val status = updateStatus) {
                    UpdateStatus.Idle -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column {
                                Text(
                                    text = "Current Version: v${AppConfig.APP_VERSION}",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = "Tap to check for updates from GitHub",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Button(
                                onClick = ::triggerCheck,
                                shape = RoundedCornerShape(12.dp),
                            ) {
                                Icon(RefreshIcon, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Check for updates")
                            }
                        }
                    }

                    UpdateStatus.Checking -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.5.dp,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Column {
                                Text(
                                    text = "Checking for updates...",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = "Connecting to github.com/${AppConfig.RELEASES_REPO}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    is UpdateStatus.UpToDate -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFA6E3A1).copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = CheckIcon,
                                        contentDescription = null,
                                        tint = Color(0xFFA6E3A1),
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                                Column {
                                    Text(
                                        text = "You're up to date!",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    Text(
                                        text = "Version ${status.version} is currently the latest release",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            OutlinedButton(
                                onClick = ::triggerCheck,
                                shape = RoundedCornerShape(12.dp),
                            ) {
                                Text("Check again")
                            }
                        }
                    }

                    is UpdateStatus.UpdateAvailable -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = DownloadIcon,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Text(
                                        text = "Update Available",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(MaterialTheme.colorScheme.primary)
                                            .padding(horizontal = 6.dp, vertical = 2.dp),
                                    ) {
                                        Text(
                                            text = status.latestVersion,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimary,
                                        )
                                    }
                                }
                                Text(
                                    text = status.releaseName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = "Current: v${AppConfig.APP_VERSION} → Latest: ${status.latestVersion}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        if (!status.releaseNotes.isNullOrBlank()) {
                            Card(
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                                ),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = "What's New:",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = status.releaseNotes,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 6,
                                    )
                                }
                            }
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Button(
                                onClick = { runCatching { uriHandler.openUri(status.downloadUrl) } },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(DownloadIcon, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Download Update", fontWeight = FontWeight.SemiBold)
                            }
                            OutlinedButton(
                                onClick = { runCatching { uriHandler.openUri(status.releaseUrl) } },
                                shape = RoundedCornerShape(12.dp),
                            ) {
                                Icon(ExternalLinkIcon, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("View on GitHub")
                            }
                        }
                    }

                    is UpdateStatus.Error -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                Text(
                                    text = "Couldn't check for updates",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.error,
                                )
                                Text(
                                    text = status.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                )
                            }
                            Button(
                                onClick = ::triggerCheck,
                                shape = RoundedCornerShape(12.dp),
                            ) {
                                Text("Retry")
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(36.dp))

        // Legal Disclaimer & Terms of Use Section
        Text(
            text = "Legal Disclaimer & Terms of Use",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Content hosting, indexer limitations, and terms of service",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(14.dp))

        Card(
            modifier = Modifier
                .widthIn(max = 680.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f),
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = ShieldIcon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Column {
                        Text(
                            text = "Non-Hosting Policy & Legal Notice",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "Please read these terms carefully before using ${AppConfig.APP_NAME}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                )

                LegalSectionItem(
                    title = "1. No Media Hosting",
                    description = "${AppConfig.APP_NAME} does not host, upload, store, archive, or transmit any video files, media streams, torrents, or copyrighted content on any server. None of the media or streams accessible through this application are hosted on servers owned, managed, or operated by the developers.",
                )

                LegalSectionItem(
                    title = "2. Public Indexing & Third-Party APIs",
                    description = "This application operates exclusively as a client-side search engine and media browser. Movie and TV show metadata, descriptions, and artwork are retrieved from public community databases (such as TMDB and Kitsu). Media playback links are parsed dynamically from publicly available third-party websites across the web.",
                )

                LegalSectionItem(
                    title = "3. Third-Party Content & Liability",
                    description = "The developers of ${AppConfig.APP_NAME} have no control over, and accept no liability for, the legality, copyright compliance, uptime, privacy policies, or content of external third-party hosts or servers. Linking to or displaying content from third-party services does not constitute an endorsement.",
                )

                LegalSectionItem(
                    title = "4. Copyright & DMCA Notices",
                    description = "All trademarks, logos, registered marks, and copyrighted materials belong to their respective owners. Because ${AppConfig.APP_NAME} does not host or store any files, copyright infringement notices or DMCA takedown requests must be directed to the third-party web hosts actually hosting the content.",
                )

                LegalSectionItem(
                    title = "5. Terms of Use & User Responsibility",
                    description = "This application is provided for personal, informational, and educational discovery purposes on an 'AS IS' basis without warranty of any kind. Users are solely responsible for ensuring their usage complies with all applicable local laws, regulations, and copyright restrictions in their jurisdiction.",
                )
            }
        }

        Spacer(Modifier.height(36.dp))

        // About section
        Text(
            text = "About",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )

        Spacer(Modifier.height(14.dp))

        Card(
            modifier = Modifier
                .widthIn(max = 680.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f),
            ),
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = AppConfig.APP_NAME,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Version ${AppConfig.APP_VERSION}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "A modern streaming app built with Compose Multiplatform. Media data sourced from TMDB and Kitsu.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun LegalSectionItem(
    title: String,
    description: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
