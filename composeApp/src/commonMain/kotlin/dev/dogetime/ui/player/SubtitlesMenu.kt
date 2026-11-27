package dev.dogetime.ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.dogetime.domain.Subtitle
import dev.dogetime.domain.SubtitleGroup

@Composable
internal fun SubtitlesMenu(
    controller: VideoPlayerController,
    subtitles: PlayerUiState,
    onSearchSubdl: () -> Unit,
    onSearchOpenSubtitles: () -> Unit,
    onSearchYtsSubs: () -> Unit,
    onPickSubtitle: (Subtitle) -> Unit,
    onTurnOffSubtitles: () -> Unit,
    onOpenChange: (Boolean) -> Unit,
) {
    val activeLanguage = subtitles.activeSubtitleLanguage
    val subtitleGroups = subtitles.subtitleGroups
    val subdlGroups = subtitles.subdl.groups
    val openSubtitlesGroups = subtitles.openSubtitles.groups
    val ytsSubsGroups = subtitles.ytsSubs.groups
    val isLoadingSubdl = subtitles.subdl.isLoading
    val isLoadingOpenSubtitles = subtitles.openSubtitles.isLoading
    val isLoadingYtsSubs = subtitles.ytsSubs.isLoading
    val isLoadingStremioSubtitles = subtitles.stremio.isLoading
    val hasSearchedSubdl = subtitles.subdl.hasSearched
    val hasSearchedOpenSubtitles = subtitles.openSubtitles.hasSearched
    val hasSearchedYtsSubs = subtitles.ytsSubs.hasSearched
    val canSearchSubdl = subtitles.canSearchSubdl
    val canSearchOpenSubtitles = subtitles.canSearchOpenSubtitles
    val canSearchYtsSubs = subtitles.canSearchYtsSubs
    val downloadingSubtitleUrl = subtitles.downloadingSubtitleUrl

    var open by remember { mutableStateOf(false) }
    var selectedLanguageGroup by remember { mutableStateOf<SubtitleGroup?>(null) }

    fun setOpen(value: Boolean) {
        open = value
        if (!value) {
            selectedLanguageGroup = null
        }
        onOpenChange(value)
    }

    val selectedTrack = controller.selectedSubtitleTrack
    // Show only the language on top, never the full subtitle release name
    val topLabel = when {
        selectedTrack == null -> "Subtitles Off"
        !activeLanguage.isNullOrBlank() -> activeLanguage
        !selectedTrack.language.isNullOrBlank() -> selectedTrack.language
        else -> "Subtitles On"
    }

    Box {
        Pill(onClick = { setOpen(true) }) {
            Icon(PlayerIcons.Subtitles, contentDescription = "Subtitles", tint = ControlTint, modifier = Modifier.size(16.dp))
            Text(topLabel, color = ControlTint, style = MaterialTheme.typography.labelLarge)
        }

        DropdownMenu(
            expanded = open,
            onDismissRequest = { setOpen(false) },
            modifier = Modifier.widthIn(min = 220.dp, max = 340.dp).heightIn(max = 380.dp),
        ) {
            val currentGroup = selectedLanguageGroup
            if (currentGroup == null) {
                // Top-level menu: shows "Off", then available stream tracks
                DropdownMenuItem(
                    text = {
                        Text(
                            text = "Off",
                            fontWeight = if (selectedTrack == null) FontWeight.Bold else FontWeight.Normal,
                            color = if (selectedTrack == null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        )
                    },
                    onClick = {
                        onTurnOffSubtitles()
                        setOpen(false)
                    },
                )

                // Available track subtitles (embedded in the media/stream)
                val otherTracks = controller.subtitleTracks.filter { track ->
                    subtitleGroups.none { group -> group.language.equals(track.language, ignoreCase = true) }
                }
                if (otherTracks.isNotEmpty()) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    otherTracks.forEach { track ->
                        val isTrackSelected = track == selectedTrack
                        val trackLabel = track.language?.takeIf { it.isNotBlank() } ?: track.label
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = trackLabel,
                                    fontWeight = if (isTrackSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isTrackSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                )
                            },
                            onClick = {
                                controller.selectSubtitleTrack(track)
                                setOpen(false)
                            },
                        )
                    }
                }

                // If subtitles are currently being auto-fetched from Stremio add-ons
                if (isLoadingStremioSubtitles) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    DropdownMenuItem(
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Text("Fetching subtitles...", style = MaterialTheme.typography.bodyMedium)
                            }
                        },
                        enabled = false,
                        onClick = {},
                    )
                }

                // If any subtitles have been found, display language groups
                if (subtitleGroups.isNotEmpty()) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    subtitleGroups.forEach { group ->
                        val isLangActive = activeLanguage.equals(group.language, ignoreCase = true)
                        DropdownMenuItem(
                            text = {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = group.language,
                                        fontWeight = if (isLangActive) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isLangActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                    )
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        Text(
                                            text = "${group.subtitles.size}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        Text(
                                            text = "›",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            },
                            onClick = {
                                selectedLanguageGroup = group
                            },
                        )
                    }
                }

                // YTS Subs search action / status
                if (!hasSearchedYtsSubs && canSearchYtsSubs) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    DropdownMenuItem(
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(PlayerIcons.Subtitles, contentDescription = null, modifier = Modifier.size(18.dp))
                                Text("Search YTS Subs", fontWeight = FontWeight.SemiBold)
                            }
                        },
                        onClick = onSearchYtsSubs,
                    )
                } else if (isLoadingYtsSubs) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    DropdownMenuItem(
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Text("Searching YTS Subs...", style = MaterialTheme.typography.bodyMedium)
                            }
                        },
                        enabled = false,
                        onClick = {},
                    )
                } else if (hasSearchedYtsSubs && ytsSubsGroups.isEmpty()) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = "No YTS subtitles found",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        enabled = false,
                        onClick = {},
                    )
                }

                // SubDL search action / status
                if (!hasSearchedSubdl && canSearchSubdl) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    DropdownMenuItem(
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(PlayerIcons.Subtitles, contentDescription = null, modifier = Modifier.size(18.dp))
                                Text("Search SubDL", fontWeight = FontWeight.SemiBold)
                            }
                        },
                        onClick = onSearchSubdl,
                    )
                } else if (isLoadingSubdl) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    DropdownMenuItem(
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Text("Searching SubDL...", style = MaterialTheme.typography.bodyMedium)
                            }
                        },
                        enabled = false,
                        onClick = {},
                    )
                } else if (hasSearchedSubdl && subdlGroups.isEmpty()) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = "No SubDL subtitles found",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        enabled = false,
                        onClick = {},
                    )
                }

                // OpenSubtitles search action / status
                if (!hasSearchedOpenSubtitles && canSearchOpenSubtitles) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    DropdownMenuItem(
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(PlayerIcons.Subtitles, contentDescription = null, modifier = Modifier.size(18.dp))
                                Text("Search OpenSubtitles", fontWeight = FontWeight.SemiBold)
                            }
                        },
                        onClick = onSearchOpenSubtitles,
                    )
                } else if (isLoadingOpenSubtitles) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    DropdownMenuItem(
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Text("Searching OpenSubtitles...", style = MaterialTheme.typography.bodyMedium)
                            }
                        },
                        enabled = false,
                        onClick = {},
                    )
                } else if (hasSearchedOpenSubtitles && openSubtitlesGroups.isEmpty()) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = "No OpenSubtitles subtitles found",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        enabled = false,
                        onClick = {},
                    )
                }
            } else {
                // Submenu: shows the subtitles for the chosen language
                DropdownMenuItem(
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(PlayerIcons.Back, contentDescription = "Back", modifier = Modifier.size(16.dp))
                            Text(
                                text = currentGroup.language,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    },
                    onClick = {
                        selectedLanguageGroup = null
                    },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                currentGroup.subtitles.forEach { subtitle ->
                    val isDownloading = subtitle.downloadUrl == downloadingSubtitleUrl
                    DropdownMenuItem(
                        text = {
                            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                                Text(
                                    text = subtitle.releaseName,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    if (subtitle.author.isNotBlank()) {
                                        Text(
                                            text = subtitle.author,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    if (subtitle.hearingImpaired) {
                                        Text(
                                            text = "HI",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFFFA726),
                                        )
                                    }
                                }
                            }
                        },
                        trailingIcon = {
                            if (isDownloading) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            }
                        },
                        enabled = !isDownloading,
                        onClick = {
                            onPickSubtitle(subtitle)
                            setOpen(false)
                        },
                    )
                }
            }
        }
    }
}
