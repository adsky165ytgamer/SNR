# NoticeFlow Receiver v1.1.4 Beta — Firebase Registration

This is a new non-destructive Beta release for `app.receiver`. Earlier Alpha and Beta releases remain available unchanged.

## Firebase configuration

The official Firebase client configuration for project `school-notics` is now installed locally for the Receiver build. It contains the registered Receiver Android identity and is processed through the Google Services Gradle plugin during the build. The `google-services.json` file is excluded from source control, release assets, and repository staging.

Receiver retains only the required Firebase dependencies: Firebase Authentication for its live Email/Password session and Firebase Cloud Messaging for real notice delivery. No service-account credential, FCM server key, or private Firebase material is included in this app or release.

## Verification

Package `app.receiver`, version code `7`, version `1.1.4-beta-firebase`, Android 8.0+, APK Signature Scheme v2 verified, official Receiver signing certificate SHA-1 `E0:18:38:EB:3F:58:B2:D3:C9:0A:7F:E7:18:45:1E:E5:E8:0D:D8:40`.
