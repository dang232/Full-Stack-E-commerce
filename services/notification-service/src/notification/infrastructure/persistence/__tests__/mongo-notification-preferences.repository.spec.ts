import { MongoNotificationPreferencesRepository } from '../mongo-notification-preferences.repository';
import { getModelToken } from '@nestjs/mongoose';
import { Test } from '@nestjs/testing';
import { NotificationPreferencesSchemaClass } from '../mongo-notification-preferences.schema';
import {
  NotificationChannel,
  NotificationPreferences,
} from '../../../domain/model/notification-preferences';
import { NotificationType } from '../../../domain/model/notification-type.enum';

describe('MongoNotificationPreferencesRepository', () => {
  const lean = jest.fn();
  const exec = jest.fn();
  const findOne = jest.fn().mockReturnValue({ lean: () => ({ exec }) });
  const updateOne = jest.fn().mockResolvedValue({});

  const mockModel = {
    findOne,
    updateOne,
  };

  let repo: MongoNotificationPreferencesRepository;

  beforeEach(async () => {
    jest.clearAllMocks();
    // Re-wire lean/exec chain
    findOne.mockReturnValue({ lean: () => ({ exec }) });

    const module = await Test.createTestingModule({
      providers: [
        MongoNotificationPreferencesRepository,
        {
          provide: getModelToken(NotificationPreferencesSchemaClass.name),
          useValue: mockModel,
        },
      ],
    }).compile();
    repo = module.get(MongoNotificationPreferencesRepository);
  });

  describe('findByUserId', () => {
    it('returns null when document not found', async () => {
      exec.mockResolvedValue(null);

      const result = await repo.findByUserId('user-1');

      expect(result).toBeNull();
      expect(findOne).toHaveBeenCalledWith({ userId: 'user-1' });
    });

    it('returns reconstituted NotificationPreferences when doc found', async () => {
      exec.mockResolvedValue({
        userId: 'user-1',
        typePreferences: [
          {
            type: NotificationType.ORDER_CREATED,
            channels: [NotificationChannel.EMAIL],
          },
        ],
        muted: false,
        updatedAt: new Date('2024-01-01'),
      });

      const result = await repo.findByUserId('user-1');

      expect(result).not.toBeNull();
      expect(result!.userId).toBe('user-1');
      expect(result!.muted).toBe(false);
      expect(result!.typePreferences[0].type).toBe(
        NotificationType.ORDER_CREATED,
      );
      expect(result!.typePreferences[0].channels).toEqual([
        NotificationChannel.EMAIL,
      ]);
    });

    it('handles doc with missing typePreferences (uses empty array)', async () => {
      exec.mockResolvedValue({
        userId: 'user-2',
        typePreferences: undefined,
        muted: true,
        updatedAt: new Date(),
      });

      const result = await repo.findByUserId('user-2');

      expect(result).not.toBeNull();
      expect(result!.typePreferences).toEqual([]);
    });

    it('defaults muted to false when missing from doc', async () => {
      exec.mockResolvedValue({
        userId: 'user-3',
        typePreferences: [],
        muted: undefined,
        updatedAt: new Date(),
      });

      const result = await repo.findByUserId('user-3');

      expect(result!.muted).toBe(false);
    });
  });

  describe('save', () => {
    it('calls updateOne with upsert and correct fields', async () => {
      const prefs = NotificationPreferences.createDefault('user-1');

      await repo.save(prefs);

      expect(updateOne).toHaveBeenCalledWith(
        { userId: 'user-1' },
        expect.objectContaining({
          $set: expect.objectContaining({
            muted: false,
          }),
          $setOnInsert: { userId: 'user-1' },
        }),
        { upsert: true },
      );
    });

    it('serialises typePreferences correctly', async () => {
      const prefs = NotificationPreferences.reconstitute({
        userId: 'user-2',
        typePreferences: [
          {
            type: NotificationType.ORDER_SHIPPED,
            channels: [NotificationChannel.SMS, NotificationChannel.PUSH],
          },
        ],
        muted: true,
        updatedAt: new Date(),
      });

      await repo.save(prefs);

      const call = updateOne.mock.calls[0];
      const setArg = call[1].$set;
      expect(setArg.typePreferences).toEqual([
        {
          type: NotificationType.ORDER_SHIPPED,
          channels: [NotificationChannel.SMS, NotificationChannel.PUSH],
        },
      ]);
      expect(setArg.muted).toBe(true);
    });
  });
});
