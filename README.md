# NoticeFlow Receiver

The NoticeFlow Receiver is the Android endpoint that turns a school display, office phone, classroom device, or other Android installation into a live notice destination. It registers one real installation with the NoticeFlow backend, receives high-priority Firebase Cloud Messaging data notifications, shows heads-up alerts, and keeps a local notice history for later reference.

The application package is `app.receiver`. The current debug APK is available from the [latest Receiver release](https://github.com/adsky165ytgamer/SNR/releases/latest).

## What the Receiver does

The Receiver guides the operator through four simple stages: authenticate the installation, choose a recognizable device name, connect to the live backend, and review incoming notices. The device name is persistent and is sent to the backend during registration, so the Sender can identify a device such as “Front Office,” “Class 8A,” or “Library Display” without relying on fabricated labels.

The application stores its generated installation ID and local notice history on the device. It obtains a real FCM token from Firebase, registers that token through the authenticated Fastify API, refreshes registration when Firebase rotates the token, and sends periodic authenticated heartbeats. FCM tokens and Firebase ownership fields are never exposed in public receiver-list responses.

## Authentication

The primary sign-in system is Firebase Email/Password authentication in the original `school-notics` Firebase project. The Receiver supports account creation, sign-in, password reset, session restoration, and sign-out. After authentication, Firebase issues an ID token and every protected backend request sends it as `Authorization: Bearer <token>`.

The backend verifies the token with Firebase Admin SDK before allowing registration or heartbeat operations. Google Sign-In remains optional code, but the Receiver does not depend on Anonymous Auth or an unauthenticated bypass.

## Receiver setup

Install the APK from the release page, open it, and use **Create account** or enter an existing Email/Password account enabled in Firebase Authentication. Choose a clear device name, select **Connect this Receiver**, and wait for the registration stages to complete. The Sender will then discover the named installation through the live backend.

The application requires a reachable permanent HTTPS backend URL and Firebase client configuration for the original `school-notics` project. Local build properties must be supplied separately and must never be committed.

## Building from source

Use the Android project files in this repository. Copy `gradle.properties.example` to `gradle.properties`, then set the permanent backend URL and the public Firebase client metadata for `school-notics`. Keep `google-services.json`, local Gradle properties, keystores, APK outputs, and all private backend credentials outside version control.

```bash
cp gradle.properties.example gradle.properties
cd android
../gradle-8.13/bin/gradle :receiver-app:assembleDebug
```

The resulting debug APK is written to `android/receiver-app/build/outputs/apk/debug/receiver-app-debug.apk`. The repository also includes the release artifact under `artifacts/NoticeFlow-Receiver-debug.apk`.

## Backend contract

The Receiver uses the following live endpoints:

| Endpoint | Purpose |
|---|---|
| `GET /health` | Checks backend reachability. |
| `POST /api/v1/receivers/register` | Registers the installation, chosen name, FCM token, and app version. |
| `POST /api/v1/receivers/heartbeat` | Refreshes liveness for an already registered installation. |

The backend remains the source of truth for registration state. The local history is only an offline-friendly presentation feature.

## Security notes

Never place Firebase Admin service-account JSON, FCM server credentials, private keys, or production backend secrets in this repository or inside the APK. The Firebase client configuration is not an Admin credential, but it should still be managed according to the deployment workflow. The service-account key previously exposed during development must be revoked and replaced before production use.

## Release contents

The GitHub release contains the matching debug APK for package `app.receiver`, while this repository contains the sanitized Android source, build examples, backend source, documentation, and authentication handoff. A physical device test is still required to validate the final live FCM path on the target handset.
