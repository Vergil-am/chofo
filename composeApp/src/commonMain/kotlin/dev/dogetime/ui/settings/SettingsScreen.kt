package dev.dogetime.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.dogetime.AppTheme

@Composable
fun SettingsScreen(
    currentTheme: AppTheme,
    onThemeSelect: (AppTheme) -> Unit,
    torrentBufferMb: Int,
    onTorrentBufferChange: (Int) -> Unit,
    viewModel: SettingsViewModel,
    accountViewModel: dev.dogetime.ui.account.AccountViewModel,
    onSignIn: () -> Unit,
    streamAddonsViewModel: StreamAddonsViewModel,
    modifier: Modifier = Modifier,
    bottomInset: Dp = 0.dp,
) {
    val scrollState = rememberScrollState()
    val updateStatus by viewModel.updateStatus.collectAsState()

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

        AccountSection(viewModel = accountViewModel, onSignIn = onSignIn)

        StreamAddonsSection(viewModel = streamAddonsViewModel)

        PlaybackSection(torrentBufferMb, onTorrentBufferChange)
        Spacer(Modifier.height(36.dp))
        ThemeSection(currentTheme, onThemeSelect)
        Spacer(Modifier.height(36.dp))
        SocialsSection()
        Spacer(Modifier.height(36.dp))
        UpdatesSection(updateStatus, onCheckForUpdates = viewModel::checkForUpdates)
        Spacer(Modifier.height(36.dp))
        LegalSection()
        Spacer(Modifier.height(36.dp))
        AboutSection()
    }
}
