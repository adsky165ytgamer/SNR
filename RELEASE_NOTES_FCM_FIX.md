# NoticeFlow Receiver v1.1.0 Alpha — FCM reliability fix

This Receiver release keeps the current NoticeFlow design language and hardens notification delivery for the Sender contract used by SNS.

The FCM service now initializes Firebase before background token work, refreshes the Firebase ID token before token re-registration, reads the exact Sender payload fields `noticeId`, `type`, `receiverId`, `title`, and `body`, persists every received notice locally, creates the high-importance `school_notice_test` channel, uses a valid app icon, sets high notification priority and public visibility, and opens the Receiver when the notification is tapped. Android 13+ notification permission is respected and the Receiver setup flow requests it explicitly.

The app remains connected to the live `/api/v1/receivers/register` contract used by the current Sender release. The backend and Sender source are unchanged in this Receiver-only patch.

Verification: package `app.receiver`; version `1.1.0-alpha`; version code `3`; Receiver and Sender Android builds successful; backend test suite 5/5 passed. NoticeFlow is proprietary and not open source. Creator: **ad_vibe_dev**.
