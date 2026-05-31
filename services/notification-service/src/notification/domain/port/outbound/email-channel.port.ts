import { Notification } from '../../model/notification';

export interface EmailRecipient {
  userId: string;
  email: string;
}

export interface EmailChannelPort {
  /**
   * Send a notification via email. Returns true if sent successfully.
   * Implementations should be idempotent (safe to retry).
   */
  send(recipient: EmailRecipient, notification: Notification): Promise<boolean>;

  /** Whether this channel is enabled (has valid credentials). */
  isEnabled(): boolean;
}

export const EMAIL_CHANNEL_PORT = Symbol('EMAIL_CHANNEL_PORT');
