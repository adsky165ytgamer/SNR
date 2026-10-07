# NoticeFlow Receiver — hardened delivery release

## Included in this main-branch release

The Receiver UI now uses a clean drawer-based layout with Overview, Inbox, This Receiver, and Settings sections. The screens use clearer descriptions, consistent spacing, responsive Compose layout, deliberate loading and empty states, and a Samsung-aware device profile label.

Startup was hardened by moving notification permission requests until the activity is resumed, protecting WorkManager initialization, and adding a dedicated monochrome notification icon. Existing email authentication, Google authentication, direct Firestore registration, local inbox storage, and the direct listener were preserved.

The Receiver now contains a Firebase Messaging service for background data-only FCM messages, stores the current FCM token in `receivers/{receiverId}.fcmToken`, and supports an optional movable quick notice overlay. The overlay requires the user to enable Android overlay access in Receiver Settings and automatically closes after a short period.

Because no Firebase deployment authorization was available in this session, the APK also includes a deployment-free WorkManager Firestore polling fallback. It checks recent notices periodically while Android allows background work and shows the same local notification/overlay without requiring a Cloud Function deployment.

## Firebase push payload

When an FCM sender is available, send a high-priority data-only message with `noticeId`, `title`, `body`, and `type` fields. Do not rely only on an FCM notification payload; the Receiver service handles the data payload and builds the local notification itself.

## Verification

- Debug APK built successfully with Gradle 8.12 and Android SDK 36.
- Package: `app.receiver`
- Target SDK: 36.
- Manifest verification confirmed both `NoticeMessagingService` and `NoticeOverlayService` are packaged.
