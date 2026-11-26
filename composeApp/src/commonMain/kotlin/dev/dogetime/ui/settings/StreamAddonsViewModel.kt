package dev.dogetime.ui.settings

import dev.dogetime.core.ViewModel
import dev.dogetime.data.addons.AddonException
import dev.dogetime.data.addons.StreamAddon
import dev.dogetime.data.addons.StreamAddonRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AddonInstallState(
    val isInstalling: Boolean = false,
    val error: String? = null,
    /** e.g. "Installed Torrentio". */
    val notice: String? = null,
)

/** Settings → Stream add-ons: install from a manifest URL, remove, reorder. */
class StreamAddonsViewModel(private val repository: StreamAddonRepository) : ViewModel() {
    val addons: StateFlow<List<StreamAddon>> = repository.addons.stateIn(scope, SharingStarted.Eagerly, emptyList())

    private val _install = MutableStateFlow(AddonInstallState())
    val install: StateFlow<AddonInstallState> = _install.asStateFlow()

    /** Installs the add-on at [url]; [onDone] clears the text field. */
    fun install(url: String, onDone: () -> Unit) {
        if (_install.value.isInstalling || url.isBlank()) return
        _install.value = AddonInstallState(isInstalling = true)
        scope.launch {
            try {
                val addon = repository.install(url)
                _install.value = AddonInstallState(notice = "Installed ${addon.name}")
                onDone()
            } catch (e: CancellationException) {
                throw e
            } catch (e: AddonException) {
                _install.value = AddonInstallState(error = e.message)
            } catch (e: Exception) {
                _install.value = AddonInstallState(error = "Couldn't install that add-on: ${e.message ?: "unknown error"}")
            }
        }
    }

    fun remove(addon: StreamAddon) {
        scope.launch { repository.remove(addon.id) }
        _install.update { it.copy(error = null, notice = "Removed ${addon.name}") }
    }

    fun move(addon: StreamAddon, delta: Int) {
        scope.launch { repository.move(addon.id, delta) }
    }

    fun restoreDefaults() {
        scope.launch { repository.restoreDefaults() }
    }
}
