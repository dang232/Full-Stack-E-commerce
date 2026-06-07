import { Injectable, Logger } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import type { App } from 'firebase-admin/app';
import {
  PushChannelPort,
  PushRecipient,
} from '../../domain/port/outbound/push-channel.port';

/**
 * Firebase Cloud Messaging push adapter.
 *
 * Set FIREBASE_SERVICE_ACCOUNT to a JSON-encoded service account credential
 * to enable real push delivery. When absent the adapter acts as a no-op stub.
 */
@Injectable()
export class FcmPushChannelAdapter implements PushChannelPort {
  private readonly logger = new Logger(FcmPushChannelAdapter.name);
  private readonly enabled: boolean;
  private readonly app: App | null;

  constructor(config: ConfigService) {
    const serviceAccountJson = config.get<string>('FIREBASE_SERVICE_ACCOUNT');
    let app: App | null = null;
    let enabled = false;

    if (serviceAccountJson) {
      try {
        // Lazy-require so the module loads even when firebase-admin is absent in tests
        /* eslint-disable @typescript-eslint/no-require-imports */
        const admin =
          require('firebase-admin') as typeof import('firebase-admin');
        /* eslint-enable @typescript-eslint/no-require-imports */
        app = admin.initializeApp({
          // eslint-disable-next-line @typescript-eslint/no-unsafe-argument
          credential: admin.credential.cert(JSON.parse(serviceAccountJson)),
        });
        enabled = true;
        this.logger.log('Firebase push adapter ENABLED');
      } catch (e) {
        this.logger.error('Failed to initialize Firebase push adapter', e);
      }
    } else {
      this.logger.log(
        'Firebase push adapter DISABLED (stub mode — set FIREBASE_SERVICE_ACCOUNT to activate)',
      );
    }

    this.enabled = enabled;
    this.app = app;
  }

  isEnabled(): boolean {
    return this.enabled;
  }

  async send(
    recipient: PushRecipient,
    title: string,
    body: string,
    data?: Record<string, string>,
  ): Promise<boolean> {
    if (!this.enabled || !this.app) {
      this.logger.debug(
        `[STUB] Would push to user=${recipient.userId}: "${title}"`,
      );
      return false;
    }

    try {
      /* eslint-disable @typescript-eslint/no-require-imports */
      const admin =
        require('firebase-admin') as typeof import('firebase-admin');
      /* eslint-enable @typescript-eslint/no-require-imports */
      await admin.messaging(this.app).send({
        token: recipient.deviceToken,
        notification: { title, body },
        data: data ?? {},
      });
      this.logger.log(`Push sent to user=${recipient.userId}: "${title}"`);
      return true;
    } catch (error) {
      this.logger.error(
        `Failed to send push to user=${recipient.userId}`,
        error,
      );
      return false;
    }
  }
}
