import { Injectable } from '@nestjs/common';
import { AppConfigDto } from './dto/app-config.dto.js';

@Injectable()
export class ConfigurationService {
  getConfig(): AppConfigDto {
    return {
      brand: {
        name: process.env.BRAND_NAME ?? 'VNShop',
        tagline: process.env.BRAND_TAGLINE ?? 'MARKETPLACE',
        logoUrl: process.env.BRAND_LOGO_URL ?? '',
      },
      social: {
        facebook: process.env.SOCIAL_FACEBOOK ?? 'https://facebook.com',
        instagram: process.env.SOCIAL_INSTAGRAM ?? 'https://instagram.com',
        twitter: process.env.SOCIAL_TWITTER ?? 'https://x.com',
        youtube: process.env.SOCIAL_YOUTUBE ?? 'https://youtube.com',
      },
      payment: {
        providers: (
          process.env.PAYMENT_PROVIDERS ?? 'VNPay,MoMo,COD,Visa,Mastercard'
        ).split(','),
        defaultMethod: process.env.PAYMENT_DEFAULT ?? 'COD',
      },
      features: {
        flashSale: process.env.FEATURE_FLASH_SALE !== 'false',
        messaging: process.env.FEATURE_MESSAGING !== 'false',
        notifications: process.env.FEATURE_NOTIFICATIONS !== 'false',
        reviews: process.env.FEATURE_REVIEWS !== 'false',
      },
      support: {
        phone: process.env.SUPPORT_PHONE ?? '1900-0000',
        email: process.env.SUPPORT_EMAIL ?? 'support@vnshop.vn',
        hours: process.env.SUPPORT_HOURS ?? '24/7',
      },
      websocket: {
        notificationsPath:
          process.env.WS_NOTIFICATIONS_PATH ?? '/ws/notifications',
        messagingPath: process.env.WS_MESSAGING_PATH ?? '/ws/messaging',
        maxReconnectAttempts: parseInt(
          process.env.WS_MAX_RECONNECT ?? '5',
          10,
        ),
        reconnectBaseMs: parseInt(
          process.env.WS_RECONNECT_BASE_MS ?? '2000',
          10,
        ),
        reconnectCapMs: parseInt(
          process.env.WS_RECONNECT_CAP_MS ?? '30000',
          10,
        ),
      },
    };
  }
}
