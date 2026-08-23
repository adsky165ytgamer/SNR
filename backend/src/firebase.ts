import { App, applicationDefault, cert, getApp, getApps, initializeApp } from "firebase-admin/app";
import { FieldValue, Firestore, Timestamp, getFirestore } from "firebase-admin/firestore";
import { Messaging, getMessaging } from "firebase-admin/messaging";
import { FcmGateway, ReceiverRecord, ReceiverRegistration, ReceiverStore } from "./domain.js";

const asIsoString = (value: unknown): string | null => value instanceof Timestamp ? value.toDate().toISOString() : null;

export function firebaseAdminApp(): App {
  if (getApps().length > 0) return getApp();
  const serviceAccountJson = process.env.FIREBASE_SERVICE_ACCOUNT_JSON;
  if (serviceAccountJson) return initializeApp({ credential: cert(JSON.parse(serviceAccountJson)) });
  const projectId = process.env.GOOGLE_CLOUD_PROJECT ?? "school-notics";
  return initializeApp({ credential: applicationDefault(), projectId });
}

export function createFirebaseDependencies(): { store: ReceiverStore; fcm: FcmGateway; logNotice: (input: { noticeId: string; senderUid: string; receiverId: string; title: string; body: string; messageId: string; type: "TEST" }) => Promise<void> } {
  const app = firebaseAdminApp();
  const firestore = getFirestore(app);
  return {
    store: new FirestoreReceiverStore(firestore),
    fcm: new FirebaseMessagingGateway(getMessaging(app)),
    logNotice: async (input) => {
      await firestore.collection("notices").doc(input.noticeId).set({
        noticeId: input.noticeId,
        senderUid: input.senderUid,
        receiverId: input.receiverId,
        title: input.title,
        body: input.body,
        messageId: input.messageId,
        type: input.type,
        dispatchedAt: FieldValue.serverTimestamp()
      });
    }
  };
}

class FirestoreReceiverStore implements ReceiverStore {
  constructor(private readonly firestore: Firestore) {}
  private document(receiverId: string) { return this.firestore.collection("receivers").doc(receiverId); }
  private toRecord(receiverId: string, data: Record<string, unknown>): ReceiverRecord {
    return {
      receiverId,
      ownerUid: typeof data.ownerUid === "string" ? data.ownerUid : null,
      name: typeof data.name === "string" ? data.name : null,
      fcmToken: typeof data.fcmToken === "string" ? data.fcmToken : "",
      platform: "android",
      appVersion: typeof data.appVersion === "string" ? data.appVersion : null,
      enabled: data.enabled !== false,
      createdAt: asIsoString(data.createdAt),
      updatedAt: asIsoString(data.updatedAt),
      lastSeenAt: asIsoString(data.lastSeenAt)
    };
  }
  async upsertReceiver(input: ReceiverRegistration): Promise<ReceiverRecord> {
    const reference = this.document(input.receiverId);
    await this.firestore.runTransaction(async (transaction) => {
      const existing = await transaction.get(reference);
      transaction.set(reference, {
        receiverId: input.receiverId,
        ownerUid: input.ownerUid,
        name: input.name,
        fcmToken: input.fcmToken,
        platform: "android",
        appVersion: input.appVersion,
        enabled: true,
        createdAt: existing.exists ? existing.get("createdAt") : FieldValue.serverTimestamp(),
        updatedAt: FieldValue.serverTimestamp(),
        lastSeenAt: FieldValue.serverTimestamp()
      }, { merge: true });
    });
    return (await this.getReceiver(input.receiverId))!;
  }
  async heartbeat(receiverId: string, appVersion: string | null): Promise<ReceiverRecord | null> {
    const reference = this.document(receiverId);
    const existing = await reference.get();
    if (!existing.exists) return null;
    await reference.update({ appVersion, updatedAt: FieldValue.serverTimestamp(), lastSeenAt: FieldValue.serverTimestamp() });
    return (await this.getReceiver(receiverId))!;
  }
  async listReceivers(): Promise<ReceiverRecord[]> {
    const snapshot = await this.firestore.collection("receivers").orderBy("updatedAt", "desc").get();
    return snapshot.docs.map((document) => this.toRecord(document.id, document.data()));
  }
  async getReceiver(receiverId: string): Promise<ReceiverRecord | null> {
    const snapshot = await this.document(receiverId).get();
    return snapshot.exists ? this.toRecord(snapshot.id, snapshot.data()!) : null;
  }
}

class FirebaseMessagingGateway implements FcmGateway {
  constructor(private readonly messaging: Messaging) {}
  sendTestNotice(input: { receiverId: string; fcmToken: string; noticeId: string; title: string; body: string; type: "TEST" }): Promise<string> {
    return this.messaging.send({
      token: input.fcmToken,
      data: { noticeId: input.noticeId, type: input.type, receiverId: input.receiverId, title: input.title, body: input.body },
      android: { priority: "high" }
    });
  }
}
