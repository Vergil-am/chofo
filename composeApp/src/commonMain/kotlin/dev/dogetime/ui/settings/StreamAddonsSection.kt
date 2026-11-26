package dev.dogetime.ui.settings

import androidx.compose.foundation.background
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** The installed stream add-ons, and a field to install one from its manifest link. */
@Composable
fun StreamAddonsSection(viewModel: StreamAddonsViewModel) {
    val addons by viewModel.addons.collectAsState()
    val install by viewModel.install.collectAsState()
    val uriHandler = LocalUriHandler.current
    var url by rememberSaveable { mutableStateOf("") }

    Text(
        text = "Stream add-ons",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
    )
    Spacer(Modifier.height(4.dp))
    Text(
        text = "Stremio add-ons that list streams. Paste an add-on's manifest link to install it.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(14.dp))

    Column(
        modifier = Modifier
            .widthIn(max = 680.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                placeholder = { Text("https://…/manifest.json") },
                singleLine = true,
                enabled = !install.isInstalling,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { viewModel.install(url) { url = "" } }),
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
            )
            Button(
                onClick = { viewModel.install(url) { url = "" } },
                enabled = !install.isInstalling && url.isNotBlank(),
                shape = RoundedCornerShape(12.dp),
            ) {
                if (install.isInstalling) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text("Install")
                }
            }
        }
        install.error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
        install.notice?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }

        if (addons.isEmpty()) {
            Text("No stream add-ons installed.", style = MaterialTheme.typography.bodyMedium)
        }
        addons.forEachIndexed { index, addon ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(addon.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = listOfNotNull(
                            addon.version.takeIf { it.isNotBlank() }?.let { "v$it" },
                            addon.types.takeIf { it.isNotEmpty() }?.joinToString(", "),
                        ).joinToString(" · ").ifEmpty { addon.baseUrl.substringAfter("://").substringBefore('/') },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (index > 0) TextButton(onClick = { viewModel.move(addon, -1) }) { Text("↑") }
                if (index < addons.lastIndex) TextButton(onClick = { viewModel.move(addon, 1) }) { Text("↓") }
                addon.configureUrl?.let { configure -> TextButton(onClick = { uriHandler.openUri(configure) }) { Text("Configure") } }
                TextButton(onClick = { viewModel.remove(addon) }) { Text("Remove", color = MaterialTheme.colorScheme.error) }
            }
        }
        TextButton(onClick = viewModel::restoreDefaults) { Text("Restore the built-in add-ons") }
        Text(
            text = "To change a configurable add-on's settings, open Configure, then install the link it gives you: it replaces the old one.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Spacer(Modifier.height(36.dp))
}
