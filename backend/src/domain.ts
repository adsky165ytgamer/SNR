export type ReceiverRecord = {
  receiverId: string;
  name: string | null;
  fcmToken: string;
  platform: "android";
  appVersion: string | null;
  enabled: boolean;
  createdAt: string | null;
  updatedAt: string | null;
  lastSeenAt: string | null;
};

export type ReceiverPublic = Omit<ReceiverRecord, "fcmToken" | "createdAt">;

export type ReceiverRegistration = {
  receiverId: string;
  name: string | null;
  fcmToken: string;
  appVersion: string | null;
};

export type ReceiverStore = {
  upsertReceiver(input: ReceiverRegistration): Promise<ReceiverRecord>;
  heartbeat(receiverId: string, appVersion: string | null): Promise<ReceiverRecord | null>;
  listReceivers(): Promise<ReceiverRecord[]>;
  getReceiver(receiverId: string): Promise<ReceiverRecord | null>;
};

export type FcmGateway = {
  sendTestNotice(input: { receiverId: string; fcmToken: string; noticeId: string; title: string; body: string; type: "TEST" }): Promise<string>;
};

export const toPublicReceiver = (receiver: ReceiverRecord): ReceiverPublic => ({
  receiverId: receiver.receiverId,
  name: receiver.name,
  platform: receiver.platform,
  appVersion: receiver.appVersion,
  enabled: receiver.enabled,
  lastSeenAt: receiver.lastSeenAt,
  updatedAt: receiver.updatedAt
});
