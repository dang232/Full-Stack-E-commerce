export interface PushRecipient {
  userId: string;
  deviceToken: string;
}

export interface PushChannelPort {
  /**
   * Send a push notification to a device token. Returns true if sent successfully.
   * Implementations should be idempotent (safe to retry).
   */
  send(
    recipient: PushRecipient,
    title: string,
    body: string,
    data?: Record<string, string>,
  ): Promise<boolean>;

  /** Whether this channel is enabled (has valid credentials). */
  isEnabled(): boolean;
}

export const PUSH_CHANNEL_PORT = Symbol('PUSH_CHANNEL_PORT');
