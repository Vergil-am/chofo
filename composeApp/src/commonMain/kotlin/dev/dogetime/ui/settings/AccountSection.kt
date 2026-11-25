package dev.dogetime.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.dogetime.data.sync.SyncStatus
import dev.dogetime.ui.account.AccountViewModel
import kotlinx.coroutines.delay
import kotlin.time.Clock

/** Signed out: what an account adds, and a way to sign in. Signed in: who, sync state, sign out. */
@Composable
fun AccountSection(viewModel: AccountViewModel, onSignIn: () -> Unit) {
    if (!viewModel.isConfigured) return
    val account by viewModel.account.collectAsState()
    val status by viewModel.syncStatus.collectAsState()

    Text(
        text = "Account",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
    )
    Spacer(Modifier.height(4.dp))
    Text(
        text = if (account == null) "Sync your watch history across devices" else "Your watch history syncs across your devices",
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
        val signedIn = account
        if (signedIn == null) {
            Text(
                text = "You're using Chofo on this device only. Sign in to keep your history and where you " +
                    "stopped the same on every device.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Button(onClick = onSignIn, shape = RoundedCornerShape(12.dp)) { Text("Sign in or create an account") }
        } else {
            Column {
                Text(signedIn.username, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                if (signedIn.email.isNotBlank()) {
                    Text(signedIn.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(
                text = syncText(status),
                style = MaterialTheme.typography.bodyMedium,
                color = if (status.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = viewModel::syncNow, enabled = !status.isSyncing, shape = RoundedCornerShape(12.dp)) {
                    Text(if (status.isSyncing) "Syncing…" else "Sync now")
                }
                OutlinedButton(onClick = viewModel::signOut, shape = RoundedCornerShape(12.dp)) { Text("Sign out") }
            }
        }
    }
    Spacer(Modifier.height(36.dp))
}

@Composable
private fun syncText(status: SyncStatus): String {
    // Ticks so "2 min ago" stays current while Settings is open.
    var now by remember { mutableLongStateOf(Clock.System.now().toEpochMilliseconds()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = Clock.System.now().toEpochMilliseconds()
        }
    }
    val last = status.lastSyncedAt
    return when {
        status.isSyncing -> "Syncing…"
        status.error != null -> status.error
        last == null -> "Not synced yet"
        else -> {
            val minutes = ((now - last).coerceAtLeast(0) / 60_000).toInt()
            when {
                minutes < 1 -> "Synced just now"
                minutes < 60 -> "Synced $minutes min ago"
                else -> "Synced ${minutes / 60} h ago"
            }
        }
    }
}
