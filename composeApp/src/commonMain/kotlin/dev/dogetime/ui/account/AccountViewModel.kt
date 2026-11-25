package dev.dogetime.ui.account

import dev.dogetime.core.ViewModel
import dev.dogetime.data.sync.Account
import dev.dogetime.data.sync.AccountRepository
import dev.dogetime.data.sync.GoogleSignIn
import dev.dogetime.data.sync.GoogleSignInCancelled
import dev.dogetime.data.sync.SyncStatus
import dev.dogetime.data.sync.WatchSync
import dev.dogetime.data.sync.toAccountMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AuthMode { SignIn, SignUp }

data class AuthFormState(
    val mode: AuthMode = AuthMode.SignIn,
    val isBusy: Boolean = false,
    /** Waiting on Google (the account picker, or the browser on desktop). */
    val isGoogleBusy: Boolean = false,
    val error: String? = null,
)

/** Sign-in, sign-up and sign-out, for the welcome screen and the Settings account section. */
class AccountViewModel(
    private val accounts: AccountRepository,
    private val sync: WatchSync,
) : ViewModel() {
    val isConfigured: Boolean get() = accounts.isConfigured
    val account: StateFlow<Account?> = accounts.account
    val syncStatus: StateFlow<SyncStatus> = sync.status

    private val _form = MutableStateFlow(AuthFormState())
    val form: StateFlow<AuthFormState> = _form.asStateFlow()

    fun setMode(mode: AuthMode) {
        _form.value = AuthFormState(mode = mode)
    }

    fun signIn(username: String, password: String, onDone: () -> Unit) {
        when {
            username.isBlank() -> fail("Enter your username.")
            password.isEmpty() -> fail("Enter your password.")
            else -> submit(onDone) { accounts.signIn(username, password) }
        }
    }

    fun signUp(username: String, email: String, password: String, onDone: () -> Unit) {
        when {
            username.trim().length < 3 -> fail("The username needs at least 3 characters.")
            !email.contains('@') -> fail("Enter a valid email address.")
            password.length < 8 -> fail("The password needs at least 8 characters.")
            else -> submit(onDone) { accounts.signUp(username, email, password) }
        }
    }

    private var googleJob: Job? = null

    /** Signs in with Google through [googleSignIn]; a cancelled picker isn't shown as an error. */
    fun signInWithGoogle(googleSignIn: GoogleSignIn, onDone: () -> Unit) {
        if (_form.value.isBusy) return
        _form.update { it.copy(isBusy = true, isGoogleBusy = true, error = null) }
        googleJob = scope.launch {
            try {
                accounts.signInWithGoogle(googleSignIn.signIn())
                _form.value = AuthFormState()
                onDone()
            } catch (e: CancellationException) {
                _form.update { it.copy(isBusy = false, isGoogleBusy = false) }
                throw e
            } catch (_: GoogleSignInCancelled) {
                _form.update { it.copy(isBusy = false, isGoogleBusy = false) }
            } catch (e: Exception) {
                _form.update { it.copy(isBusy = false, isGoogleBusy = false, error = e.toAccountMessage()) }
            }
        }
    }

    /** Stops waiting for Google (e.g. the browser tab was closed on desktop). */
    fun cancelGoogle() {
        googleJob?.cancel()
        googleJob = null
    }

    /** The first-launch choice to use the app without an account. */
    fun continueWithoutAccount(onDone: () -> Unit) {
        scope.launch {
            accounts.finishOnboarding()
            onDone()
        }
    }

    fun signOut() {
        scope.launch { accounts.signOut() }
    }

    fun syncNow() = sync.syncNow()

    private fun fail(message: String) = _form.update { it.copy(error = message) }

    private fun submit(onDone: () -> Unit, action: suspend () -> Unit) {
        if (_form.value.isBusy) return
        _form.update { it.copy(isBusy = true, error = null) }
        scope.launch {
            try {
                action()
                _form.value = AuthFormState()
                onDone()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _form.update { it.copy(isBusy = false, error = e.toAccountMessage()) }
            }
        }
    }
}
