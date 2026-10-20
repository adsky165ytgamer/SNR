# NoticeFlow Receiver v1.1.8 Beta — UI overhaul and overlay hit-box repair

This testing-1 release keeps the direct Firebase Receiver architecture and replaces the old hidden-menu interaction with the explicit phone navigation already used by the Receiver workspace: Home, Inbox, Device, and Settings.

The notice overlay path is now wired to the live direct-Firebase notification flow again. The overlay uses only the visible card bounds, removes the actual WindowManager view before opening the inbox or stopping, removes the unlimited-layout flag, and terminates cleanly if the window cannot be added. Tapping a notice cannot leave an invisible square intercepting touches.

Firebase Auth, Firestore notice listening, local inbox persistence, FCM token handling, notification permission, and the real Receiver identity remain unchanged. No devices or notices are fabricated.

Build artifact: `NoticeFlow-Receiver-v1.1.8-beta-ui-overhaul-debug.apk`.
