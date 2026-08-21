# NoticeFlow V0.1 — Detailed Technical Handoff Prompt

Copy everything below the line into another AI chat, a developer task, or a project handoff.

---

## Prompt

You are continuing a real, working **NoticeFlow V0.1 School Notice Broadcast System**. Read the full context carefully before changing anything. Preserve the verified architecture, do not fabricate devices or data, and do not touch the unrelated Manga project.

### 1. Goal and Proven Working Result

The goal of V0.1 is deliberately small: a Sender Android app chooses a real registered Receiver device, writes a test notice, and sends it through a trusted backend to Firebase Cloud Messaging. The Receiver Android app displays that notice.

The complete path below has been verified on a real Moto Android phone:

```text
Sender app
  → HTTPS REST backend
  → Firebase Admin SDK
  → Cloud Firestore receiver record + Firebase Cloud Messaging
  → Receiver app native notification / in-app latest notice state
```

The user confirmed that the Receiver app registered, Sender showed the real Receiver, the Sender notice was sent, and the Receiver displayed the message. This was a real Firebase and FCM flow, not a mocked UI demonstration.

### 2. Strict Scope and Non-Goals

Keep the system simple. V0.1 intentionally has **no** Firebase Auth, Google Sign-In, school accounts, teacher accounts, roles, classrooms, branches, dashboards, WebSockets, MQTT, Bluetooth, Nearby Connections, or microservices.

Devices are identified only by generated UUIDs. The backend is the only trusted component allowed to send privileged FCM messages. Do not put an FCM server key, Firebase Admin private key, or service-account credential in either APK.

Do not modify or inspect this unrelated project:

```text
/home/ubuntu/manga-authority-web-platform-/
```

All work for this system belongs here:

```text
/home/ubuntu/school-notice-broadcast-v01/
```

### 3. Firebase Project and Security Boundary

The existing Firebase project is:

```text
school-notics
```

The backend uses Firebase Admin SDK credentials only on the server side. A service-account JSON file was supplied by the project owner and stored privately outside the repository. It must never be printed, committed, attached to a build, or placed in an APK.

Cloud Firestore stores receiver records only at:

```text
receivers/{receiverId}
```

The record has this conceptual shape:

```text
receiverId
name
fcmToken
platform
appVersion
enabled
createdAt
updatedAt
lastSeenAt
```

The FCM token is private. It must never be returned in any HTTP API response, exposed by Sender, or displayed in the Receiver UI.

Firestore client rules deny direct client access. The Android apps communicate with Firestore only indirectly through the backend’s REST API. The backend’s Firebase Admin SDK performs the trusted Firestore and FCM work.

### 4. Backend Implementation

Backend location:

```text
/home/ubuntu/school-notice-broadcast-v01/backend/
```

Stack:

| Area | Implementation |
|---|---|
| Runtime | Node.js 20+ |
| Language | TypeScript with strict settings |
| HTTP server | Fastify v5 |
| Validation | Zod |
| Database adapter | Firebase Admin SDK + Cloud Firestore |
| Push adapter | Firebase Admin SDK + Firebase Cloud Messaging |
| Security middleware | CORS, Helmet, rate limiting |

Important backend files:

```text
backend/src/app.ts        REST routes and validation
backend/src/firebase.ts   Firebase Admin, Firestore store, FCM gateway
backend/src/domain.ts     receiver domain types and token-safe public mapping
backend/src/server.ts     process entry point
backend/tests/app.test.ts API contract tests
backend/.env.example      runtime environment template
backend/firestore.rules   client-access-deny policy
```

The backend provides these endpoints:

| Method | Endpoint | Purpose |
|---|---|---|
| GET | `/health` | Health check |
| POST | `/api/v1/receivers/register` | Store or refresh a real Receiver UUID, name, app version, and FCM token |
| POST | `/api/v1/receivers/heartbeat` | Update a registered Receiver’s last-seen information |
| GET | `/api/v1/receivers` | Return token-safe public receiver records |
| GET | `/api/v1/receivers/:receiverId` | Return one token-safe receiver record |
| POST | `/api/v1/test-notice` | Send a V0.1 `TEST` FCM notice to one selected Receiver |

The test-notice route accepts this conceptual body:

```json
{
  "receiverId": "generated-receiver-uuid",
  "title": "Notice title",
  "body": "Notice text",
  "type": "TEST"
}
```

The backend sends a high-priority Android FCM message with both notification and data payloads. The data includes `noticeId`, `type`, `receiverId`, `title`, and `body`. Android notification channel ID is:

```text
school_notice_test
```

The backend was validated with strict TypeScript checks, a production build, and API contract tests. The contract test suite passed with **4/4 tests**.

### 5. Android Applications

Android project root:

```text
/home/ubuntu/school-notice-broadcast-v01/android/
```

The package IDs are fixed and must remain exactly:

| Application | Package ID | Firebase client SDK requirement |
|---|---|---|
| NoticeFlow Receiver | `app.receiver` | Yes: Firebase Messaging and the matching `google-services.json` |
| NoticeFlow Sender | `app.sender` | No Firebase client SDK required; it calls the REST backend only |

The real Firebase Android app record was registered in project `school-notics` for:

```text
app.receiver
```

Its `google-services.json` belongs only in:

```text
android/receiver-app/google-services.json
```

It is intentionally excluded from version control. Do not put a Firebase service-account file in either Android module.

#### Receiver behavior

The Receiver app has a Material-style dashboard and does the following:

1. Generates and persists a local UUID as its `receiverId`.
2. Lets the user optionally set a real display name.
3. Checks the configured backend health endpoint.
4. Creates a Firebase Installations identity.
5. Obtains a real FCM token with Firebase Messaging.
6. Calls `POST /api/v1/receivers/register` with the generated UUID, optional display name, FCM token, and version.
7. Schedules a periodic heartbeat through WorkManager.
8. Handles token refresh in `NoticeMessagingService` by re-registering with the backend.
9. Shows native Android notifications and retains the latest received notice in local state for display in the app.

The Receiver UI was improved after an early generic error. It now visibly reports these stages:

```text
Checking secure backend
Preparing Firebase
Requesting push token
Registering device
Complete
```

If a stage fails, the screen exposes a copyable diagnostic rather than hiding the exception behind a generic status message.

#### Sender behavior

The Sender app was rebuilt around a strict target-first interaction model. Its flow is:

1. Tap **Load registered receivers**.
2. Fetch only enabled real Receiver records from `GET /api/v1/receivers`.
3. Show each returned record as an explicit, selectable device card.
4. Tap exactly one real Receiver card.
5. Highlight the chosen card and display `Selected: [device]`.
6. Unlock the notice title, notice body, and send button only after selection.
7. Submit the selected receiver’s exact UUID to `POST /api/v1/test-notice`.

There is deliberately no manually typed target ID, no fake device row, and no ambiguous dropdown requirement. The Sender uses only real public receiver records returned by the backend.

### 6. Temporary HTTPS Endpoint and Important Limitation

For the real device test, the Firebase-connected Fastify backend was exposed through a temporary HTTPS proxy. The most recently used temporary base URL was:

```text
https://8081-ivbog2jy4ioauyfzy33mz-31b13873.us4.manus.computer
```

That URL is embedded into the current debug builds through `android/gradle.properties` as `BACKEND_BASE_URL`.

This is only a short-lived controlled-testing endpoint. It can expire when the workspace stops, restarts, or changes. It is not a permanent production host. Because V0.1 intentionally has no authentication, do not distribute an APK pointing at a public temporary endpoint beyond controlled testing.

The next production-readiness task is to deploy the same backend to a stable HTTPS host. Use an environment-based Firebase Admin credential or cloud runtime identity. Do not redesign the backend into a client-side Firebase-send architecture.

### 7. Debugging History and What Was Learned

The project passed through several real debugging stages. Preserve these lessons:

| Symptom | Actual finding | Resolution / conclusion |
|---|---|---|
| Receiver could not register at first | Earlier temporary backend endpoint had expired | A fresh backend instance and temporary HTTPS endpoint were created; debug apps were rebuilt with the new URL |
| Sender showed an old receiver | Firestore contained a real historical receiver record | This was expected persistent data, not fake sample data |
| Receiver showed generic “Connection needs attention” | The first UI hid the exact failure | Receiver UI was upgraded to show connection stages and copyable diagnostics |
| Receiver diagnostic contained `Value <!doctype ... cannot be converted to JSONObject` | The device reached the backend health route, which returned HTTP 200 JSON; the HTML issue was not a Fastify route failure | The diagnostic UI made the distinction visible; later the Receiver completed registration successfully |
| Sender dropdown was difficult to use and selection was visually unclear | The prior control did not make target selection explicit | Sender was redesigned around real receiver cards and gated composition |

The final screenshots and user confirmation prove that the new physical Receiver registered and the Sender loaded that live receiver. The final notice was delivered and displayed.

### 8. Current Verified End-to-End State

At handoff, these statements are true:

| Capability | Status |
|---|---|
| Backend reaches real Firestore project `school-notics` | Verified |
| Backend initializes Firebase Messaging | Verified |
| Receiver Firebase Android app `app.receiver` exists | Verified |
| Receiver creates a real UUID | Verified |
| Receiver registered against backend | Verified on real Moto device |
| Sender loaded a real Receiver record | Verified |
| Sender selected that receiver and sent a notice | Verified |
| Receiver displayed the FCM-delivered message | Verified by the user |
| Firebase Admin secrets embedded in APKs | Not allowed; none intentionally embedded |
| Permanent HTTPS deployment | Not completed; temporary endpoint only |
| Authentication and authorization | Intentionally out of scope for V0.1 |

### 9. Rules for Any Future Work

Follow these rules exactly:

1. Do not create sample receivers, hardcode names, fabricate Firestore records, or simulate FCM success.
2. Do not return `fcmToken` in an API response.
3. Do not allow Sender to send FCM messages directly.
4. Do not put Firebase Admin credentials or FCM server keys in Android builds.
5. Do not change `app.receiver` or `app.sender` package IDs.
6. Do not touch the Manga project.
7. Do not rebuild APKs repeatedly without identifying a specific code/configuration reason first.
8. Before changing Android UI behavior, confirm whether the backend log receives the expected request. This separates client-side Firebase/token problems from backend/network problems.
9. Keep V0.1 simple until a deliberate next-version scope is approved.

### 10. Recommended Next Steps

Do these in order:

1. Deploy the existing Fastify backend to a stable HTTPS host. Reuse the existing API and Firebase Admin integration.
2. Set the stable URL as `BACKEND_BASE_URL` for both Android modules and build new release/test APKs.
3. Repeat the real-device flow: Receiver registration → Sender load → explicit device selection → notice send → Receiver notification.
4. Only after that flow remains stable should the project consider authentication, sender protection, richer notice history, delivery status, or multiple device management.

Do not introduce Firebase Auth, role models, schools, classrooms, or dashboard complexity unless the project owner explicitly expands the scope.

### 11. Exact Request for the Next Developer or AI

Continue from the verified V0.1 state. First inspect the existing backend and Android code rather than generating a replacement architecture. Preserve the real Firebase project connection and the `receivers/{receiverId}` model. Prioritize a stable HTTPS deployment for the current Fastify backend, then update the Android base URL once and retest the already-proven end-to-end flow. Keep tokens secret, use real runtime records only, and do not touch the unrelated Manga codebase.

---

## End of Prompt
