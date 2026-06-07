import { Test } from '@nestjs/testing';
import { GetPreferencesUseCase } from '../get-preferences.use-case';
import { NOTIFICATION_PREFERENCES_REPOSITORY } from '../../../domain/port/outbound/notification-preferences.repository';
import { NotificationPreferences } from '../../../domain/model/notification-preferences';

describe('GetPreferencesUseCase', () => {
  const mockPrefsRepo = {
    findByUserId: jest.fn(),
    save: jest.fn().mockResolvedValue(undefined),
  };

  let useCase: GetPreferencesUseCase;

  beforeEach(async () => {
    jest.clearAllMocks();
    const module = await Test.createTestingModule({
      providers: [
        GetPreferencesUseCase,
        {
          provide: NOTIFICATION_PREFERENCES_REPOSITORY,
          useValue: mockPrefsRepo,
        },
      ],
    }).compile();
    useCase = module.get(GetPreferencesUseCase);
  });

  it('returns existing preferences when found', async () => {
    const existing = NotificationPreferences.createDefault('user-1');
    mockPrefsRepo.findByUserId.mockResolvedValue(existing);

    const result = await useCase.execute('user-1');

    expect(result).toBe(existing);
    expect(mockPrefsRepo.save).not.toHaveBeenCalled();
  });

  it('creates and saves default preferences when none exist', async () => {
    mockPrefsRepo.findByUserId.mockResolvedValue(null);

    const result = await useCase.execute('new-user');

    expect(result.userId).toBe('new-user');
    expect(result.muted).toBe(false);
    expect(mockPrefsRepo.save).toHaveBeenCalledWith(result);
  });

  it('returns preferences with all notification types enabled by default', async () => {
    mockPrefsRepo.findByUserId.mockResolvedValue(null);

    const result = await useCase.execute('fresh-user');

    expect(result.typePreferences.length).toBeGreaterThan(0);
  });

  it('calls findByUserId with the correct userId', async () => {
    mockPrefsRepo.findByUserId.mockResolvedValue(null);

    await useCase.execute('user-xyz');

    expect(mockPrefsRepo.findByUserId).toHaveBeenCalledWith('user-xyz');
  });
});
