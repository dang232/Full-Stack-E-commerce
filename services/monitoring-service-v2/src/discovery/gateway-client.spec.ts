import { GatewayClient } from './gateway-client.js';
import { ConfigService } from '@nestjs/config';
import { GatewayRoute } from './discovery.types.js';

describe('GatewayClient', () => {
  let client: GatewayClient;

  beforeEach(() => {
    const config = { get: () => 'http://localhost:8080/actuator/gateway/routes' } as unknown as ConfigService;
    client = new GatewayClient(config);
  });

  it('parses routes into deduplicated services', () => {
    const routes: GatewayRoute[] = [
      {
        route_id: 'products',
        predicates: [{ name: 'Path', args: { _genkey_0: '/products/**' } }],
        filters: [],
        uri: 'http://product-service:8082',
        order: 0,
      },
      {
        route_id: 'categories',
        predicates: [{ name: 'Path', args: { _genkey_0: '/categories/**' } }],
        filters: [],
        uri: 'http://product-service:8082',
        order: 1,
      },
      {
        route_id: 'cart',
        predicates: [{ name: 'Path', args: { _genkey_0: '/cart/**' } }],
        filters: [],
        uri: 'http://cart-service:8084',
        order: 2,
      },
    ];

    const services = client.parseServices(routes);

    expect(services).toHaveLength(2);
    const productSvc = services.find((s) => s.id === 'product-service');
    expect(productSvc).toBeDefined();
    expect(productSvc!.routes).toEqual(['/products/**', '/categories/**']);
    expect(productSvc!.name).toBe('Product Service');

    const cartSvc = services.find((s) => s.id === 'cart-service');
    expect(cartSvc).toBeDefined();
    expect(cartSvc!.routes).toEqual(['/cart/**']);
  });

  it('returns empty array for empty routes', () => {
    expect(client.parseServices([])).toEqual([]);
  });
});
