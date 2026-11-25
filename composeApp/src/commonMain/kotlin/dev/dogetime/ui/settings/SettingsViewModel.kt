package dev.dogetime.ui.settings

import dev.dogetime.core.ViewModel
import dev.dogetime.data.UpdateChecker
import dev.dogetime.data.UpdateStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * State behind [SettingsScreen]: the update check. It runs once when the ViewModel is created,
 * which is at app start since Settings is a tab, and again whenever the user asks.
 */
class SettingsViewModel(private val updateChecker: UpdateChecker) : ViewModel() {

    private val _updateStatus = MutableStateFlow<UpdateStatus>(UpdateStatus.Idle)
    val updateStatus: StateFlow<UpdateStatus> = _updateStatus.asStateFlow()

    init {
        checkForUpdates()
    }

    fun checkForUpdates() {
        if (_updateStatus.value is UpdateStatus.Checking) return
        _updateStatus.value = UpdateStatus.Checking
        scope.launch {
            _updateStatus.value = updateChecker.checkForUpdates()
        }
    }
}
