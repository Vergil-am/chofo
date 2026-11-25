package dev.dogetime.data.sync

import androidx.compose.runtime.Composable

/** Not built for iOS yet: the Google button stays hidden there. */
@Composable
actual fun rememberGoogleSignIn(api: ChofoApi): GoogleSignIn? = null
