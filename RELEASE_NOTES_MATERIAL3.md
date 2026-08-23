# NoticeFlow Receiver v1.1.1 Alpha Material 3

This is a new, non-destructive official signed Receiver release for package `app.receiver`; it does not replace any previous SNR release.

## Included changes

The uploaded Material 3 design direction has been reconciled with the live Receiver implementation. The update keeps guided onboarding, account creation/sign-in/reset, editable device naming, authenticated live registration, FCM token acquisition and refresh, notification display, and local inbox history. It refines Material 3 button sizing, visible setup feedback, section consistency, safe-area handling for punch-hole/cutout and navigation-bar devices, and restrained transitions.

## Verified status

The release APK and AAB compile successfully. The APK is verified with Android APK Signature Scheme v2 and uses the Receiver official certificate SHA-1 `E0:18:38:EB:3F:58:B2:D3:C9:0A:7F:E7:18:45:1E:E5:E8:0D:D8:40`. Backend contract tests passed 5/5 and the TypeScript build completed successfully.

Physical-device FCM delivery cannot be proven in this environment because no Android device is attached. After installation, grant notification permission, sign in, name the device, connect it, and send a notice from the matching Sender version.
