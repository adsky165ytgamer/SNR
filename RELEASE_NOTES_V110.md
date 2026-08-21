# NoticeFlow Receiver v1.1.0 Alpha

This Alpha release rebuilds the Receiver around a calmer, section-based experience instead of a single long setup screen.

## What changed

The Receiver now opens with a first-run introduction that explains the account, device-name, and live-connection steps. After introduction, the app is divided into **Home**, **Setup**, **Inbox**, and **About** sections. The Home section gives the next useful action, Setup keeps account credentials, device naming, and live registration in clear separate cards, Inbox presents local received-notice history, and About identifies **v1.1.0 Alpha**, creator **ad_vibe_dev**, and the proprietary no-open-source status.

The Receiver still uses Firebase Email/Password authentication, a verified Firebase ID token, a real FCM registration token, live backend registration, heartbeat refresh, and a persistent editable device name. The package is `app.receiver` and the release asset is `NoticeFlow-Receiver-v1.1.0-Alpha.apk`.

## Important

This is an Alpha build. Use it with the original `school-notics` Firebase configuration and a reachable permanent HTTPS backend. The application is proprietary and not open source. Do not redistribute, reverse engineer, or publish the application or source without written authorization from ad_vibe_dev.
