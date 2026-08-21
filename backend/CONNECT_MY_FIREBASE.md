# Connect the Local Backend to Your Existing Firebase Project

This is the clean, direct path. It does **not** create a new Firebase project, use Cloud Functions, deploy Cloud Run, or modify an Android APK.

```text
Existing Firebase project (school-notics)
        │
        │ Admin SDK credential, used only by the local backend process
        ▼
Local Fastify backend → Cloud Firestore + Firebase Cloud Messaging
```

## Best Local Wiring: Application Default Credentials

The uploaded text contains general Firebase CLI guidance, not an Admin credential. For this backend, **do not use `firebase login` as the runtime credential**. The cleanest local connection is Google Application Default Credentials (ADC), because this backend already uses `applicationDefault()` when `FIREBASE_SERVICE_ACCOUNT_JSON` is not set. [1]

On the machine where the backend runs, authenticate once with the Google account that can access Firebase project `school-notics`:

```bash
gcloud auth application-default login --no-launch-browser
gcloud config set project school-notics
```

Follow the one-time browser link and code prompt shown by `gcloud`. Then run the backend without a private key:

```bash
cd /home/ubuntu/school-notice-broadcast-v01/backend
unset FIREBASE_SERVICE_ACCOUNT_JSON
export GOOGLE_CLOUD_PROJECT=school-notics
export PORT=8080
npm start
```

This is the preferred local-only approach. It connects the existing Fastify backend to the existing Firebase project while keeping a service-account private key out of the project and out of chat.

## Alternative: Service Account File

Use this only when the backend cannot use ADC, such as a CI machine or third-party host. In the Firebase Console for **`school-notics`**, go to **Project settings → Service accounts → Firebase Admin SDK → Generate new private key**. Download the JSON file and keep it private.

> The file is a server credential. Do not paste its contents into chat, commit it to Git, place it in an APK, or put it inside this repository.

Cloud Firestore in production mode is compatible with this backend. The Firebase Admin SDK runs as a trusted server and accesses Firestore and FCM using the project’s Admin credential. [1]

## Service Account Wiring Command

Save the downloaded key somewhere outside the repository, for example in your Downloads folder. Then, from the backend folder, run:

```bash
cd /home/ubuntu/school-notice-broadcast-v01/backend
export FIREBASE_SERVICE_ACCOUNT_JSON="$(tr -d '\n' < /path/to/school-notics-admin-key.json)"
export PORT=8080
npm start
```

The backend already reads `FIREBASE_SERVICE_ACCOUNT_JSON`, initializes Firebase Admin, opens `receivers/{receiverId}` in your **existing** Firestore database, and sends FCM through that same project. No source-code change is needed.

## What a Successful Connection Means

Open this locally after the process starts:

```bash
curl http://localhost:8080/health
```

The expected response proves the API process is running:

```json
{"success":true,"service":"school-notice-broadcast-v01"}
```

The first real `POST /api/v1/receivers/register` request then proves the backend can write to **your** Firestore. A subsequent `POST /api/v1/test-notice` with a real Receiver FCM token proves the backend can send through **your** Firebase Cloud Messaging project.

## Do Not Use These for This Step

| Do not use | Why |
|---|---|
| Firebase client `google-services.json` | It belongs later in the Receiver APK, not in this Node backend |
| FCM server key | Legacy key-based sending is not used; Firebase Admin handles sending securely |
| Firebase Auth / Google Sign-In | Explicitly outside V0.1 scope |
| Cloud Functions or Cloud Run | Optional hosting choices for later; they are not required to wire the existing local backend to your Firebase project |

## References

[1]: https://firebase.google.com/docs/admin/setup "Firebase Admin SDK setup"
