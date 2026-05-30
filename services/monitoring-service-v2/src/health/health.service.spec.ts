import { Test } from '@nestjs/testing';
import { EventEmitter2 } from '@nestjs/event-emitter';
import { HealthService } from './health.service.js';
import { HealthChecker } from './health-checker.js';
import { DiscoveryService } from '../discovery/discovery.service.js';
import { MetricsService } from '../metrics/metrics.service.js';

describe('HealthService', () => {
  let service: HealthService;
  const mockDiscovery = { getServices: jest.fn() };
  const mockMetrics = {
    recordMetric: jest.fn(),
    getSummary: jest.fn().mockResolvedValue({ avgMs: 50, p95Ms: 100, uptimePct: 99.5 }),
    createAlert: jest.fn().mockResolvedValue({ id: 1, message: 'test' }),
    resolveAlerts: jest.fn(),
  };
  const mockChecker = { check: jest.fn() };
  const mockEmitter = { emit: jest.fn() };

  beforeEach(async () => {
    const module = await Test.createTestingModule({
      providers: [
        HealthService,
        { provide: DiscoveryService, useValue: mockDiscovery },
        { provide: MetricsService, useValue: mockMetrics },
        { provide: HealthChecker, useValue: mockChecker },
        { provide: EventEmitter2, useValue: mockEmitter },
      ],
    }).compile();

    service = module.get(HealthService);
    jest.clearAllMocks();
  });

  it('polls all discovered services and records metrics', async () => {
    mockDiscovery.getServices.mockReturnValue([
      { id: 'cart-service', name: 'Cart Service', url: 'http://cart-service:8084', healthPath: '/health', routes: [] },
    ]);
    mockChecker.check.mockResolvedValue({
      serviceId: 'cart-service',
      status: 'up',
      responseMs: 45,
      dependencies: {},
    });

    await service.pollAll();

    expect(mockMetrics.recordMetric).toHaveBeenCalledWith('cart-service', 'up', 45, {});
    expect(service.getAllStatuses()).toHaveLength(1);
    expect(service.getAllStatuses()[0].status).toBe('up');
  });

  it('emits alert after 3 consecutive failures', async () => {
    mockDiscovery.getServices.mockReturnValue([
      { id: 'x', name: 'X', url: 'http://x:1', healthPath: '/health', routes: [] },
    ]);
    mockChecker.check.mockResolvedValue({ serviceId: 'x', status: 'down', responseMs: 5000, dependencies: {} });

    await service.pollAll();
    await service.pollAll();
    await service.pollAll();

    expect(mockMetrics.createAlert).toHaveBeenCalledWith('x', 'down', 'X is unreachable');
  });

  it('skips polling when no services discovered', async () => {
    mockDiscovery.getServices.mockReturnValue([]);
    await service.pollAll();
    expect(mockChecker.check).not.toHaveBeenCalled();
  });
});
