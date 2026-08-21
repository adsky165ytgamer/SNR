# School Notice Receiver

**NoticeFlow Receiver** is the Android device application for the V0.1 school-notice broadcast prototype. It creates a persistent local receiver ID, obtains an FCM token, registers the device through the trusted backend, sends periodic heartbeats, and displays real Firebase Cloud Messaging notices.

> This is a prototype package. The included debug APK was built against a temporary test backend URL and may no longer connect after that endpoint expires. Configure a permanent HTTPS backend URL before relying on it.

## What this repository contains

| Path | Purpose |
|---|---|
| `android/receiver-app/` | Receiver Android source, package ID `app.receiver` |
| `backend/` | Shared Fastify + Firebase Admin backend source needed by the complete system |
| `prototype-apk/NoticeFlowReceiver-v0.1.1-debug.apk` | Verified prototype Receiver debug APK |
| `docs/` | Device-test guide and complete V0.1 technical handoff |

## Receiver flow

```text
Receiver app → HTTPS backend → Firestore receiver record
             ← Firebase Cloud Messaging notice ← Sender → HTTPS backend
```

The app uses only real runtime identity and FCM token data. It does not create fake devices or expose FCM tokens.

## Local build prerequisites

1. Create or use a Firebase project and register Android package `app.receiver`.
2. Download that project’s `google-services.json` and place it at `android/receiver-app/google-services.json`. Do not commit it.
3. Copy `android/gradle.properties.example` to `android/gradle.properties` and set `BACKEND_BASE_URL` to the permanent HTTPS backend URL.
4. From `android/`, run `./gradlew :receiver-app:assembleDebug` or an equivalent Gradle command.

The Receiver needs Firebase Messaging. It never contains a Firebase Admin service-account key.

## Backend setup

The `backend/` directory is the trusted Fastify service. Configure Firebase Admin credentials only through runtime environment configuration, such as `FIREBASE_SERVICE_ACCOUNT_JSON`, or a cloud runtime identity. Never commit credentials. See `backend/README.md` and `docs/NOTICEFLOW_V01_DETAILED_HANDOFF_PROMPT.md`.

## Prototype status

The end-to-end V0.1 path was verified with a real Android Receiver device, a live Firebase project, Firestore receiver registration, Sender selection, Firebase Cloud Messaging, and notice display.
