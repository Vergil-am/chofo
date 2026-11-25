package dev.dogetime.data.sync

import androidx.compose.runtime.Composable

/** What a platform's Google sign-in hands back, for [AccountRepository.signInWithGoogle]. */
sealed interface GoogleCredential {
    /** A Google ID token for the API's Web client (Android's account picker). */
    data class IdToken(val token: String) : GoogleCredential

    /** A one-time token from the API, after signing in to Google in the browser (desktop). */
    data class OneTimeToken(val token: String) : GoogleCredential
}

/** Google sign-in didn't finish, with a message for the user. */
open class GoogleSignInException(message: String) : Exception(message)

/** The user closed the account picker or the sign-in: not an error to show. */
class GoogleSignInCancelled : GoogleSignInException("Google sign-in was cancelled.")

/** Starts this platform's Google sign-in. */
fun interface GoogleSignIn {
    /** Suspends until the user is done; throws [GoogleSignInException] when it doesn't succeed. */
    suspend fun signIn(): GoogleCredential
}

/**
 * This platform's Google sign-in, or null where there is none (or it isn't configured).
 * [api] is the Chofo API the desktop browser flow goes through.
 */
@Composable
expect fun rememberGoogleSignIn(api: ChofoApi): GoogleSignIn?
