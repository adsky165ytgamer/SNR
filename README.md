# NoticeFlow Receiver

> **Testing build · `app.receiver` · Proprietary software**

NoticeFlow Receiver turns an Android phone, school display, office tablet, or classroom device into a named destination for live school notices.

## Current Home screen

The Home screen is the Receiver’s live status dashboard. It shows the device name, Firebase account state, direct Firestore connection state, registration time, inbox summary, and the next useful setup action. Setup separates account access, device naming, and connection; Inbox stores received notices locally; Settings exposes connection and account controls.

The Home screen and setup source is `android/receiver-app/src/main/kotlin/app/receiver/ReceiverActivity.kt`.

## Direct Firebase architecture

The Receiver connects directly to the `school-notics` Firebase project:

- Firebase Authentication handles Email/Password and Google Sign-In.
- The Receiver writes its document to `receivers/{receiverId}` in Firestore.
- It listens to `receivers/{receiverId}/notices` using a live Firestore listener.
- New notices are persisted locally and shown as Android notifications.
- Heartbeats update the Receiver document directly through Firestore.
- Fastify, HTTP `BackendClient`, Cloud Run, Firebase Admin SDK, FCM registration, and the old messaging service were removed.
- Firestore rules are in `firebase/firestore.rules`.

The direct data client is `android/receiver-app/src/main/kotlin/app/receiver/DirectFirebaseStore.kt`; local notification presentation is in `DirectNoticeNotifier.kt`.

## Firebase configuration

`google-services.json` is intentionally ignored by Git. For a local build, place the Firebase Android configuration containing the `app.receiver` client at:

```text
android/receiver-app/google-services.json
```

The Google web OAuth client is configured in the build as:

```text
763216367314-7ikindb4e0cabej1ut4rhj7n0ejeke6q.apps.googleusercontent.com
```

Enable **Google** under Firebase Authentication, register the SHA-1 certificate used to sign the APK, and publish `firebase/firestore.rules` in the Firebase Console. For the current sandbox debug APK, the SHA-1 is:

```text
4E:5E:53:54:94:3D:8C:5A:31:9F:A4:40:2B:27:D9:17:58:09:23:72
```

## Build

```bash
cd android
../gradle-8.12/bin/gradle :receiver-app:assembleDebug
```

The current testing APK is published in the GitHub testing release. The package is `app.receiver`; the build targets SDK 36.

## Testing release

See [`RELEASE_NOTES_TESTING.md`](RELEASE_NOTES_TESTING.md) for the complete Receiver change list, removed components, Firestore rules requirement, Google Auth requirements, and APK checksum.

## License

This repository and application are proprietary. Do not commit `google-services.json`, service-account credentials, keystores, private keys, or local Gradle properties.
