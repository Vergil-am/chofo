package dev.dogetime.ui.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.dogetime.AppConfig
import dev.dogetime.AppTheme
import dev.dogetime.data.PreferencesRepository

/** A section title, with an optional line under it. */
@Composable
internal fun SectionHeader(title: String, subtitle: String? = null) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
    )
    if (subtitle != null) {
        Spacer(Modifier.height(4.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Spacer(Modifier.height(14.dp))
}

/** How much of a torrent to download before playback starts. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PlaybackSection(torrentBufferMb: Int, onTorrentBufferChange: (Int) -> Unit) {
    SectionHeader(
        "Playback",
        "Download part of a torrent before it starts playing, to avoid early stalls on slow torrents",
    )
    FlowRow(
        modifier = Modifier.widthIn(max = 680.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PreferencesRepository.TORRENT_BUFFER_OPTIONS_MB.forEach { mb ->
            FilterChip(
                selected = mb == torrentBufferMb,
                onClick = { onTorrentBufferChange(mb) },
                label = { Text(if (mb == 0) "Off" else "$mb MB") },
            )
        }
    }
}

@Composable
internal fun ThemeSection(currentTheme: AppTheme, onThemeSelect: (AppTheme) -> Unit) {
    SectionHeader("Appearance", "Choose your color theme palette")
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
}

@Composable
internal fun SocialsSection() {
    val uriHandler = LocalUriHandler.current
    SectionHeader("Connect", "Follow developer social profiles and repositories")
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
}

@Composable
internal fun LegalSection() {
    SectionHeader("Legal Disclaimer & Terms of Use", "Content hosting, indexer limitations, and terms of service")
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
}

@Composable
internal fun AboutSection() {
    SectionHeader("About")
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
