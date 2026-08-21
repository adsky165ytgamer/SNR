package app.receiver.auth

import android.content.Context
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

/** Google identity session for the Receiver. The backend receives only a Firebase ID token. */
class GoogleAuthSession(activity: ComponentActivity) {
    private val context: Context = activity
    private val credentialManager = CredentialManager.create(activity)
    private val auth = FirebaseAuth.getInstance()

    fun isConfigured(): Boolean = BuildConfig.GOOGLE_WEB_CLIENT_ID.isNotBlank() &&
        !BuildConfig.GOOGLE_WEB_CLIENT_ID.startsWith("replace-")

    suspend fun current(): AuthenticatedIdentity? {
        val user = auth.currentUser ?: return null
        return identity(user, forceRefresh = false)
    }

    suspend fun signIn(): AuthenticatedIdentity {
        check(isConfigured()) {
            "Google Sign-In is not configured. Add the production Web OAuth client ID to this build."
        }
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
        return identity(auth.currentUser ?: error("Firebase did not return a signed-in user."), forceRefresh = true)
    }

    suspend fun signOut() {
        auth.signOut()
        runCatching { credentialManager.clearCredentialState(ClearCredentialStateRequest()) }
    }

    private suspend fun identity(user: FirebaseUser, forceRefresh: Boolean): AuthenticatedIdentity {
        val token = user.getIdToken(forceRefresh).await()?.token
            ?: error("Firebase did not return an ID token for the signed-in account.")
        return AuthenticatedIdentity(
            uid = user.uid,
            displayName = user.displayName,
            email = user.email,
            idToken = token,
        )
    }
}

data class AuthenticatedIdentity(
    val uid: String,
    val displayName: String?,
    val email: String?,
    val idToken: String,
)
