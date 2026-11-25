package dev.dogetime.data.sync

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import dev.dogetime.Secrets

/**
 * Google's account picker through Credential Manager. The ID token is issued to the API's *Web*
 * client ([Secrets.GOOGLE_WEB_CLIENT_ID]), which is what the API checks it against; Google
 * matches this app through its Android client (package name and signing SHA-1).
 */
@Composable
actual fun rememberGoogleSignIn(api: ChofoApi): GoogleSignIn? {
    // Credential Manager needs an Activity context to show its sheet.
    val context = LocalContext.current
    return remember(context) {
        if (Secrets.GOOGLE_WEB_CLIENT_ID.isBlank() || !api.isConfigured) null else AndroidGoogleSignIn(context)
    }
}

private class AndroidGoogleSignIn(private val context: Context) : GoogleSignIn {
    override suspend fun signIn(): GoogleCredential {
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(GetSignInWithGoogleOption.Builder(Secrets.GOOGLE_WEB_CLIENT_ID).build())
            .build()
        val result = try {
            CredentialManager.create(context).getCredential(context, request)
        } catch (_: GetCredentialCancellationException) {
            throw GoogleSignInCancelled()
        } catch (_: NoCredentialException) {
            throw GoogleSignInException("No Google account on this device. Add one in Settings and try again.")
        } catch (e: GetCredentialException) {
            throw GoogleSignInException("Google sign-in isn't available: ${e.message ?: e.type}")
        }
        val credential = result.credential
        if (credential !is CustomCredential || credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            throw GoogleSignInException("Google returned an unexpected credential.")
        }
        return GoogleCredential.IdToken(GoogleIdTokenCredential.createFrom(credential.data).idToken)
    }
}
