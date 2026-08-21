import { cert, initializeApp } from "firebase-admin/app";
import { getMessaging } from "firebase-admin/messaging";

const credential = JSON.parse(process.env.FIREBASE_SERVICE_ACCOUNT_JSON ?? "");
initializeApp({ credential: cert(credential) });

try {
  await getMessaging().send({ token: "not-a-real-fcm-token", notification: { title: "probe", body: "probe" } });
  console.log(JSON.stringify({ reachable: true, unexpected: "FCM accepted a deliberately invalid token" }));
} catch (error) {
  const code = typeof error === "object" && error && "code" in error ? String(error.code) : "UNKNOWN";
  const message = error instanceof Error ? error.message : String(error);
  const transportReachable = code === "messaging/invalid-registration-token" || code === "messaging/invalid-argument" || code === "messaging/registration-token-not-registered";
  console.log(JSON.stringify({ reachable: transportReachable, firebaseErrorCode: code, message }, null, 2));
  if (!transportReachable) process.exitCode = 1;
}
