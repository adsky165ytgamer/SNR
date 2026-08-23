# NoticeFlow Receiver v1.1.6 Beta — Signed Authentication

This is a new non-destructive official-signed Beta release for `app.receiver`.

## Authentication experience

The Receiver account screen now clearly offers three real Firebase-backed paths: **sign in with email**, **create account**, and **continue with Google**. Password reset and sign-out remain available. Google uses the native Android Credential Manager flow and Firebase Authentication; it is not a browser mock or a hardcoded identity.

## Signing and verification

Package `app.receiver`, version code `9`, version `1.1.6-beta-authentication`, Android 8.0+. The release APK verifies with APK Signature Scheme v2 and the official Receiver signing certificate SHA-1 `E0:18:38:EB:3F:58:B2:D3:C9:0A:7F:E7:18:45:1E:E5:E8:0D:D8:40`.

No keystore, password, Firebase client file, Web OAuth client value, OAuth secret, service-account credential, or FCM server key is included in this repository or release.
