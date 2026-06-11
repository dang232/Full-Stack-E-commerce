import { Injectable, Logger, OnModuleInit } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { Interval } from '@nestjs/schedule';
import axios from 'axios';
import { GatewayClient } from './gateway-client.js';
import { OpenApiFetcher } from './openapi-fetcher.js';
import { DiscoveredService, DiscoveredEndpoint } from './discovery.types.js';

/**
 * Static service registry used as fallback when the gateway actuator is
 * unreachable (e.g. requires auth). Maps Docker service hostnames to ports.
 */
const STATIC_SERVICES: DiscoveredService[] = [
  { id: 'user-service', name: 'User Service', url: 'http://user-service:8081', healthPath: '/actuator/health', routes: ['/api/users/**'] },
  { id: 'product-service', name: 'Product Service', url: 'http://product-service:8082', healthPath: '/actuator/health', routes: ['/api/products/**'] },
  { id: 'inventory-service', name: 'Inventory Service', url: 'http://inventory-service:8083', healthPath: '/actuator/health', routes: ['/api/inventory/**'] },
  { id: 'cart-service', name: 'Cart Service', url: 'http://cart-service:8084', healthPath: '/actuator/health', routes: ['/api/cart/**'] },
  { id: 'search-service', name: 'Search Service', url: 'http://search-service:8086', healthPath: '/actuator/health', routes: ['/api/search/**'] },
  { id: 'notification-service', name: 'Notification Service', url: 'http://notification-service:8087', healthPath: '/health', routes: ['/api/notifications/**'] },
  { id: 'coupon-service', name: 'Coupon Service', url: 'http://coupon-service:8088', healthPath: '/actuator/health', routes: ['/api/coupons/**'] },
  { id: 'seller-finance-service', name: 'Seller Finance Service', url: 'http://seller-finance-service:8090', healthPath: '/actuator/health', routes: ['/api/seller-finance/**'] },
  { id: 'order-service', name: 'Order Service', url: 'http://order-service:8091', healthPath: '/actuator/health', routes: ['/api/orders/**'] },
  { id: 'payment-service', name: 'Payment Service', url: 'http://payment-service:8092', healthPath: '/actuator/health', routes: ['/api/payments/**'] },
  { id: 'shipping-service', name: 'Shipping Service', url: 'http://shipping-service:8093', healthPath: '/actuator/health', routes: ['/api/shipping/**'] },
  { id: 'recommendations-service', name: 'Recommendations Service', url: 'http://recommendations-service:8094', healthPath: '/actuator/health', routes: ['/api/recommendations/**'] },
  { id: 'messaging-service', name: 'Messaging Service', url: 'http://messaging-service:8095', healthPath: '/health', routes: ['/api/messaging/**'] },
  { id: 'configuration-service', name: 'Configuration Service', url: 'http://configuration-service:8097', healthPath: '/actuator/health', routes: ['/api/config/**'] },
  { id: 'invoice-service', name: 'Invoice Service', url: 'http://invoice-service:8098', healthPath: '/actuator/health', routes: ['/api/invoices/**'] },
];

@Injectable()
export class DiscoveryService implements OnModuleInit {
  private readonly logger = new Logger(DiscoveryService.name);
  private services: DiscoveredService[] = [];
  private endpoints: DiscoveredEndpoint[] = [];
  private readonly useStaticRegistry: boolean;

  constructor(
    private readonly gatewayClient: GatewayClient,
    private readonly openApiFetcher: OpenApiFetcher,
    private readonly config: ConfigService,
  ) {
    this.useStaticRegistry =
      this.config.get<string>('app.discoveryMode', 'auto') === 'static';
  }

  async onModuleInit(): Promise<void> {
    await this.refresh();
  }

  @Interval(300000)
  async refresh(): Promise<void> {
    this.logger.log('Refreshing service discovery...');

    if (this.useStaticRegistry) {
      this.services = [...STATIC_SERVICES];
      this.logger.log(`Using static registry: ${this.services.length} services`);
      await this.refreshEndpoints();
      return;
    }

    const routes = await this.gatewayClient.fetchRoutes();

    if (routes.length > 0) {
      this.services = this.gatewayClient.parseServices(routes);
      await this.detectHealthPaths();
      await this.refreshEndpoints();
      this.logger.log(`Discovered ${this.services.length} services, ${this.endpoints.length} endpoints`);
    } else if (this.services.length === 0) {
      this.logger.warn('Gateway discovery failed — falling back to static registry');
      this.services = [...STATIC_SERVICES];
      await this.refreshEndpoints();
      this.logger.log(`Static fallback: ${this.services.length} services`);
    }
  }

  private async detectHealthPaths(): Promise<void> {
    for (const svc of this.services) {
      try {
        await axios.get(`${svc.url}/actuator/health`, { timeout: 2000 });
        svc.healthPath = '/actuator/health';
      } catch {
        svc.healthPath = '/health';
      }
    }
  }

  private async refreshEndpoints(): Promise<void> {
    const allEndpoints: DiscoveredEndpoint[] = [];
    for (const svc of this.services) {
      const eps = await this.openApiFetcher.fetchSchema(svc.url, svc.id);
      allEndpoints.push(...eps);
    }
    this.endpoints = allEndpoints;
  }

  getServices(): DiscoveredService[] {
    return this.services;
  }

  getEndpoints(): DiscoveredEndpoint[] {
    return this.endpoints;
  }

  getEndpointById(id: string): DiscoveredEndpoint | undefined {
    return this.endpoints.find((e) => e.id === id);
  }

  getServiceById(id: string): DiscoveredService | undefined {
    return this.services.find((s) => s.id === id);
  }
}
