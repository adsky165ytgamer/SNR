import { cert, getApps, initializeApp } from "firebase-admin/app";
import { getFirestore } from "firebase-admin/firestore";
import { getMessaging } from "firebase-admin/messaging";

const credentialJson = process.env.FIREBASE_SERVICE_ACCOUNT_JSON;
if (!credentialJson) throw new Error("FIREBASE_SERVICE_ACCOUNT_JSON is required for this one-time verification.");

const serviceAccount = JSON.parse(credentialJson);
if (!getApps().length) initializeApp({ credential: cert(serviceAccount) });

const snapshot = await getFirestore().collection("receivers").limit(1).get();
getMessaging();

console.log(JSON.stringify({
  success: true,
  projectId: serviceAccount.project_id,
  firestoreReadable: true,
  receiverDocumentPresent: !snapshot.empty,
  fcmClientInitialized: true
}));
