# NoticeFlow Receiver v1.1.3 Beta — Material 3 v2

This is a new non-destructive Beta release for `app.receiver`. Earlier Alpha and Beta releases remain available unchanged.

## Receiver v2 interface

The uploaded Material 3 v2 design has been integrated as a responsive Compose interface with Home, Inbox, Device, and Settings sections. The visible app state is connected to live Receiver data rather than static placeholders: Firebase Email/Password sign-in and sign-out, account display, password reset, persistent device naming, Receiver ID, last registration, connection state, and registration feedback all use the installed app’s real state.

## Inbox and notification repair

Each received notice is now saved with its FCM notice identifier instead of being collapsed by title/body text. History commits immediately, supports multiple notices with matching content, keeps up to 20 entries, refreshes the Compose Inbox/Home preview when the local store changes, and opens Inbox when the notification is tapped. The backend FCM sender now sends high-priority data-only messages so the Android Receiver service can persist and notify about notices when the app is backgrounded.

## Verification and limits

The package is `app.receiver`, version code `6`, version `1.1.3-beta-receiver-v2`, Android 8.0+, signed with the official Receiver certificate SHA-1 `E0:18:38:EB:3F:58:B2:D3:C9:0A:7F:E7:18:45:1E:E5:E8:0D:D8:40`. The release APK verifies with APK Signature Scheme v2. Backend contract tests passed 5/5 and the TypeScript build completed.

Physical-device FCM delivery still needs confirmation on the installed Receiver after the server process running the revised backend source is refreshed. The Android app and backend source are both included in this release; no notices, devices, or delivery results are fabricated.
