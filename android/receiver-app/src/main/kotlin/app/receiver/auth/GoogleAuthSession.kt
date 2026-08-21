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

/** Firebase session for Receiver. Email/Password is primary; Google remains optional. */
class GoogleAuthSession(activity: ComponentActivity) {
    private val context = activity.applicationContext
    private val credentialManager = CredentialManager.create(activity)
    private val auth: FirebaseAuth

    init {
        FirebaseBootstrap.ensureInitialized(context)
        auth = FirebaseAuth.getInstance()
    }

    fun preferredButtonText(): String = "Sign in with email"

    suspend fun current(): AuthenticatedIdentity? {
        val user = auth.currentUser ?: return null
        return identity(user, forceRefresh = false)
    }

    suspend fun signInWithEmail(email: String, password: String): AuthenticatedIdentity {
        require(email.isNotBlank()) { "Enter the account email address." }
        require(password.length >= 6) { "Password must contain at least 6 characters." }
        auth.signInWithEmailAndPassword(email.trim(), password).await()
        return identity(auth.currentUser ?: error("Firebase did not return the signed-in account."), forceRefresh = true)
    }

    suspend fun createEmailAccount(email: String, password: String): AuthenticatedIdentity {
        require(email.isNotBlank()) { "Enter an email address for this Receiver account." }
        require(password.length >= 6) { "Password must contain at least 6 characters." }
        auth.createUserWithEmailAndPassword(email.trim(), password).await()
        return identity(auth.currentUser ?: error("Firebase did not return the new Receiver account."), forceRefresh = true)
    }

    suspend fun sendPasswordReset(email: String) {
        require(email.isNotBlank()) { "Enter your account email address first." }
        auth.sendPasswordResetEmail(email.trim()).await()
    }

    suspend fun signOut() {
        auth.signOut()
        runCatching { credentialManager.clearCredentialState(ClearCredentialStateRequest()) }
    }

    /** Optional Google path retained for projects that later configure OAuth. */
    suspend fun signInWithGoogle(webClientId: String): AuthenticatedIdentity {
        require(webClientId.isNotBlank() && !webClientId.startsWith("replace-")) { "Google OAuth is not configured." }
        auth.signOut()
        val option = GetGoogleIdOption.Builder()
            .setServerClientId(webClientId)
            .setFilterByAuthorizedAccounts(false)
            .setAutoSelectEnabled(false)
            .build()
        val result = credentialManager.getCredential(
            context,
            GetCredentialRequest.Builder().addCredentialOption(option).build(),
        )
        val credential = result.credential
        check(credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            "Google returned an unsupported credential type."
        }
        val google = GoogleIdTokenCredential.createFrom(credential.data)
        auth.signInWithCredential(GoogleAuthProvider.getCredential(google.idToken, null)).await()
        return identity(auth.currentUser ?: error("Firebase did not return a Google account."), forceRefresh = true)
    }

    private suspend fun identity(user: FirebaseUser, forceRefresh: Boolean): AuthenticatedIdentity {
        val token = user.getIdToken(forceRefresh).await()?.token
            ?: error("Firebase did not return an ID token for the authenticated session.")
        val method = if (user.providerData.any { it.providerId == "password" }) "Email/Password" else "Google Sign-In"
        return AuthenticatedIdentity(user.uid, user.displayName, user.email, token, method)
    }
}

data class AuthenticatedIdentity(
    val uid: String,
    val displayName: String?,
    val email: String?,
    val idToken: String,
    val authMethod: String,
)
