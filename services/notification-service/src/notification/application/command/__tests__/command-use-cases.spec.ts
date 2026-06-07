import { Test } from '@nestjs/testing';
import { NotFoundException } from '@nestjs/common';
import { UpdatePreferencesUseCase } from '../update-preferences.use-case';
import { MarkNotificationReadUseCase } from '../mark-notification-read.use-case';
import { NOTIFICATION_REPOSITORY } from '../../../domain/port/outbound/notification.repository';
import { NOTIFICATION_PREFERENCES_REPOSITORY } from '../../../domain/port/outbound/notification-preferences.repository';
import {
  NotificationChannel,
  NotificationPreferences,
} from '../../../domain/model/notification-preferences';
import { NotificationType } from '../../../domain/model/notification-type.enum';
import { Notification } from '../../../domain/model/notification';

describe('UpdatePreferencesUseCase', () => {
  const mockPrefsRepo = {
    findByUserId: jest.fn(),
    save: jest.fn().mockResolvedValue(undefined),
  };

  let useCase: UpdatePreferencesUseCase;

  beforeEach(async () => {
    jest.clearAllMocks();
    const module = await Test.createTestingModule({
      providers: [
        UpdatePreferencesUseCase,
        {
          provide: NOTIFICATION_PREFERENCES_REPOSITORY,
          useValue: mockPrefsRepo,
        },
      ],
    }).compile();
    useCase = module.get(UpdatePreferencesUseCase);
  });

  it('updates existing preferences and saves', async () => {
    const existing = NotificationPreferences.createDefault('user-1');
    mockPrefsRepo.findByUserId.mockResolvedValue(existing);

    const result = await useCase.execute({
      userId: 'user-1',
      typePreferences: [
        {
          type: NotificationType.ORDER_CREATED,
          channels: [NotificationChannel.IN_APP],
        },
      ],
      muted: false,
    });

    expect(mockPrefsRepo.save).toHaveBeenCalledWith(result);
    expect(result.typePreferences).toHaveLength(1);
    expect(result.typePreferences[0].channels).toEqual([
      NotificationChannel.IN_APP,
    ]);
  });

  it('creates default preferences when none exist for user', async () => {
    mockPrefsRepo.findByUserId.mockResolvedValue(null);

    const result = await useCase.execute({
      userId: 'new-user',
      typePreferences: [],
      muted: true,
    });

    expect(result.userId).toBe('new-user');
    expect(result.muted).toBe(true);
    expect(mockPrefsRepo.save).toHaveBeenCalledWith(result);
  });

  it('filters out invalid channels from the command', async () => {
    mockPrefsRepo.findByUserId.mockResolvedValue(null);

    const result = await useCase.execute({
      userId: 'user-2',
      typePreferences: [
        {
          type: NotificationType.ORDER_CREATED,
          channels: [
            NotificationChannel.EMAIL,
            'INVALID_CHANNEL' as NotificationChannel,
          ],
        },
      ],
      muted: false,
    });

    const orderPref = result.typePreferences.find(
      (tp) => tp.type === NotificationType.ORDER_CREATED,
    );
    expect(orderPref?.channels).not.toContain('INVALID_CHANNEL');
    expect(orderPref?.channels).toContain(NotificationChannel.EMAIL);
  });

  it('sets muted flag on existing preferences', async () => {
    const existing = NotificationPreferences.createDefault('user-3');
    mockPrefsRepo.findByUserId.mockResolvedValue(existing);

    const result = await useCase.execute({
      userId: 'user-3',
      typePreferences: [],
      muted: true,
    });

    expect(result.muted).toBe(true);
  });
});

describe('MarkNotificationReadUseCase', () => {
  const mockRepo = {
    findByIdAndUserId: jest.fn(),
    save: jest.fn().mockResolvedValue(undefined),
  };

  let useCase: MarkNotificationReadUseCase;

  beforeEach(async () => {
    jest.clearAllMocks();
    const module = await Test.createTestingModule({
      providers: [
        MarkNotificationReadUseCase,
        { provide: NOTIFICATION_REPOSITORY, useValue: mockRepo },
      ],
    }).compile();
    useCase = module.get(MarkNotificationReadUseCase);
  });

  it('marks notification as read and saves', async () => {
    const notification = Notification.create({
      userId: 'user-1',
      type: NotificationType.ORDER_CREATED,
      title: 'T',
      body: 'B',
    });
    mockRepo.findByIdAndUserId.mockResolvedValue(notification);

    const result = await useCase.execute(notification.id, 'user-1');

    expect(result.read).toBe(true);
    expect(result.readAt).toBeInstanceOf(Date);
    expect(mockRepo.save).toHaveBeenCalledWith(result);
  });

  it('throws NotFoundException when notification does not exist', async () => {
    mockRepo.findByIdAndUserId.mockResolvedValue(null);

    await expect(useCase.execute('non-existent-id', 'user-1')).rejects.toThrow(
      NotFoundException,
    );
  });

  it('throws NotFoundException when notification belongs to different user', async () => {
    // findByIdAndUserId returns null when user doesn't match
    mockRepo.findByIdAndUserId.mockResolvedValue(null);

    await expect(useCase.execute('notif-id', 'wrong-user')).rejects.toThrow(
      NotFoundException,
    );
  });

  it('calls findByIdAndUserId with correct args', async () => {
    const notification = Notification.create({
      userId: 'user-1',
      type: NotificationType.PAYMENT_COMPLETED,
      title: 'T',
      body: 'B',
    });
    mockRepo.findByIdAndUserId.mockResolvedValue(notification);

    await useCase.execute('notif-abc', 'user-1');

    expect(mockRepo.findByIdAndUserId).toHaveBeenCalledWith(
      'notif-abc',
      'user-1',
    );
  });
});
