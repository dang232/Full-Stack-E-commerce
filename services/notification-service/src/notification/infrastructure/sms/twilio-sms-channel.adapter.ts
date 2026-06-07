import { Injectable, Logger, OnModuleInit } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import {
  SmsChannelPort,
  SmsRecipient,
} from '../../domain/port/outbound/sms-channel.port';
import { Notification } from '../../domain/model/notification';

/**
 * Twilio SMS channel adapter.
 *
 * Set SMS_ENABLED=true and provide TWILIO_ACCOUNT_SID, TWILIO_AUTH_TOKEN,
 * TWILIO_FROM_NUMBER to enable real delivery.
 * When disabled (the default) the adapter acts as a no-op stub.
 * Fail-fast on startup if enabled but any credential is missing.
 */
@Injectable()
export class TwilioSmsChannelAdapter implements SmsChannelPort, OnModuleInit {
  private readonly logger = new Logger(TwilioSmsChannelAdapter.name);
  private readonly enabled: boolean;
  private readonly accountSid: string;
  private readonly authToken: string;
  private readonly fromNumber: string;

  constructor(private readonly config: ConfigService) {
    this.enabled = this.config.get<string>('SMS_ENABLED', 'false') === 'true';
    this.accountSid = this.config.get<string>('TWILIO_ACCOUNT_SID', '');
    this.authToken = this.config.get<string>('TWILIO_AUTH_TOKEN', '');
    this.fromNumber = this.config.get<string>('TWILIO_FROM_NUMBER', '');
  }

  onModuleInit(): void {
    if (!this.enabled) {
      this.logger.log(
        'SMS channel DISABLED (stub mode — set SMS_ENABLED=true with Twilio credentials to activate)',
      );
      return;
    }

    const missing: string[] = [];
    if (!this.accountSid) missing.push('TWILIO_ACCOUNT_SID');
    if (!this.authToken) missing.push('TWILIO_AUTH_TOKEN');
    if (!this.fromNumber) missing.push('TWILIO_FROM_NUMBER');

    if (missing.length > 0) {
      throw new Error(
        `SMS channel is enabled but missing required credentials: ${missing.join(', ')}`,
      );
    }

    this.logger.log(`SMS channel ENABLED (from: ${this.fromNumber})`);
  }

  isEnabled(): boolean {
    return this.enabled;
  }

  async send(recipient: SmsRecipient, notification: Notification): Promise<boolean> {
    if (!this.enabled) {
      this.logger.debug(
        `[STUB] Would send SMS to ${recipient.phoneNumber}: "${notification.title}"`,
      );
      return false;
    }

    try {
      // Lazy-require so the module loads even when twilio is absent in tests
      // eslint-disable-next-line @typescript-eslint/no-require-imports
      const twilio = require('twilio') as typeof import('twilio');
      const client = twilio(this.accountSid, this.authToken);

      const body = this.buildSmsBody(notification);
      await client.messages.create({
        from: this.fromNumber,
        to: recipient.phoneNumber,
        body,
      });

      this.logger.log(
        `SMS sent to ${recipient.phoneNumber}: "${notification.title}" [notif=${notification.id}]`,
      );
      return true;
    } catch (error) {
      this.logger.error(`Failed to send SMS to ${recipient.phoneNumber}`, error);
      return false;
    }
  }

  private buildSmsBody(notification: Notification): string {
    const parts = [`[VNShop] ${notification.title}`, notification.body];
    if (notification.deepLink) {
      parts.push(`Chi tiết: https://vnshop.vn${notification.deepLink}`);
    }
    return parts.join('\n');
  }
}
