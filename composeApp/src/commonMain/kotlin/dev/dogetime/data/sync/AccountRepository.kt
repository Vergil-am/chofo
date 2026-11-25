package dev.dogetime.data.sync

import dev.dogetime.data.local.PreferenceDao
import dev.dogetime.data.local.PreferenceEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** The account this device is signed in to. */
@Serializable
data class Account(
    val userId: String,
    val username: String,
    val email: String,
    val token: String,
)

/**
 * Sign-in state for the Chofo API. Accounts are optional: everything works locally without one,
 * and signing in only adds sync. The session token is kept in the app's local database.
 */
class AccountRepository(
    private val api: ChofoApi,
    private val prefs: PreferenceDao,
    appScope: CoroutineScope,
) {
    /** False when this build has no API address, in which case accounts are hidden. */
    val isConfigured: Boolean get() = api.isConfigured

    private val _account = MutableStateFlow<Account?>(null)
    val account: StateFlow<Account?> = _account.asStateFlow()

    private val loaded = CompletableDeferred<Unit>()

    /**
     * Whether the first-launch choice (sign in, or use the app locally) was made. Null until
     * read. Always true when accounts are hidden, so the prompt never shows.
     */
    val onboardingDone: Flow<Boolean?> =
        if (!api.isConfigured) kotlinx.coroutines.flow.flowOf(true)
        else prefs.observe(KEY_ONBOARDING).map { it == "done" }

    init {
        appScope.launch {
            try {
                _account.value = prefs.observe(KEY_ACCOUNT).first()?.let { json.decodeFromString<Account>(it) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                println("Reading the saved account failed: ${e.message}")
            } finally {
                loaded.complete(Unit)
            }
        }
    }

    suspend fun awaitLoaded() = loaded.await()

    suspend fun signIn(username: String, password: String) {
        save(api.signIn(username.trim(), password))
    }

    suspend fun signUp(username: String, email: String, password: String) {
        save(api.signUp(username.trim(), email.trim(), password))
    }

    /**
     * Signs out on this device right away; the local watch history stays. The server session is
     * ended too, when reachable.
     */
    suspend fun signOut() {
        val old = _account.value ?: return
        forget()
        try {
            api.signOut(old.token)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Signed out here already; the server session expires on its own.
        }
    }

    /** The server no longer accepts the token (expired, revoked, account deleted). */
    suspend fun onSessionRejected() {
        forget()
    }

    /** Records the first-launch choice so the prompt doesn't show again. */
    suspend fun finishOnboarding() {
        prefs.upsert(PreferenceEntity(KEY_ONBOARDING, "done"))
    }

    private suspend fun save(response: AuthResponse) {
        val account = Account(
            userId = response.user.id,
            username = response.user.username ?: response.user.name,
            email = response.user.email,
            token = response.token,
        )
        prefs.upsert(PreferenceEntity(KEY_ACCOUNT, json.encodeToString(account)))
        finishOnboarding()
        _account.value = account
    }

    private suspend fun forget() {
        _account.value = null
        prefs.delete(KEY_ACCOUNT)
    }

    private companion object {
        const val KEY_ACCOUNT = "chofo_account"
        const val KEY_ONBOARDING = "onboarding"
        val json = Json { ignoreUnknownKeys = true }
    }
}

/** A short message for the user from a failed sign-in or sign-up. */
fun Throwable.toAccountMessage(): String = when (this) {
    is ChofoApiException -> when {
        code == "INVALID_USERNAME_OR_PASSWORD" || status == 401 -> "Wrong username or password."
        code == "USERNAME_IS_ALREADY_TAKEN" -> "That username is taken. Try another one."
        code == "USER_ALREADY_EXISTS" || code == "USER_ALREADY_EXISTS_USE_ANOTHER_EMAIL" -> "An account with that email already exists."
        code == "PASSWORD_TOO_SHORT" -> "The password needs at least 8 characters."
        code == "USERNAME_TOO_SHORT" -> "The username needs at least 3 characters."
        code == "INVALID_USERNAME" -> "Usernames can only use letters, numbers, dots and underscores."
        code == "INVALID_EMAIL" -> "That email address doesn't look right."
        status == 429 -> "Too many attempts. Wait a minute and try again."
        else -> message ?: "Something went wrong (${status})."
    }
    else -> "Couldn't reach the server. Check your connection."
}
