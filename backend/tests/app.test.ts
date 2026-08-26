import assert from "node:assert/strict";
import test from "node:test";
import { createApp } from "../src/app.js";
import { AuthVerifier, FcmGateway, ReceiverRecord, ReceiverRegistration, ReceiverStore } from "../src/domain.js";

class MemoryStore implements ReceiverStore {
  private readonly records = new Map<string, ReceiverRecord>();
  async upsertReceiver(input: ReceiverRegistration) { const now = new Date().toISOString(); const previous = this.records.get(input.receiverId); const record: ReceiverRecord = { receiverId: input.receiverId, ownerUid: input.ownerUid, name: input.name, fcmToken: input.fcmToken, platform: "android", appVersion: input.appVersion, enabled: true, createdAt: previous?.createdAt ?? now, updatedAt: now, lastSeenAt: now }; this.records.set(record.receiverId, record); return record; }
  async heartbeat(receiverId: string, appVersion: string | null) { const record = this.records.get(receiverId); if (!record) return null; record.appVersion = appVersion; record.updatedAt = record.lastSeenAt = new Date().toISOString(); return record; }
  async listReceivers() { return [...this.records.values()]; }
  async getReceiver(receiverId: string) { return this.records.get(receiverId) ?? null; }
}
class MemoryFcm implements FcmGateway { sent: Parameters<FcmGateway["sendTestNotice"]>[0][] = []; async sendTestNotice(input: Parameters<FcmGateway["sendTestNotice"]>[0]) { this.sent.push(input); return "projects/test/messages/abc"; } }

test("V0.1 registers without returning the FCM token and sends a real gateway request", async () => {
  const store = new MemoryStore(); const fcm = new MemoryFcm(); const app = await createApp({ store, fcm }); const receiverId = "db870847-a78c-4926-8be7-498864df0711";
  const registered = await app.inject({ method: "POST", url: "/api/v1/receivers/register", payload: { receiverId, name: "Live panel", fcmToken: "token-from-firebase", appVersion: "1.0.0" } });
  assert.equal(registered.statusCode, 200);
  const receivers = await app.inject({ method: "GET", url: "/api/v1/receivers" });
  assert.equal(receivers.statusCode, 200); assert.equal(receivers.body.includes("token-from-firebase"), false);
  const notice = await app.inject({ method: "POST", url: "/api/v1/test-notice", payload: { receiverId, title: "Test Notice", body: "Backend to Receiver", type: "NOTICE" } });
  assert.equal(notice.statusCode, 200); assert.equal(fcm.sent.length, 1); assert.equal(fcm.sent[0]?.type, "NOTICE"); await app.close();
});

test("V0.1 rejects missing receivers", async () => {
  const app = await createApp({ store: new MemoryStore(), fcm: new MemoryFcm() });
  const response = await app.inject({ method: "POST", url: "/api/v1/test-notice", payload: { receiverId: "db870847-a78c-4926-8be7-498864df0711", title: "Test", body: "Test", type: "HOMEWORK" } });
  assert.equal(response.statusCode, 404); assert.equal(JSON.parse(response.body).error, "RECEIVER_NOT_FOUND"); await app.close();
});

test("V0.1 maps a rejected Firebase registration token to a safe 409 response", async () => {
  const store = new MemoryStore(); const receiverId = "db870847-a78c-4926-8be7-498864df0711";
  await store.upsertReceiver({ receiverId, ownerUid: "prototype-anonymous", name: null, fcmToken: "expired-token", appVersion: null });
  const fcm: FcmGateway = { async sendTestNotice() { throw { code: "messaging/registration-token-not-registered" }; } };
  const app = await createApp({ store, fcm });
  const response = await app.inject({ method: "POST", url: "/api/v1/test-notice", payload: { receiverId, title: "Test", body: "Test", type: "NEWS" } });
  assert.equal(response.statusCode, 409); assert.equal(JSON.parse(response.body).error, "INVALID_FCM_TOKEN"); await app.close();
});

test("authenticated routes reject missing Firebase credentials", async () => {
  const auth: AuthVerifier = {
    async verifyAuthorizationHeader(value) {
      if (value !== "Bearer valid-token") throw Object.assign(new Error("invalid"), { code: "AUTH_REQUIRED" });
      return { uid: "google-user-1", email: "teacher@example.com", displayName: "Teacher" };
    }
  };
  const app = await createApp({ store: new MemoryStore(), fcm: new MemoryFcm(), auth });
  const response = await app.inject({ method: "GET", url: "/api/v1/receivers" });
  assert.equal(response.statusCode, 401);
  const valid = await app.inject({ method: "GET", url: "/api/v1/receivers", headers: { authorization: "Bearer valid-token" } });
  assert.equal(valid.statusCode, 200);
  await app.close();
});

test("V0.1 rejects malformed JSON with a safe 400 response", async () => {
  const app = await createApp({ store: new MemoryStore(), fcm: new MemoryFcm() });
  const response = await app.inject({ method: "POST", url: "/api/v1/receivers/register", headers: { "content-type": "application/json" }, payload: "{not valid json" });
  assert.equal(response.statusCode, 400); assert.equal(JSON.parse(response.body).error, "INVALID_REQUEST_BODY"); await app.close();
});

test("V0.1 rejects unsupported categories while normalizing legacy TEST to Notice", async () => {
  const store = new MemoryStore(); const fcm = new MemoryFcm(); const receiverId = "db870847-a78c-4926-8be7-498864df0711";
  await store.upsertReceiver({ receiverId, ownerUid: "prototype-anonymous", name: null, fcmToken: "token-from-firebase", appVersion: null });
  const app = await createApp({ store, fcm });
  const unsupported = await app.inject({ method: "POST", url: "/api/v1/test-notice", payload: { receiverId, title: "Test", body: "Test", type: "IMPORTANT" } });
  assert.equal(unsupported.statusCode, 400);
  const legacy = await app.inject({ method: "POST", url: "/api/v1/test-notice", payload: { receiverId, title: "Test", body: "Test", type: "TEST" } });
  assert.equal(legacy.statusCode, 200); assert.equal(fcm.sent[0]?.type, "NOTICE"); await app.close();
});
