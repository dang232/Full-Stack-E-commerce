import { Injectable, Logger, OnModuleInit } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { Interval } from '@nestjs/schedule';
import axios from 'axios';
import { GatewayClient } from './gateway-client.js';
import { OpenApiFetcher } from './openapi-fetcher.js';
import { DiscoveredService, DiscoveredEndpoint } from './discovery.types.js';

@Injectable()
export class DiscoveryService implements OnModuleInit {
  private readonly logger = new Logger(DiscoveryService.name);
  private services: DiscoveredService[] = [];
  private endpoints: DiscoveredEndpoint[] = [];

  constructor(
    private readonly gatewayClient: GatewayClient,
    private readonly openApiFetcher: OpenApiFetcher,
    private readonly config: ConfigService,
  ) {}

  async onModuleInit(): Promise<void> {
    await this.refresh();
  }

  @Interval(300000)
  async refresh(): Promise<void> {
    this.logger.log('Refreshing service discovery...');
    const routes = await this.gatewayClient.fetchRoutes();

    if (routes.length > 0) {
      this.services = this.gatewayClient.parseServices(routes);
      await this.detectHealthPaths();
      await this.refreshEndpoints();
      this.logger.log(`Discovered ${this.services.length} services, ${this.endpoints.length} endpoints`);
    } else if (this.services.length === 0) {
      this.logger.warn('No routes from gateway and no cached services — will retry');
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
