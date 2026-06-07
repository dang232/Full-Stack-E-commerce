import { Injectable, Logger } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { SESClient, SendEmailCommand } from '@aws-sdk/client-ses';
import {
  EmailChannelPort,
  EmailRecipient,
} from '../../domain/port/outbound/email-channel.port';
import { Notification } from '../../domain/model/notification';
import { NotificationType } from '../../domain/model/notification-type.enum';
import { TemplateService } from '../templates/template.service';

/**
 * SES email channel adapter.
 *
 * Set EMAIL_ENABLED=true together with AWS credentials to enable real delivery.
 * When disabled (the default) the adapter acts as a no-op stub that logs what
 * would be sent, so the rest of the pipeline can run without AWS access.
 */
/** Maps notification types to their Handlebars template name. */
const TEMPLATE_MAP: Partial<Record<NotificationType, string>> = {
  [NotificationType.ORDER_CREATED]: 'order-confirmed',
  [NotificationType.ORDER_SHIPPED]: 'order-shipped',
};

@Injectable()
export class SesEmailChannelAdapter implements EmailChannelPort {
  private readonly logger = new Logger(SesEmailChannelAdapter.name);
  private readonly enabled: boolean;
  private readonly fromAddress: string;
  private readonly ses: SESClient | null;

  constructor(
    private readonly config: ConfigService,
    private readonly templateService: TemplateService,
  ) {
    this.enabled = this.config.get<string>('EMAIL_ENABLED', 'false') === 'true';
    this.fromAddress = this.config.get<string>('EMAIL_FROM_ADDRESS', 'noreply@vnshop.vn');

    if (this.enabled) {
      this.ses = new SESClient({
        region: this.config.get<string>('AWS_REGION', 'ap-southeast-1'),
      });
      this.logger.log(`Email channel ENABLED (from: ${this.fromAddress})`);
    } else {
      this.ses = null;
      this.logger.log('Email channel DISABLED (stub mode — set EMAIL_ENABLED=true to activate)');
    }
  }

  isEnabled(): boolean {
    return this.enabled;
  }

  async send(recipient: EmailRecipient, notification: Notification): Promise<boolean> {
    if (!this.enabled || !this.ses) {
      this.logger.debug(
        `[STUB] Would send email to ${recipient.email}: "${notification.title}"`,
      );
      return false;
    }

    try {
      await this.ses.send(
        new SendEmailCommand({
          Source: this.fromAddress,
          Destination: { ToAddresses: [recipient.email] },
          Message: {
            Subject: { Data: notification.title, Charset: 'UTF-8' },
            Body: {
              Html: {
                Data: this.buildHtmlBody(notification),
                Charset: 'UTF-8',
              },
            },
          },
        }),
      );
      this.logger.log(
        `Email sent to ${recipient.email}: "${notification.title}" [notif=${notification.id}]`,
      );
      return true;
    } catch (error) {
      this.logger.error(`Failed to send email to ${recipient.email}`, error);
      return false;
    }
  }

  private buildHtmlBody(notification: Notification): string {
    const templateName = TEMPLATE_MAP[notification.type];
    const orderId = (notification.metadata?.['orderId'] as string | undefined) ?? undefined;

    if (templateName) {
      return this.templateService.render(templateName, {
        title: notification.title,
        body: notification.body,
        deepLink: notification.deepLink ?? undefined,
        orderId,
      });
    }

    // Fallback for types without a dedicated template
    return this.templateService.render('__fallback__', {
      title: notification.title,
      body: notification.body,
      deepLink: notification.deepLink ?? undefined,
    });
  }
}
