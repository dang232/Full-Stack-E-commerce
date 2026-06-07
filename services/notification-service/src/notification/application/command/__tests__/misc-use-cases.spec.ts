import { Test } from '@nestjs/testing';
import { RetryFailedDeliveriesUseCase } from '../retry-failed-deliveries.use-case';
import { MarkAllReadUseCase } from '../mark-all-read.use-case';
import { NOTIFICATION_REPOSITORY } from '../../../domain/port/outbound/notification.repository';
import { REALTIME_CHANNEL_PORT } from '../../../domain/port/outbound/realtime-channel.port';
import { CONNECTION_REGISTRY_PORT } from '../../../domain/port/outbound/connection-registry.port';

describe('RetryFailedDeliveriesUseCase', () => {
  let useCase: RetryFailedDeliveriesUseCase;

  const mockRepo = { save: jest.fn(), findFailed: jest.fn() };
  const mockChannel = { sendToUser: jest.fn() };
  const mockRegistry = { isOnline: jest.fn() };

  beforeEach(async () => {
    jest.clearAllMocks();
    const module = await Test.createTestingModule({
      providers: [
        RetryFailedDeliveriesUseCase,
        { provide: NOTIFICATION_REPOSITORY, useValue: mockRepo },
        { provide: REALTIME_CHANNEL_PORT, useValue: mockChannel },
        { provide: CONNECTION_REGISTRY_PORT, useValue: mockRegistry },
      ],
    }).compile();
    useCase = module.get(RetryFailedDeliveriesUseCase);
  });

  it('execute returns retried=0 and movedToDlq=0 (no-op placeholder)', () => {
    const result = useCase.execute();
    expect(result).toEqual({ retried: 0, movedToDlq: 0 });
  });

  it('execute does not throw', () => {
    expect(() => useCase.execute()).not.toThrow();
  });
});

describe('MarkAllReadUseCase', () => {
  let useCase: MarkAllReadUseCase;

  const mockRepo = {
    markAllReadForUser: jest.fn().mockResolvedValue(3),
  };

  beforeEach(async () => {
    jest.clearAllMocks();
    const module = await Test.createTestingModule({
      providers: [
        MarkAllReadUseCase,
        { provide: NOTIFICATION_REPOSITORY, useValue: mockRepo },
      ],
    }).compile();
    useCase = module.get(MarkAllReadUseCase);
  });

  it('delegates to repo.markAllReadForUser and returns count', async () => {
    const result = await useCase.execute('user-1');
    expect(result).toBe(3);
    expect(mockRepo.markAllReadForUser).toHaveBeenCalledWith('user-1');
  });

  it('returns 0 when no notifications were updated', async () => {
    mockRepo.markAllReadForUser.mockResolvedValue(0);
    const result = await useCase.execute('user-no-notifs');
    expect(result).toBe(0);
  });
});
