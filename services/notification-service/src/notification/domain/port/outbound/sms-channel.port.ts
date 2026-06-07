import { Notification } from '../../model/notification';

export interface SmsRecipient {
  userId: string;
  phoneNumber: string;
}

export interface SmsChannelPort {
  /**
   * Send a notification via SMS. Returns true if sent successfully.
   * Implementations should be idempotent (safe to retry).
   */
  send(recipient: SmsRecipient, notification: Notification): Promise<boolean>;

  /** Whether this channel is enabled (has valid credentials). */
  isEnabled(): boolean;
}

export const SMS_CHANNEL_PORT = Symbol('SMS_CHANNEL_PORT');
