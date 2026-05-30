import { Test } from '@nestjs/testing';
import { getRepositoryToken } from '@nestjs/typeorm';
import { MetricsService } from './metrics.service.js';
import { HealthMetric } from './entities/health-metric.entity.js';
import { Alert } from './entities/alert.entity.js';

describe('MetricsService', () => {
  let service: MetricsService;
  const mockMetricRepo = {
    insert: jest.fn(),
    find: jest.fn(),
    createQueryBuilder: jest.fn(),
  };
  const mockAlertRepo = {
    create: jest.fn(),
    save: jest.fn(),
    update: jest.fn(),
    find: jest.fn(),
  };

  beforeEach(async () => {
    const module = await Test.createTestingModule({
      providers: [
        MetricsService,
        { provide: getRepositoryToken(HealthMetric), useValue: mockMetricRepo },
        { provide: getRepositoryToken(Alert), useValue: mockAlertRepo },
      ],
    }).compile();

    service = module.get(MetricsService);
    jest.clearAllMocks();
  });

  it('records a metric', async () => {
    await service.recordMetric('product-service', 'up', 42, null);
    expect(mockMetricRepo.insert).toHaveBeenCalledWith(
      expect.objectContaining({ serviceId: 'product-service', status: 'up', responseMs: 42 }),
    );
  });

  it('creates an alert', async () => {
    const alert = { id: 1, serviceId: 'cart-service', type: 'down', message: 'unreachable' };
    mockAlertRepo.create.mockReturnValue(alert);
    mockAlertRepo.save.mockResolvedValue(alert);

    const result = await service.createAlert('cart-service', 'down', 'unreachable');
    expect(result).toEqual(alert);
  });

  it('resolves active alerts for a service', async () => {
    await service.resolveAlerts('cart-service');
    expect(mockAlertRepo.update).toHaveBeenCalledWith(
      { serviceId: 'cart-service', resolvedAt: expect.anything() },
      { resolvedAt: expect.any(Date) },
    );
  });
});
