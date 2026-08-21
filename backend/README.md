# School Notice Broadcast V0.1 Backend

This is a deliberately small prototype backend. It accepts a Receiver APK’s generated UUID and FCM registration token, stores it as `receivers/{receiverId}` in Cloud Firestore, and sends a Firebase Cloud Messaging test notification when the Sender APK calls `POST /api/v1/test-notice`.

> This backend intentionally has **no authentication, accounts, role system, school/classroom model, WebSockets, MQTT, Bluetooth, or advanced synchronization**. It is only for validating the Sender → HTTPS backend → FCM → Receiver communication pipeline.

## Endpoints

| Method | Endpoint | Purpose |
|---|---|---|
| `POST` | `/api/v1/receivers/register` | Upserts a live Android receiver and its FCM token. |
| `POST` | `/api/v1/receivers/heartbeat` | Updates a known receiver’s last-seen timestamp. |
| `GET` | `/api/v1/receivers` | Lists registered receivers without exposing FCM tokens. |
| `GET` | `/api/v1/receivers/{receiverId}` | Returns one receiver without its FCM token. |
| `POST` | `/api/v1/test-notice` | Sends an FCM test notification to one enabled receiver. |

## Connect This Backend to Your Existing Firebase Project

This project is already ready to connect directly to your existing Firebase project. It does not need Cloud Functions, a second Firebase project, or Firebase client credentials. Download a private **Admin SDK service-account JSON** from the Firebase Console for your existing project, expose it only in the terminal environment as `FIREBASE_SERVICE_ACCOUNT_JSON`, and start this backend. The exact safe steps are in [CONNECT_MY_FIREBASE.md](./CONNECT_MY_FIREBASE.md).

```bash
cp .env.example .env
npm install
npm run check
npm test
npm run dev
```

The deployed service must be exposed through HTTPS. The Android apps should use its public `https://` base URL, not `localhost`.

## Deployment Handoff

The backend remains deployment-neutral. Once local Firestore and FCM checks work, it can run behind any HTTPS-capable host using the same Admin SDK environment variable. This repository includes a production `Dockerfile`; APK compilation remains deliberately deferred.
