# School Notice Broadcast V0.1 — Firebase and HTTPS Deployment

This handoff deploys **only the backend**. It does not compile, sign, or install Android APKs. The runtime is a small Node.js service that stores receiver records in `receivers/{receiverId}` and sends test notices through Firebase Cloud Messaging (FCM).

> **V0.1 security boundary:** Firebase Admin credentials and FCM sending remain only on the server. Neither service-account credentials nor server keys belong in Git, environment files committed to Git, or an Android APK.

## What Is Ready

| Item | State |
|---|---|
| REST API | Five V0.1 endpoints plus `GET /health` |
| Data model | Firestore `receivers/{receiverId}` only |
| FCM | Trusted Firebase Admin SDK sender with Android high-priority notification and data payloads |
| Token privacy | `fcmToken` is never returned from the API |
| Invalid FCM tokens | Firebase registration-token failures return `409 INVALID_FCM_TOKEN` |
| Client Firestore access | Explicitly denied by `firestore.rules` |
| Deployable artifact | `Dockerfile`, `.dockerignore`, production TypeScript build |

## Firebase Console Setup

First, create or select the **single Firebase project** that both the backend and Receiver application will use. Enable **Cloud Firestore in Native mode** and Cloud Messaging. The Admin SDK is designed for privileged server environments and can read/write Firestore plus send FCM messages. [1]

Deploy the committed Firestore rules before testing. These rules reject every direct client read and write, preserving the rule that only the trusted backend handles receiver documents and FCM tokens. The Admin SDK is privileged server-side code; Firestore rules govern client access. [1] [2]

```bash
# Run from /home/ubuntu/school-notice-broadcast-v01/backend
npm install --global firebase-tools
firebase login
firebase use YOUR_FIREBASE_PROJECT_ID
firebase deploy --only firestore:rules,firestore:indexes
```

The current query uses a single-field ordering on `updatedAt`, so no composite Firestore index is required; the committed indexes file is intentionally empty.

## Runtime Credentials

| Hosting choice | Credential configuration |
|---|---|
| **Google Cloud Run** | Preferred. Attach a dedicated service account and rely on Application Default Credentials (ADC). The backend automatically uses ADC when `FIREBASE_SERVICE_ACCOUNT_JSON` is absent. Google recommends ADC for Google-managed environments. [1] |
| **Third-party container host** | Store the complete minified service-account JSON only in that host’s secret manager as `FIREBASE_SERVICE_ACCOUNT_JSON`. Do not create a JSON file in the repository or bake it into the image. |
| **Local development** | Use `FIREBASE_SERVICE_ACCOUNT_JSON` from a private shell or use ADC through `GOOGLE_APPLICATION_CREDENTIALS`; never commit either credential. [1] |

For a Cloud Run service identity, grant the minimal runtime permissions needed to read/write Firestore data and send FCM messages. `Cloud Datastore User` provides Firestore data read/write access; choose the FCM API role that grants message creation in your project rather than granting Owner. [3] [4] The deployer account needs separate Cloud Run deployment permissions.

## Local Backend Verification

```bash
cd /home/ubuntu/school-notice-broadcast-v01/backend
cp .env.example .env
# Set FIREBASE_SERVICE_ACCOUNT_JSON privately in the shell or secret manager.
npm ci
npm run check
npm test
npm run build
PORT=8080 npm start
```

Then check the public health contract locally:

```bash
curl http://localhost:8080/health
# Expected: {"success":true,"service":"school-notice-broadcast-v01"}
```

## Cloud Run HTTPS Deployment

Cloud Run is the recommended first deployment target because it supplies an HTTPS service URL and supports an attached service identity. This V0.1 API intentionally has no user authentication, so it must be reachable by the Sender and Receiver applications; do not publish it outside controlled testing without first adding sender authentication.

```bash
cd /home/ubuntu/school-notice-broadcast-v01/backend
gcloud config set project YOUR_FIREBASE_PROJECT_ID
gcloud services enable run.googleapis.com cloudbuild.googleapis.com artifactregistry.googleapis.com

# Replace REGION and the already-created dedicated runtime service account.
gcloud run deploy school-notice-broadcast-v01 \
  --source . \
  --region REGION \
  --service-account noticeflow-backend@YOUR_FIREBASE_PROJECT_ID.iam.gserviceaccount.com \
  --allow-unauthenticated
```

Cloud Run returns a stable HTTPS service URL after a successful deployment. Store that URL as the `BACKEND_BASE_URL` later, when Android client work resumes. Cloud Run’s deployment documentation describes source/image deployment, public access, revisions, and region selection. [5]

## Live Smoke Test After Deployment

Use the returned HTTPS base URL, never `localhost` or an IP address. The following verifies only the backend transport and Firestore layer; it does not need an APK build.

```bash
export BACKEND_URL="https://YOUR_CLOUD_RUN_URL"
export RECEIVER_ID="$(uuidgen | tr '[:upper:]' '[:lower:]')"

# A real test requires a real Firebase FCM registration token from the Receiver app.
curl -sS -X POST "$BACKEND_URL/api/v1/receivers/register" \
  -H 'content-type: application/json' \
  --data "{\"receiverId\":\"$RECEIVER_ID\",\"name\":\"Real Receiver\",\"fcmToken\":\"REAL_FCM_TOKEN\",\"appVersion\":\"0.1.0\"}"

curl -sS "$BACKEND_URL/api/v1/receivers"

curl -sS -X POST "$BACKEND_URL/api/v1/test-notice" \
  -H 'content-type: application/json' \
  --data "{\"receiverId\":\"$RECEIVER_ID\",\"title\":\"FCM backend test\",\"body\":\"If received, the full V0.1 path works.\",\"type\":\"TEST\"}"
```

FCM documents that registration tokens can become invalid or unregistered and should no longer be used. This backend reports that condition as `409 INVALID_FCM_TOKEN`; re-registering the receiver with a new FCM token is the V0.1 recovery flow. [6]

## Endpoint and Error Contract

| Method | Endpoint | Success | Important failure responses |
|---|---|---|---|
| `POST` | `/api/v1/receivers/register` | `200` with `receiverId` | `400 VALIDATION_ERROR`, `400 INVALID_REQUEST_BODY` |
| `POST` | `/api/v1/receivers/heartbeat` | `200` | `404 RECEIVER_NOT_FOUND` |
| `GET` | `/api/v1/receivers` | `200` and public receiver fields | Never includes `fcmToken` |
| `GET` | `/api/v1/receivers/:receiverId` | `200` | `400 VALIDATION_ERROR`, `404 RECEIVER_NOT_FOUND` |
| `POST` | `/api/v1/test-notice` | `200` with FCM message ID | `404 RECEIVER_NOT_FOUND`, `409 RECEIVER_DISABLED`, `409 INVALID_FCM_TOKEN`, `502 FCM_SEND_FAILED` |

## Deliberately Deferred

Receiver and Sender APK compilation, `google-services.json` placement, Android signing, and physical-device FCM verification are all deferred until the backend has a real Firebase project, credentials, and an HTTPS URL. The backend code is ready for that next stage; it cannot send a real push until those Firebase prerequisites exist.

## References

[1]: https://firebase.google.com/docs/admin/setup "Firebase Admin SDK setup"
[2]: https://firebase.google.com/docs/firestore/security/rules-conditions "Cloud Firestore security rules"
[3]: https://docs.cloud.google.com/iam/docs/roles-permissions/firestore "Firestore IAM roles"
[4]: https://docs.cloud.google.com/iam/docs/roles-permissions/firebasecloudmessaging "Firebase Cloud Messaging IAM roles"
[5]: https://cloud.google.com/run/docs/deploying "Deploying services to Cloud Run"
[6]: https://firebase.google.com/docs/cloud-messaging/error-codes "Firebase Cloud Messaging error codes"
