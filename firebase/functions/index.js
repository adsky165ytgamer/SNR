const { onDocumentCreated } = require("firebase-functions/v2/firestore");
const logger = require("firebase-functions/logger");
const admin = require("firebase-admin");

admin.initializeApp();
const db = admin.firestore();

/**
 * Firestore is the source of truth. This trigger fans each accepted notice out
 * to the registered Receiver token using a high-priority data-only FCM message.
 * Data-only is intentional: NoticeMessagingService handles the message in the
 * background and creates the app's own notification and optional overlay.
 */
exports.pushNoticeToReceiver = onDocumentCreated(
  {
    document: "receivers/{receiverId}/notices/{noticeId}",
    region: "us-central1",
    retry: true,
  },
  async (event) => {
    const snapshot = event.data;
    if (!snapshot) return;
    const notice = snapshot.data() || {};
    const receiverId = event.params.receiverId;
    const receiver = await db.collection("receivers").doc(receiverId).get();
    const token = receiver.get("fcmToken");
    if (!token) {
      logger.warn("Receiver has no FCM token", { receiverId });
      return;
    }

    const title = String(notice.title || "School notice").trim();
    const body = String(notice.body || "").trim();
    if (!body) return;

    try {
      await admin.messaging().send({
        token,
        data: {
          noticeId: String(notice.noticeId || snapshot.id),
          receiverId,
          title,
          body,
          type: String(notice.type || "INFORMATION"),
        },
        android: {
          priority: "high",
          ttl: 24 * 60 * 60 * 1000,
        },
      });
      logger.info("Notice pushed to Receiver", { receiverId, noticeId: snapshot.id });
    } catch (error) {
      const code = error?.errorInfo?.code || "unknown";
      logger.error("FCM delivery failed", { receiverId, noticeId: snapshot.id, code });
      if (code === "messaging/registration-token-not-registered" || code === "messaging/invalid-registration-token") {
        await receiver.ref.update({ fcmToken: admin.firestore.FieldValue.delete() });
      } else {
        throw error;
      }
    }
  },
);
