# Live Device Test — NoticeFlow V0.1

Both debug APKs were built against the same temporary HTTPS backend endpoint. The endpoint is connected to Firebase project `school-notics` using the server-side Firebase Admin credential; the Receiver APK contains the Firebase Android configuration registered for package `app.receiver`.

> The current HTTPS endpoint is temporary and intended only for this controlled V0.1 test. It is public because V0.1 deliberately has no authentication. Do not treat it as a permanent production service.

## Install Order

| Step | Action | Expected real result |
|---|---|---|
| 1 | Install `receiver-app-debug.apk` on an Android device. | Android recognizes package `app.receiver`. |
| 2 | Open **NoticeFlow / Receiver**, allow notifications, optionally enter a device name, then tap **Connect this receiver**. | The app gets its real FCM token and registers it with the backend. |
| 3 | Open Firebase Console → Firestore → `receivers`. | One document with a generated UUID appears; its FCM token is never shown by the Android UI or backend APIs. |
| 4 | Install and open `sender-app-debug.apk`. | Android recognizes package `app.sender`. |
| 5 | Tap **Refresh live receivers**, choose the registered Receiver, enter a title and message, then tap **Send test notice**. | The Sender calls the backend, which sends through Firebase Cloud Messaging. |
| 6 | Observe the Receiver device. | A native high-priority **School notices** notification appears; opening Receiver also shows the most recently received notice. |

## If a Step Fails

The Receiver connection screen reports whether the missing part is Firebase configuration, the temporary backend URL, or the registration request. The Sender screen reports only actual receiver records returned by the backend; it does not include sample devices.

If the Sender says **No enabled receivers**, first return to Receiver and use **Connect this receiver**. If it says the backend cannot be reached, the temporary endpoint has expired and must be replaced by a permanent HTTPS deployment before a new APK build.

## Scope Kept Intentionally Small

This V0.1 test contains no sign-in, roles, classrooms, sample devices, fake notices, or client-side FCM server keys. The trusted Firebase Admin credential remains only with the backend.
