# Reviewable Cloud Run Configuration — `school-notics`

This document is the **backend-only** deployment plan for Firebase project **`school-notics`**. It has not been executed. Running the commands will enable Google Cloud APIs, create a runtime identity, grant project IAM roles, deploy Firestore rules, and publish a public HTTPS backend; obtain explicit approval before running any of them.

> **No APK action is included.** The Sender and Receiver package IDs remain `app.sender` and `app.receiver`, and APK compilation remains paused.

## Selected Configuration

| Configuration item | Value |
|---|---|
| Firebase / Google Cloud project | `school-notics` |
| Cloud Run service | `school-notice-broadcast-v01` |
| Runtime service account | `school-notice-backend@school-notics.iam.gserviceaccount.com` |
| Default region | `asia-south1` (Mumbai); change before deployment if another region better matches the Firestore location or expected devices |
| Service visibility | Public HTTPS, required by the deliberate V0.1 no-auth API contract |
| Runtime credentials | Cloud Run Application Default Credentials; no `FIREBASE_SERVICE_ACCOUNT_JSON` on Cloud Run |
| Database policy | Direct client access denied by `firestore.rules`; trusted backend uses Firebase Admin SDK |

Cloud Run exposes an HTTPS endpoint after deployment. Application Default Credentials are the recommended Admin SDK setup in Google-managed environments, avoiding a copied service-account key. [1] [2]

## Actions That Require Your Explicit Confirmation

| Action | Cloud resource effect |
|---|---|
| Enable APIs | Activates Cloud Run, Cloud Build, Artifact Registry, Firestore, and Firebase Cloud Messaging APIs in `school-notics` |
| Create runtime identity | Adds the dedicated `school-notice-backend` service account |
| Grant IAM | Allows that runtime identity to read/write Firestore data and create FCM messages |
| Deploy Firestore rules | Applies the server-only client-access policy from this repository |
| Deploy Cloud Run | Builds and publishes the backend under a public HTTPS URL |

## Exact Commands — Do Not Run Yet

```bash
set -euo pipefail

export PROJECT_ID="school-notics"
export REGION="asia-south1"
export SERVICE_NAME="school-notice-broadcast-v01"
export RUNTIME_SA_NAME="school-notice-backend"
export RUNTIME_SA_EMAIL="${RUNTIME_SA_NAME}@${PROJECT_ID}.iam.gserviceaccount.com"

gcloud config set project "$PROJECT_ID"

# Enables the services required to build, run, access Firestore, and send FCM.
gcloud services enable \
  run.googleapis.com \
  cloudbuild.googleapis.com \
  artifactregistry.googleapis.com \
  firestore.googleapis.com \
  firebase.googleapis.com \
  fcm.googleapis.com

# Creates a dedicated non-human runtime identity.
gcloud iam service-accounts create "$RUNTIME_SA_NAME" \
  --display-name="School Notice Broadcast backend runtime"

# Runtime least-privilege data and message-send permissions.
gcloud projects add-iam-policy-binding "$PROJECT_ID" \
  --member="serviceAccount:${RUNTIME_SA_EMAIL}" \
  --role="roles/datastore.user"
gcloud projects add-iam-policy-binding "$PROJECT_ID" \
  --member="serviceAccount:${RUNTIME_SA_EMAIL}" \
  --role="roles/firebasecloudmessaging.admin"

# From the backend directory, deploy server-only Firestore client rules.
cd /home/ubuntu/school-notice-broadcast-v01/backend
npx --yes firebase-tools deploy \
  --project "$PROJECT_ID" \
  --only firestore:rules,firestore:indexes

# Builds the Dockerfile and deploys the HTTPS REST service.
gcloud run deploy "$SERVICE_NAME" \
  --source . \
  --region "$REGION" \
  --service-account "$RUNTIME_SA_EMAIL" \
  --allow-unauthenticated \
  --set-env-vars="NODE_ENV=production,LOG_LEVEL=info"
```

`roles/datastore.user` provides read/write access to Firestore data. The Firebase Cloud Messaging API Admin role contains the FCM API message-creation permission required by the Admin SDK’s send operation. [3] [4]

## IAM Verification Record

The official Google Cloud IAM references were checked on 21 August 2026. They identify **Cloud Datastore User** as `roles/datastore.user` with Firestore data read/write access, and **Firebase Cloud Messaging API Admin** as `roles/firebasecloudmessaging.admin` with `cloudmessaging.messages.create`. Those are the two runtime roles used in the reviewable command block above.

## Post-Deploy Validation

After the deploy command reports the HTTPS URL, run the following read-only health check.

```bash
export BACKEND_URL="https://URL_RETURNED_BY_CLOUD_RUN"
curl --fail --silent --show-error "$BACKEND_URL/health"
# Expected: {"success":true,"service":"school-notice-broadcast-v01"}
```

Do not set `FIREBASE_SERVICE_ACCOUNT_JSON` on Cloud Run unless ADC is unavailable for a specific diagnosed reason. The backend uses the Cloud Run runtime identity when the environment secret is absent. Firestore rules continue to block direct application access, while the server’s Firebase Admin SDK performs the trusted database and FCM operations. [1] [5]

## Production Boundary Note

The public service is intentional only because V0.1 explicitly omits authentication. Rate limiting, request validation, helmet headers, and CORS rejection are already configured. For any deployment beyond controlled testing, add sender authentication before publishing a generally accessible endpoint.

## Deployment Attempt Status

On 21 August 2026, the approved deployment attempt reached the Google Cloud Run console for project `school-notics`, but the browser session subsequently returned to the Google account chooser showing the available account as signed out. No Google Cloud APIs, IAM roles, Firestore rules, service accounts, or Cloud Run services were changed by this attempt. The next action is to sign in again to the browser session and resume from the Cloud Run console.

After a subsequent sign-in, Cloud Shell was provisioned for `school-notics`, but accepting its OAuth authorization prompt redirected the browser to a blank relay page rather than returning control to the terminal. No deployment command was entered or executed during that authorization sequence.

Closing the relay tab and navigating back in the browser did not restore the Cloud Shell terminal, so the deployment remains blocked on a stable authenticated Cloud Shell/browser session. No cloud resources were changed.

## References

[1]: https://firebase.google.com/docs/admin/setup "Firebase Admin SDK setup and Application Default Credentials"
[2]: https://cloud.google.com/run/docs/deploying "Deploying services to Cloud Run"
[3]: https://docs.cloud.google.com/iam/docs/roles-permissions/firestore "Firestore IAM roles"
[4]: https://docs.cloud.google.com/iam/docs/roles-permissions/firebasecloudmessaging "Firebase Cloud Messaging IAM roles"
[5]: https://firebase.google.com/docs/firestore/security/rules-conditions "Cloud Firestore security rules"
