package dev.dogetime.ui.settings

import dev.dogetime.core.ViewModel
import dev.dogetime.data.SimklAccount
import dev.dogetime.data.SimklLoginResult
import dev.dogetime.data.SimklRepository
import dev.dogetime.data.UpdateChecker
import dev.dogetime.data.UpdateStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Where the Simkl sign-in is. */
sealed interface SimklLoginState {
    data object Idle : SimklLoginState
    data object Starting : SimklLoginState

    /** Waiting for the user to enter [userCode] at [verificationUrl]. */
    data class AwaitingApproval(val userCode: String, val verificationUrl: String) : SimklLoginState
    data class Error(val message: String) : SimklLoginState
}

/**
 * State behind [SettingsScreen]: the update check and the Simkl connection. The update check runs
 * once when the ViewModel is created, which is at app start since Settings is a tab, and again
 * whenever the user asks.
 */
class SettingsViewModel(
    private val updateChecker: UpdateChecker,
    private val simkl: SimklRepository,
) : ViewModel() {

    private val _updateStatus = MutableStateFlow<UpdateStatus>(UpdateStatus.Idle)
    val updateStatus: StateFlow<UpdateStatus> = _updateStatus.asStateFlow()

    val isSimklConfigured: Boolean get() = simkl.isConfigured
    val simklAccount: StateFlow<SimklAccount?> = simkl.account

    private val _simklLogin = MutableStateFlow<SimklLoginState>(SimklLoginState.Idle)
    val simklLogin: StateFlow<SimklLoginState> = _simklLogin.asStateFlow()
    private var loginJob: Job? = null

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

    fun connectSimkl() {
        if (loginJob?.isActive == true) return
        _simklLogin.value = SimklLoginState.Starting
        loginJob = scope.launch {
            val code = try {
                simkl.startLogin()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _simklLogin.value = SimklLoginState.Error(e.message ?: "Couldn't reach Simkl.")
                return@launch
            }
            _simklLogin.value = SimklLoginState.AwaitingApproval(
                userCode = code.userCode,
                verificationUrl = code.verificationUriComplete ?: code.verificationUri,
            )
            _simklLogin.value = when (val result = simkl.awaitLogin(code)) {
                SimklLoginResult.Success -> SimklLoginState.Idle
                SimklLoginResult.Expired -> SimklLoginState.Error("The code expired. Try again.")
                is SimklLoginResult.Failed -> SimklLoginState.Error("Sign-in failed: ${result.message}")
            }
        }
    }

    fun cancelSimklLogin() {
        loginJob?.cancel()
        _simklLogin.value = SimklLoginState.Idle
    }

    fun disconnectSimkl() {
        simkl.logout()
    }
}
