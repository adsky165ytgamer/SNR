# Google Sign-In Integration Research

## Sources and implementation findings

1. Firebase Android Google Sign-In guide: https://firebase.google.com/docs/auth/android/google-signin
   - Add Firebase Authentication with the Firebase Android BoM.
   - Add Credential Manager dependencies: `androidx.credentials:credentials`, `androidx.credentials:credentials-play-services-auth`, and `com.google.android.libraries.identity.googleid:googleid`.
   - Add the Android app SHA-1 fingerprint in Firebase project settings and download the updated `google-services.json` so OAuth client information is present.
   - Use the Web application OAuth client ID, not the Android client ID, as `GetGoogleIdOption.Builder().setServerClientId(...)`.
   - Exchange the Google ID token with `GoogleAuthProvider.getCredential(idToken, null)` and sign in using `FirebaseAuth.signInWithCredential`.
   - On sign-out, sign out from Firebase Auth and clear Credential Manager state.

2. Android Sign in with Google / Credential Manager guide: https://developer.android.com/identity/sign-in/credential-manager-siwg
   - Credential Manager is the recommended Android API for Sign in with Google.
   - Use both the Credential Manager bottom-sheet experience and a persistent Sign in with Google button so users can retry after dismissing the sheet or add an account when needed.
   - The integration requires Google Auth Platform configuration and production brand verification considerations.

3. Firebase Admin ID-token verification guide: https://firebase.google.com/docs/auth/admin/verify-id-tokens
   - After client sign-in, retrieve the Firebase user ID token and send it to the custom backend over HTTPS.
   - The backend must verify the token with Firebase Admin SDK `verifyIdToken()` and use the decoded `uid` for authenticated identity.
   - The backend must not trust a client-supplied UID alone.
   - Admin verification requires a service account or equivalent trusted Admin SDK credentials and a correct project ID.

4. Firebase Android Authentication overview: https://firebase.google.com/docs/auth/android/start
   - FirebaseAuth exposes current auth state and user profile information.
   - The Firebase UID identifies the user within the Firebase project, but backend authentication should use a verified Firebase ID token rather than trusting the UID directly.

## Project-specific implications

The NoticeFlow Receiver should use Google Sign-In through Credential Manager, Firebase Auth, and the real `school-notics` Android configuration. The Receiver should send a freshly obtained Firebase ID token in an HTTPS Authorization header when registering or refreshing a device. The Sender should also sign in and send an ID token on receiver-list and notice-send requests. The backend should verify the token and associate receiver installations with the decoded Firebase UID.

The existing local UUID remains a per-installation identifier, not an authentication credential. A single Google account may own multiple receiver installations; the data model should therefore retain both `uid` and `receiverId`. The backend should not expose FCM tokens in any response. The previous service-account private key and FCM-looking token pasted into chat are compromised and must be revoked or rotated before production use.
