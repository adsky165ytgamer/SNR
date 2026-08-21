# NoticeFlow Receiver

> **v1.1.0 Alpha** · Created by **ad_vibe_dev** · **Proprietary software — not open source**

NoticeFlow Receiver turns an Android phone, school display, office tablet, or classroom device into a named destination for live school notices. Its application package is `app.receiver`.

## v1.1.0 Alpha experience

The Receiver has been rebuilt as a multi-section workspace rather than a single long form. A first-run introduction explains how account access, device naming, and live connection fit together. After onboarding, the application provides **Home**, **Setup**, **Inbox**, and **About** sections. Home shows the device’s next useful action, Setup separates authentication, naming, and registration, Inbox stores received notices locally, and About records the version, creator, and license status.

The device name is persistent and is sent during live registration so Sender users choose real locations such as Front Office, Class 8A, or Library Display. The Receiver uses Firebase Email/Password authentication, receives a Firebase ID token, obtains a real FCM token, registers with the backend, refreshes liveness, and keeps a local inbox.

## Install and configure

Download the current APK from [Releases](https://github.com/adsky165ytgamer/SNR/releases). Install it on the target Android device, create or use an Email/Password account enabled in the original `school-notics` Firebase project, name the device, then connect it to the permanent HTTPS backend.

To build locally, copy `gradle.properties.example` to `gradle.properties` and add the permanent backend URL plus public Firebase client metadata. Never commit `google-services.json`, local Gradle properties, keystores, Firebase Admin credentials, FCM server credentials, or private keys.

## License

This repository and application are proprietary. No permission is granted to copy, redistribute, reverse engineer, modify, publish, or use the source or binaries without written authorization from **ad_vibe_dev**.
