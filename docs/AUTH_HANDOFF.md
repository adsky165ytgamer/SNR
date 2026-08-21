# NoticeFlow V0.1 Production Authentication Handoff

## Scope

This handoff records the Google Sign-In and authenticated API upgrade for the School Notice Broadcast System. The system remains split into two native Android applications, `app.receiver` and `app.sender`, with a Fastify/TypeScript backend connected to the real Firebase project `school-notice-systems`.

The authentication boundary is now:

> Google account on Android → Firebase Auth credential → Firebase ID token → Fastify `Authorization: Bearer <token>` → Firebase Admin `verifyIdToken()` → Firestore and FCM.

The Android applications never receive or embed the Firebase Admin service-account private key, an FCM server key, or any other backend credential.

## Backend implementation

The backend entrypoint in `backend/src/server.ts` now constructs `FirebaseIdTokenVerifier` and passes it to `createApp`. `backend/src/auth.ts` validates the `Authorization` header and verifies the Firebase ID token with the Admin SDK. Invalid, missing, or expired credentials are returned as `401 AUTH_REQUIRED`.

The protected API surface is as follows:

| Route | Authentication | Purpose |
|---|---:|---|
| `GET /health` | Public | Liveness probe only. It does not expose data. |
| `POST /api/v1/receivers/register` | Required in production | Registers or refreshes a receiver installation and stores the verified Firebase UID as `ownerUid`. |
| `POST /api/v1/receivers/heartbeat` | Required in production | Refreshes receiver liveness and checks account ownership. |
| `GET /api/v1/receivers` | Required in production | Returns enabled receiver metadata without FCM tokens or Firebase ownership fields. |
| `GET /api/v1/receivers/:receiverId` | Required in production | Returns one public receiver record without sensitive fields. |
| `POST /api/v1/test-notice` | Required in production | Sends a high-priority FCM data notification and logs the dispatch in Firestore. |

Receiver records now include an internal `ownerUid` field. The field is never returned by the public DTO. A signed-in account cannot claim a receiver installation already owned by a different Firebase UID. Notice dispatch records are written to the `notices` collection without storing the FCM token.

For backwards-compatible unit tests, `createApp()` still permits an omitted verifier. The real server entrypoint always supplies `FirebaseIdTokenVerifier`; tests that exercise production authentication inject a fake verifier and assert that missing credentials return `401`.

## Android implementation

Both apps already contain a `GoogleAuthSession` helper based on Credential Manager, Google ID tokens, and Firebase Auth. The activities now expose a visible Google account card, restore the existing Firebase session on launch, allow sign-in and sign-out, and use the resulting Firebase ID token for protected backend requests.

The Receiver flow is: sign in with Google, initialize Firebase, obtain the real Firebase installation ID and FCM token, then call the registration endpoint with the Bearer token. The background heartbeat worker and FCM token-refresh registration path also obtain the current Firebase ID token before calling the backend. If no signed-in Firebase user exists, those background operations safely skip or retry rather than sending unauthenticated requests.

The Sender flow is: sign in with Google, load real enabled receiver records with the Bearer token, select one returned device, compose a notice, and send it with the same authenticated API path. The UI does not fabricate receiver names, IDs, or status data.

The shared build property is:

```properties
GOOGLE_WEB_CLIENT_ID=replace-with-production-web-client-id
```

This value is a non-secret OAuth client identifier, not a private key. It must be replaced with the Firebase/Google Cloud **Web application** OAuth client ID before a functional production build. The current backend URL remains a separate build property and must point to the permanent HTTPS backend deployment rather than a temporary tunnel.

Each Android app must have its own Firebase Android registration for its exact package name. The Receiver’s `google-services.json` remains local and ignored by Git. The Sender module now supports its own local `google-services.json`; the correct file must be obtained for the `app.sender` registration and must not be copied from the Receiver app.

## Validation completed

The backend test suite passes all five tests, including registration privacy, missing receiver handling, invalid FCM token mapping, malformed JSON handling, and rejection of missing Firebase credentials. The backend TypeScript build passes with `npm run build`.

The Android project could not be compiled in this sandbox because the repository does not contain a Gradle wrapper and no system `gradle` executable is installed. The source-level call-site scan confirms that protected Receiver and Sender requests now pass Firebase ID tokens, while the public `/health` probe remains unauthenticated.

## Required user-side configuration before a real APK test

The production Web OAuth client ID must be entered into `android/gradle.properties` as `GOOGLE_WEB_CLIENT_ID`. A Firebase Android app registration and matching `google-services.json` must exist for both `app.receiver` and `app.sender`; these files remain uncommitted. The backend must be deployed at a permanent HTTPS URL and that URL must replace `BACKEND_BASE_URL` in the shared Gradle properties.

The Firebase Authentication provider for Google must be enabled in the `school-notice-systems` Firebase project. The Android test devices must use Google Play services and have a Google account permitted by the project’s authentication configuration.

## Credential rotation requirement

The service-account JSON and FCM credential previously pasted into chat must be treated as compromised. Revoke the exposed service-account key in Google Cloud IAM immediately and create a replacement only if a local machine requires one. For Cloud Run, prefer Application Default Credentials through the attached runtime service account and grant only the Firestore and FCM permissions required by this backend. Do not commit the replacement JSON, paste it into source files, or put it in either APK.

## End-to-end acceptance test

1. Replace the OAuth client ID and permanent backend URL in the local Android build properties.
2. Place the correct Firebase configuration file in each module locally, without committing it.
3. Build and install both apps.
4. Sign into the Receiver with Google and connect it. Confirm a `receivers/{receiverId}` document contains the correct `ownerUid`, a current FCM token, and a refreshed `lastSeenAt`.
5. Sign into the Sender with Google, refresh the live receiver list, select the real Receiver, and send a notice.
6. Confirm the Receiver displays the high-priority notification and local history entry, and confirm a corresponding document appears in `notices/{noticeId}`.
7. Sign out or use an expired token and confirm the backend returns `401 AUTH_REQUIRED` rather than performing a protected operation.

## Repository synchronization

The Android source trees are intended to sync to the existing `SNR` and `SNS` GitHub repositories. Both repositories currently report public visibility and should be changed to private before pushing any project history. The `.gitignore` files now exclude `google-services.json`, keystores, and build outputs. No service-account credential or FCM secret is included in this handoff.
