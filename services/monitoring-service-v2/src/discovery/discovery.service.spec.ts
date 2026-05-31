import { Test } from '@nestjs/testing';
import { ConfigService } from '@nestjs/config';
import { DiscoveryService } from './discovery.service.js';
import { GatewayClient } from './gateway-client.js';
import { OpenApiFetcher } from './openapi-fetcher.js';

jest.mock('axios');

describe('DiscoveryService', () => {
  let service: DiscoveryService;
  const mockGatewayClient = {
    fetchRoutes: jest.fn(),
    parseServices: jest.fn(),
  };
  const mockOpenApiFetcher = { fetchSchema: jest.fn() };
  const mockConfig = { get: jest.fn().mockReturnValue(300000) };

  beforeEach(async () => {
    const module = await Test.createTestingModule({
      providers: [
        DiscoveryService,
        { provide: GatewayClient, useValue: mockGatewayClient },
        { provide: OpenApiFetcher, useValue: mockOpenApiFetcher },
        { provide: ConfigService, useValue: mockConfig },
      ],
    }).compile();

    service = module.get(DiscoveryService);
    jest.clearAllMocks();
  });

  it('populates services on refresh', async () => {
    const routes = [{ route_id: 'cart', uri: 'http://cart-service:8084', predicates: [], filters: [], order: 0 }];
    const services = [{ id: 'cart-service', name: 'Cart Service', url: 'http://cart-service:8084', healthPath: '/health', routes: ['/cart/**'] }];

    mockGatewayClient.fetchRoutes.mockResolvedValue(routes);
    mockGatewayClient.parseServices.mockReturnValue(services);
    mockOpenApiFetcher.fetchSchema.mockResolvedValue([]);

    await service.refresh();

    expect(service.getServices()).toHaveLength(1);
    expect(service.getServices()[0].id).toBe('cart-service');
  });

  it('keeps cached services when gateway is unreachable', async () => {
    mockGatewayClient.fetchRoutes.mockResolvedValue([{ route_id: 'x', uri: 'http://x:1', predicates: [], filters: [], order: 0 }]);
    mockGatewayClient.parseServices.mockReturnValue([{ id: 'x', name: 'X', url: 'http://x:1', healthPath: '/health', routes: [] }]);
    mockOpenApiFetcher.fetchSchema.mockResolvedValue([]);
    await service.refresh();

    mockGatewayClient.fetchRoutes.mockResolvedValue([]);
    await service.refresh();

    expect(service.getServices()).toHaveLength(1);
  });
});
