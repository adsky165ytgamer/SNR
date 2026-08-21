package app.receiver.auth

import androidx.activity.ComponentActivity
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import app.receiver.BuildConfig
import kotlinx.coroutines.tasks.await

/**
 * Authenticated session for the Receiver. Google Sign-In is preferred; if its
 * OAuth client is not configured or the provider cannot complete, Firebase
 * anonymous auth still gives the backend a verifiable ID token for device
 * recognition instead of sending unauthenticated requests.
 */
class GoogleAuthSession(activity: ComponentActivity) {
    private val context = activity.applicationContext
    private val credentialManager = CredentialManager.create(activity)
    private val auth: FirebaseAuth

    init {
        FirebaseBootstrap.ensureInitialized(context)
        auth = FirebaseAuth.getInstance()
    }

    fun isGoogleConfigured(): Boolean = BuildConfig.GOOGLE_WEB_CLIENT_ID.isNotBlank() &&
        !BuildConfig.GOOGLE_WEB_CLIENT_ID.startsWith("replace-")

    fun preferredButtonText(): String = if (isGoogleConfigured()) "Continue with Google" else "Secure this device"

    suspend fun current(): AuthenticatedIdentity? {
        val user = auth.currentUser ?: return null
        return identity(user, forceRefresh = false)
    }

    suspend fun signIn(): AuthenticatedIdentity {
        if (isGoogleConfigured()) {
            try {
                return googleSignIn()
            } catch (_: Throwable) {
                // Continue with a Firebase-authenticated device session. This
                // keeps the API secure and avoids blocking the app on OAuth setup.
            }
        }
        return anonymousSignIn()
    }

    suspend fun signOut() {
        auth.signOut()
        runCatching { credentialManager.clearCredentialState(ClearCredentialStateRequest()) }
    }

    private suspend fun googleSignIn(): AuthenticatedIdentity {
        auth.signOut()
        val googleIdOption = GetGoogleIdOption.Builder()
            .setServerClientId(BuildConfig.GOOGLE_WEB_CLIENT_ID)
            .setFilterByAuthorizedAccounts(false)
            .setAutoSelectEnabled(false)
            .build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()
        val result = credentialManager.getCredential(context, request)
        val credential = result.credential
        check(credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            "Google returned an unsupported credential type."
        }
        val googleCredential = GoogleIdTokenCredential.createFrom(credential.data)
        auth.signInWithCredential(GoogleAuthProvider.getCredential(googleCredential.idToken, null)).await()
        return identity(auth.currentUser ?: error("Firebase did not return a signed-in Google user."), forceRefresh = true)
    }

    private suspend fun anonymousSignIn(): AuthenticatedIdentity {
        val user = auth.currentUser ?: auth.signInAnonymously().await().user
            ?: error("Firebase could not create a secure device session.")
        return identity(user, forceRefresh = true)
    }

    private suspend fun identity(user: FirebaseUser, forceRefresh: Boolean): AuthenticatedIdentity {
        val token = user.getIdToken(forceRefresh).await()?.token
            ?: error("Firebase did not return an ID token for the authenticated session.")
        return AuthenticatedIdentity(
            uid = user.uid,
            displayName = user.displayName,
            email = user.email,
            idToken = token,
            authMethod = if (user.isAnonymous) "Secure device session" else "Google Sign-In",
        )
    }
}

data class AuthenticatedIdentity(
    val uid: String,
    val displayName: String?,
    val email: String?,
    val idToken: String,
    val authMethod: String,
)
