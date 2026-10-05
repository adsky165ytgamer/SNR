# NoticeFlow Receiver — Testing Release

## Summary

This testing release moves the Receiver from the former Fastify/HTTP/FCM registration and delivery path to direct Firebase access.

## Included changes

- Updated the main Home screen dashboard in `ReceiverActivity.kt` with device identity, Firebase account state, Firestore connection state, registration status, and inbox summary.
- Added direct Firestore Receiver registration at `receivers/{receiverId}`.
- Added direct Firestore heartbeat updates.
- Added a live listener for `receivers/{receiverId}/notices`.
- Added local notice persistence and direct Android notification presentation.
- Configured Firebase Authentication for Email/Password and Google Sign-In.
- Configured the production Google web OAuth client ID from the supplied Firebase project.
- Added AndroidX/Jetifier build flags and SDK 36 build configuration.
- Added `firebase/firestore.rules` for authenticated Sender/Receiver access.

## Removed

- Fastify backend runtime.
- HTTP `BackendClient`.
- Cloud Run/API route dependency.
- Firebase Admin registration and delivery dependency.
- FCM token registration and the old `NoticeMessagingService` runtime.
- Stale backend URL Gradle property and backend-only module link.

## Firebase setup required

Publish `firebase/firestore.rules` to the `school-notics` Firebase project. Enable Google under Firebase Authentication and register the APK signing SHA-1 in Firebase Project Settings. Keep `google-services.json` local; it is intentionally ignored by Git.

## Artifact

- Package: `app.receiver`
- Target SDK: 36
- APK: `NoticeFlow-Receiver-google-auth-debug.apk`
- SHA-256: `f5b503c78e554d6d8c0d30e2bd3105d3e6687ea7b20aa311b27f7ec5164225ec`
- APK v2 signature: verified
