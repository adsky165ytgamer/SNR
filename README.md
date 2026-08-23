# NoticeFlow Receiver

> **v1.1.1 Alpha Material 3** · Created by **ad_vibe_dev** · **Proprietary software — not open source**

NoticeFlow Receiver turns an Android phone, school display, office tablet, or classroom device into a named destination for live school notices. Its application package is `app.receiver`.

## v1.1.1 Alpha Material 3 experience

This official signed update preserves the live Firebase Email/Password authentication, named-device registration, FCM receipt, and local inbox contracts while refining Material 3 touch targets, setup feedback, motion, navigation consistency, and display-cutout/navigation-bar safety. The version is `1.1.1-alpha-material3` with version code `4`; obtain the matching APK or AAB from the [v1.1.1 Alpha Material 3 release](https://github.com/adsky165ytgamer/SNR/releases/tag/v1.1.1-alpha-material3).

The Receiver has been rebuilt as a multi-section workspace rather than a single long form. A first-run introduction explains how account access, device naming, and live connection fit together. After onboarding, the application provides **Home**, **Setup**, **Inbox**, and **About** sections. Home shows the device’s next useful action, Setup separates authentication, naming, and registration, Inbox stores received notices locally, and About records the version, creator, and license status.

The device name is persistent and is sent during live registration so Sender users choose real locations such as Front Office, Class 8A, or Library Display. The Receiver uses Firebase Email/Password authentication, receives a Firebase ID token, obtains a real FCM token, registers with the backend, refreshes liveness, and keeps a local inbox.

## Install and configure

Download the current APK from [Releases](https://github.com/adsky165ytgamer/SNR/releases). Install it on the target Android device, create or use an Email/Password account enabled in the original `school-notics` Firebase project, name the device, then connect it to the permanent HTTPS backend.

To build locally, copy `gradle.properties.example` to `gradle.properties` and add the permanent backend URL plus public Firebase client metadata. Never commit `google-services.json`, local Gradle properties, keystores, Firebase Admin credentials, FCM server credentials, or private keys.

The official release key SHA-1 for `app.receiver` is `E0:18:38:EB:3F:58:B2:D3:C9:0A:7F:E7:18:45:1E:E5:E8:0D:D8:40`. Register it in the Google/Firebase Android OAuth configuration before attempting native Google sign-in. Email/Password remains the tested primary sign-in route until a production Web OAuth client ID is configured.

## License

This repository and application are proprietary. No permission is granted to copy, redistribute, reverse engineer, modify, publish, or use the source or binaries without written authorization from **ad_vibe_dev**.
