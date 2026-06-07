import { Test } from '@nestjs/testing';
import { ConfigService } from '@nestjs/config';
import { SesEmailChannelAdapter } from '../ses-email-channel.adapter';
import { TemplateService } from '../../templates/template.service';
import { Notification } from '../../../domain/model/notification';
import { NotificationType } from '../../../domain/model/notification-type.enum';

// Mock the AWS SES client so we never hit the network
const mockSend = jest.fn();
jest.mock('@aws-sdk/client-ses', () => ({
  SESClient: jest.fn().mockImplementation(() => ({ send: mockSend })),
  SendEmailCommand: jest.fn().mockImplementation((input) => ({ input })),
}));

describe('SesEmailChannelAdapter', () => {
  const mockTemplateService = {
    render: jest.fn().mockReturnValue('<html>rendered</html>'),
  };

  function buildConfig(overrides: Record<string, string> = {}) {
    const defaults: Record<string, string> = {
      EMAIL_ENABLED: 'false',
      EMAIL_FROM_ADDRESS: 'noreply@vnshop.vn',
      AWS_REGION: 'ap-southeast-1',
      ...overrides,
    };
    return {
      get: jest.fn(
        (key: string, fallback?: string) => defaults[key] ?? fallback ?? '',
      ),
    };
  }

  function buildNotification(
    type = NotificationType.ORDER_CREATED,
    deepLink?: string,
  ) {
    return Notification.create({
      userId: 'user-1',
      type,
      title: 'Order confirmed',
      body: 'Your order is confirmed.',
      deepLink,
      metadata: { orderId: 'ORD-001' },
    });
  }

  beforeEach(() => {
    jest.clearAllMocks();
  });

  describe('stub mode (EMAIL_ENABLED=false)', () => {
    let adapter: SesEmailChannelAdapter;

    beforeEach(async () => {
      const module = await Test.createTestingModule({
        providers: [
          SesEmailChannelAdapter,
          { provide: ConfigService, useValue: buildConfig() },
          { provide: TemplateService, useValue: mockTemplateService },
        ],
      }).compile();
      adapter = module.get(SesEmailChannelAdapter);
    });

    it('isEnabled returns false', () => {
      expect(adapter.isEnabled()).toBe(false);
    });

    it('send returns false without hitting SES', async () => {
      const result = await adapter.send(
        { userId: 'user-1', email: 'buyer@example.com' },
        buildNotification(),
      );
      expect(result).toBe(false);
      expect(mockSend).not.toHaveBeenCalled();
    });
  });

  describe('enabled mode (EMAIL_ENABLED=true)', () => {
    let adapter: SesEmailChannelAdapter;

    beforeEach(async () => {
      const module = await Test.createTestingModule({
        providers: [
          SesEmailChannelAdapter,
          {
            provide: ConfigService,
            useValue: buildConfig({ EMAIL_ENABLED: 'true' }),
          },
          { provide: TemplateService, useValue: mockTemplateService },
        ],
      }).compile();
      adapter = module.get(SesEmailChannelAdapter);
    });

    it('isEnabled returns true', () => {
      expect(adapter.isEnabled()).toBe(true);
    });

    it('send calls SES and returns true on success', async () => {
      mockSend.mockResolvedValue({});

      const result = await adapter.send(
        { userId: 'user-1', email: 'buyer@example.com' },
        buildNotification(),
      );

      expect(result).toBe(true);
      expect(mockSend).toHaveBeenCalledTimes(1);
    });

    it('uses named template for ORDER_CREATED type', async () => {
      mockSend.mockResolvedValue({});

      await adapter.send(
        { userId: 'user-1', email: 'buyer@example.com' },
        buildNotification(NotificationType.ORDER_CREATED),
      );

      expect(mockTemplateService.render).toHaveBeenCalledWith(
        'order-confirmed',
        expect.objectContaining({ title: 'Order confirmed' }),
      );
    });

    it('uses named template for ORDER_SHIPPED type', async () => {
      mockSend.mockResolvedValue({});

      await adapter.send(
        { userId: 'user-1', email: 'buyer@example.com' },
        buildNotification(NotificationType.ORDER_SHIPPED),
      );

      expect(mockTemplateService.render).toHaveBeenCalledWith(
        'order-shipped',
        expect.objectContaining({ title: 'Order confirmed' }),
      );
    });

    it('uses fallback template for types without a mapping', async () => {
      mockSend.mockResolvedValue({});

      await adapter.send(
        { userId: 'user-1', email: 'buyer@example.com' },
        buildNotification(NotificationType.PAYMENT_COMPLETED),
      );

      expect(mockTemplateService.render).toHaveBeenCalledWith(
        '__fallback__',
        expect.objectContaining({ title: 'Order confirmed' }),
      );
    });

    it('returns false and does not throw when SES throws', async () => {
      mockSend.mockRejectedValue(new Error('SES unavailable'));

      const result = await adapter.send(
        { userId: 'user-1', email: 'buyer@example.com' },
        buildNotification(),
      );

      expect(result).toBe(false);
    });

    it('passes deepLink to template context when present', async () => {
      mockSend.mockResolvedValue({});

      await adapter.send(
        { userId: 'user-1', email: 'buyer@example.com' },
        buildNotification(NotificationType.ORDER_CREATED, '/orders/123'),
      );

      expect(mockTemplateService.render).toHaveBeenCalledWith(
        'order-confirmed',
        expect.objectContaining({ deepLink: '/orders/123' }),
      );
    });
  });
});
