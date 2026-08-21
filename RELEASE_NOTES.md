# NoticeFlow Receiver v0.1.2

This release packages the latest Receiver application source and the matching debug APK.

## Included

- Firebase Email/Password sign-in, account creation, password reset, session restoration, and sign-out.
- Editable persistent device name used during live backend registration.
- Guided Account → Device Name → Connect → Notice Inbox setup flow.
- Real FCM token registration, authenticated heartbeat, token-refresh registration, and local notice history.
- Edge-to-edge layout handling for status bars, navigation bars, and display cutouts.
- Actionable diagnostics and copyable device identity details.

## APK

`NoticeFlow-Receiver-debug.apk` targets package `app.receiver`. Install it on a test Android device after configuring the original `school-notics` Firebase project and a reachable HTTPS backend.
