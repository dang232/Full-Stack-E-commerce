import { Test } from '@nestjs/testing';
import { NotificationPreferencesController } from '../notification-preferences.controller';
import { GetPreferencesUseCase } from '../../../application/query/get-preferences.use-case';
import { UpdatePreferencesUseCase } from '../../../application/command/update-preferences.use-case';
import { NOTIFICATION_PREFERENCES_REPOSITORY } from '../../../domain/port/outbound/notification-preferences.repository';
import {
  NotificationChannel,
  NotificationPreferences,
} from '../../../domain/model/notification-preferences';
import { NotificationType } from '../../../domain/model/notification-type.enum';
import { JwtAuthGuard } from '../../auth/jwt-auth.guard';

describe('NotificationPreferencesController', () => {
  let controller: NotificationPreferencesController;

  const mockPrefsRepo = {
    findByUserId: jest.fn(),
    save: jest.fn().mockResolvedValue(undefined),
  };

  const fakeReq = { user: { sub: 'user-1' } };

  beforeEach(async () => {
    jest.clearAllMocks();
    const module = await Test.createTestingModule({
      controllers: [NotificationPreferencesController],
      providers: [
        GetPreferencesUseCase,
        UpdatePreferencesUseCase,
        {
          provide: NOTIFICATION_PREFERENCES_REPOSITORY,
          useValue: mockPrefsRepo,
        },
      ],
    })
      .overrideGuard(JwtAuthGuard)
      .useValue({ canActivate: () => true })
      .compile();

    controller = module.get(NotificationPreferencesController);
  });

  describe('GET /notifications/preferences', () => {
    it('returns serialised preferences for authenticated user', async () => {
      const prefs = NotificationPreferences.createDefault('user-1');
      mockPrefsRepo.findByUserId.mockResolvedValue(prefs);

      const result = await controller.get(fakeReq as any);

      expect(result.muted).toBe(false);
      expect(Array.isArray(result.typePreferences)).toBe(true);
      expect(typeof result.updatedAt).toBe('string');
    });

    it('creates default preferences on first access (repo returns null)', async () => {
      mockPrefsRepo.findByUserId.mockResolvedValue(null);

      const result = await controller.get(fakeReq as any);

      expect(result.muted).toBe(false);
      expect(mockPrefsRepo.save).toHaveBeenCalled();
    });

    it('maps typePreferences to type+channels shape', async () => {
      const prefs = NotificationPreferences.createDefault('user-1');
      mockPrefsRepo.findByUserId.mockResolvedValue(prefs);

      const result = await controller.get(fakeReq as any);

      for (const tp of result.typePreferences) {
        expect(tp).toHaveProperty('type');
        expect(tp).toHaveProperty('channels');
      }
    });
  });

  describe('PUT /notifications/preferences', () => {
    it('updates preferences and returns updated shape', async () => {
      const existingPrefs = NotificationPreferences.createDefault('user-1');
      mockPrefsRepo.findByUserId.mockResolvedValue(existingPrefs);

      const body = {
        typePreferences: [
          {
            type: NotificationType.ORDER_CREATED,
            channels: [NotificationChannel.IN_APP],
          },
        ],
        muted: false,
      };

      const result = await controller.update(fakeReq as any, body);

      expect(result.muted).toBe(false);
      expect(mockPrefsRepo.save).toHaveBeenCalled();
    });

    it('filters out invalid notification types from body', async () => {
      mockPrefsRepo.findByUserId.mockResolvedValue(null);

      const body = {
        typePreferences: [
          { type: 'INVALID_TYPE', channels: [NotificationChannel.EMAIL] },
          {
            type: NotificationType.ORDER_CREATED,
            channels: [NotificationChannel.EMAIL],
          },
        ],
        muted: false,
      };

      const result = await controller.update(fakeReq as any, body as any);

      const types = result.typePreferences.map((tp: any) => tp.type);
      expect(types).not.toContain('INVALID_TYPE');
    });

    it('filters out invalid channels from body', async () => {
      mockPrefsRepo.findByUserId.mockResolvedValue(null);

      const body = {
        typePreferences: [
          {
            type: NotificationType.ORDER_CREATED,
            channels: ['BAD_CHANNEL', NotificationChannel.EMAIL],
          },
        ],
        muted: false,
      };

      const result = await controller.update(fakeReq as any, body as any);

      const orderPref = result.typePreferences.find(
        (tp: any) => tp.type === NotificationType.ORDER_CREATED,
      );
      expect(orderPref?.channels).not.toContain('BAD_CHANNEL');
      expect(orderPref?.channels).toContain(NotificationChannel.EMAIL);
    });

    it('sets muted=true when requested', async () => {
      mockPrefsRepo.findByUserId.mockResolvedValue(null);

      const result = await controller.update(fakeReq as any, {
        typePreferences: [],
        muted: true,
      });

      expect(result.muted).toBe(true);
    });

    it('defaults muted to false when not provided in body', async () => {
      mockPrefsRepo.findByUserId.mockResolvedValue(null);

      const result = await controller.update(fakeReq as any, {
        typePreferences: [],
      } as any);

      expect(result.muted).toBe(false);
    });
  });
});
