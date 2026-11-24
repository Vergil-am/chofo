package dev.dogetime.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The "Tracking" section of Settings: connect or disconnect Simkl. */
@Composable
internal fun SimklSection(viewModel: SettingsViewModel) {
    val account by viewModel.simklAccount.collectAsState()
    val login by viewModel.simklLogin.collectAsState()
    val uriHandler = LocalUriHandler.current

    Text(
        text = "Tracking",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
    )
    Spacer(Modifier.height(4.dp))
    Text(
        text = "Sync what you watch to Simkl automatically",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(14.dp))

    Card(
        modifier = Modifier.widthIn(max = 680.dp).fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f),
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val connected = account
            when {
                !viewModel.isSimklConfigured -> Hint(
                    title = "Simkl",
                    body = "This build has no Simkl client id. Add SIMKL_CLIENT_ID to secrets.properties.",
                )

                connected != null -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = "Connected to Simkl as ${connected.username}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            val plan = connected.accountType?.replaceFirstChar { it.uppercase() }
                            val quota = connected.requestsLeftToday?.let { left ->
                                connected.dailyLimit?.let { "$left of $it requests left today" }
                                    ?: "$left requests left today"
                            }
                            Text(
                                text = listOfNotNull(plan?.let { "$it account" }, quota)
                                    .ifEmpty { listOf("Movies, shows and anime are scrobbled as you watch") }
                                    .joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        OutlinedButton(
                            onClick = viewModel::disconnectSimkl,
                            shape = RoundedCornerShape(12.dp),
                        ) { Text("Disconnect") }
                    }
                }

                else -> when (val state = login) {
                    SimklLoginState.Idle, is SimklLoginState.Error -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = "Simkl",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = (state as? SimklLoginState.Error)?.message
                                        ?: "Mark movies, episodes and anime as watched on Simkl",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (state is SimklLoginState.Error) {
                                        MaterialTheme.colorScheme.error
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                )
                            }
                            Button(
                                onClick = viewModel::connectSimkl,
                                shape = RoundedCornerShape(12.dp),
                            ) { Text(if (state is SimklLoginState.Error) "Try again" else "Connect") }
                        }
                    }

                    SimklLoginState.Starting -> Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.5.dp)
                        Text("Contacting Simkl…", style = MaterialTheme.typography.bodyMedium)
                    }

                    is SimklLoginState.AwaitingApproval -> {
                        Text(
                            text = "Open simkl.com/pin and enter this code:",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = state.userCode,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 28.sp,
                            letterSpacing = 3.sp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Button(
                                onClick = { runCatching { uriHandler.openUri(state.verificationUrl) } },
                                shape = RoundedCornerShape(12.dp),
                            ) { Text("Open simkl.com") }
                            TextButton(onClick = viewModel::cancelSimklLogin) { Text("Cancel") }
                            Spacer(Modifier.weight(1f))
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        }
                        Text(
                            text = "Waiting for approval. This page updates on its own.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Hint(title: String, body: String) {
    Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
