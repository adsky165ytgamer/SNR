import cors from "@fastify/cors";
import helmet from "@fastify/helmet";
import rateLimit from "@fastify/rate-limit";
import Fastify, { FastifyInstance, FastifyReply } from "fastify";
import { randomUUID } from "node:crypto";
import { z } from "zod";
import { AuthenticatedUser, AuthVerifier, FcmGateway, NoticeCategory, NoticeCategoryValues, ReceiverStore, toPublicReceiver } from "./domain.js";

type NoticeLogInput = {
  noticeId: string;
  senderUid: string;
  receiverId: string;
  title: string;
  body: string;
  messageId: string;
  type: NoticeCategory;
};

const receiverId = z.string().uuid();
const registerSchema = z.object({ receiverId, name: z.string().trim().min(1).max(120).nullable().optional(), fcmToken: z.string().trim().min(1).max(16_384), appVersion: z.string().trim().min(1).max(80).nullable().optional() });
const heartbeatSchema = z.object({ receiverId, appVersion: z.string().trim().min(1).max(80).nullable().optional() });
const noticeCategorySchema = z.enum(NoticeCategoryValues);
const testNoticeSchema = z.object({ receiverId, title: z.string().trim().min(1).max(140), body: z.string().trim().min(1).max(4_000), type: z.union([noticeCategorySchema, z.literal("TEST")]), category: noticeCategorySchema.optional() });

function validationError(reply: FastifyReply, error: z.ZodError) {
  return reply.code(400).send({ success: false, error: "VALIDATION_ERROR", details: error.flatten() });
}

function isInvalidFcmTokenError(error: unknown): boolean {
  if (!error || typeof error !== "object" || !("code" in error)) return false;
  const code = String(error.code);
  return code === "messaging/registration-token-not-registered" || code === "messaging/invalid-registration-token" || code === "messaging/invalid-argument";
}

function errorCode(error: unknown): string | null {
  return error && typeof error === "object" && "code" in error ? String(error.code) : null;
}

export async function createApp(dependencies: {
  store: ReceiverStore;
  fcm: FcmGateway;
  auth?: AuthVerifier;
  logNotice?: (input: NoticeLogInput) => Promise<void>;
}): Promise<FastifyInstance> {
  const app = Fastify({ logger: { level: process.env.LOG_LEVEL ?? "info" }, bodyLimit: 32 * 1024 });
  await app.register(helmet);
  await app.register(cors, { origin: false });
  await app.register(rateLimit, { max: 120, timeWindow: "1 minute" });

  app.setErrorHandler((error, request, reply) => {
    request.log.error(error);
    const code = errorCode(error);
    if (code === "AUTH_REQUIRED") return reply.code(401).send({ success: false, error: "AUTH_REQUIRED" });
    if (code === "RECEIVER_OWNED_BY_OTHER_ACCOUNT") return reply.code(403).send({ success: false, error: code });
    if (code === "FST_ERR_CTP_INVALID_JSON" || code === "FST_ERR_CTP_INVALID_JSON_BODY" || code === "FST_ERR_CTP_INVALID_MEDIA_TYPE") return reply.code(400).send({ success: false, error: "INVALID_REQUEST_BODY" });
    if (error instanceof z.ZodError) return validationError(reply, error);
    return reply.code(500).send({ success: false, error: "INTERNAL_ERROR" });
  });

  const authenticate = async (request: { headers: { authorization?: string } }): Promise<AuthenticatedUser | null> => {
    if (!dependencies.auth) return null;
    return dependencies.auth.verifyAuthorizationHeader(request.headers.authorization);
  };

  const verifyOwnership = async (user: AuthenticatedUser, id: string) => {
    const existing = await dependencies.store.getReceiver(id);
    if (existing && existing.ownerUid && existing.ownerUid !== user.uid) {
      const error = Object.assign(new Error("Receiver belongs to another account"), { code: "RECEIVER_OWNED_BY_OTHER_ACCOUNT" });
      throw error;
    }
    return existing;
  };

  app.get("/health", async () => ({ success: true, service: "noticeflow-backend" }));

  app.post("/api/v1/receivers/register", async (request, reply) => {
    const user = await authenticate(request);
    const parsed = registerSchema.safeParse(request.body);
    if (!parsed.success) return validationError(reply, parsed.error);
    const ownerUid = user?.uid ?? "prototype-anonymous";
    if (user) await verifyOwnership(user, parsed.data.receiverId);
    const receiver = await dependencies.store.upsertReceiver({ ...parsed.data, ownerUid, name: parsed.data.name ?? null, appVersion: parsed.data.appVersion ?? null });
    return reply.code(200).send({ success: true, receiverId: receiver.receiverId, message: "Receiver registered" });
  });

  app.post("/api/v1/receivers/heartbeat", async (request, reply) => {
    const user = await authenticate(request);
    const parsed = heartbeatSchema.safeParse(request.body);
    if (!parsed.success) return validationError(reply, parsed.error);
    const existing = await dependencies.store.getReceiver(parsed.data.receiverId);
    if (!existing) return reply.code(404).send({ success: false, error: "RECEIVER_NOT_FOUND" });
    if (user && existing.ownerUid && existing.ownerUid !== user.uid) return reply.code(403).send({ success: false, error: "RECEIVER_OWNED_BY_OTHER_ACCOUNT" });
    await dependencies.store.heartbeat(parsed.data.receiverId, parsed.data.appVersion ?? null);
    return reply.code(200).send({ success: true });
  });

  app.get("/api/v1/receivers", async (request) => {
    await authenticate(request);
    return { success: true, receivers: (await dependencies.store.listReceivers()).map(toPublicReceiver) };
  });

  app.get<{ Params: { receiverId: string } }>("/api/v1/receivers/:receiverId", async (request, reply) => {
    await authenticate(request);
    const parsed = receiverId.safeParse(request.params.receiverId);
    if (!parsed.success) return validationError(reply, parsed.error);
    const receiver = await dependencies.store.getReceiver(parsed.data);
    if (!receiver) return reply.code(404).send({ success: false, error: "RECEIVER_NOT_FOUND" });
    return reply.code(200).send({ success: true, receiver: toPublicReceiver(receiver) });
  });

  app.post("/api/v1/test-notice", async (request, reply) => {
    const user = await authenticate(request);
    const parsed = testNoticeSchema.safeParse(request.body);
    if (!parsed.success) return validationError(reply, parsed.error);
    const receiver = await dependencies.store.getReceiver(parsed.data.receiverId);
    if (!receiver) return reply.code(404).send({ success: false, error: "RECEIVER_NOT_FOUND" });
    if (!receiver.enabled) return reply.code(409).send({ success: false, error: "RECEIVER_DISABLED" });
    if (!receiver.fcmToken.trim()) return reply.code(409).send({ success: false, error: "INVALID_FCM_TOKEN" });
    const noticeId = randomUUID();
    const type: NoticeCategory = parsed.data.type === "TEST" ? (parsed.data.category ?? "NOTICE") : parsed.data.type;
    try {
      const messageId = await dependencies.fcm.sendTestNotice({ receiverId: receiver.receiverId, fcmToken: receiver.fcmToken, noticeId, title: parsed.data.title, body: parsed.data.body, type });
      await dependencies.logNotice?.({ noticeId, senderUid: user?.uid ?? "prototype-anonymous", receiverId: receiver.receiverId, title: parsed.data.title, body: parsed.data.body, messageId, type });
      request.log.info({ receiverId: receiver.receiverId, noticeId, messageId, senderUid: user?.uid }, "FCM test notice sent");
      return reply.code(200).send({ success: true, message: "Notification sent", receiverId: receiver.receiverId, messageId });
    } catch (error) {
      request.log.error({ err: error, receiverId: receiver.receiverId, noticeId }, "FCM test notice failed");
      if (isInvalidFcmTokenError(error)) return reply.code(409).send({ success: false, error: "INVALID_FCM_TOKEN" });
      return reply.code(502).send({ success: false, error: "FCM_SEND_FAILED" });
    }
  });

  await app.ready();
  return app;
}
