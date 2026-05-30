import { Injectable, Logger } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import axios from 'axios';
import { GatewayRoute, DiscoveredService } from './discovery.types.js';

@Injectable()
export class GatewayClient {
  private readonly logger = new Logger(GatewayClient.name);
  private readonly actuatorUrl: string;

  constructor(private readonly config: ConfigService) {
    this.actuatorUrl = this.config.get<string>(
      'app.gatewayActuatorUrl',
      'http://localhost:8080/actuator/gateway/routes',
    );
  }

  async fetchRoutes(): Promise<GatewayRoute[]> {
    try {
      const response = await axios.get<GatewayRoute[]>(this.actuatorUrl, {
        timeout: 5000,
      });
      return response.data;
    } catch (error) {
      this.logger.warn(`Failed to fetch gateway routes: ${(error as Error).message}`);
      return [];
    }
  }

  parseServices(routes: GatewayRoute[]): DiscoveredService[] {
    const serviceMap = new Map<string, DiscoveredService>();

    for (const route of routes) {
      const url = route.uri;
      const hostname = new URL(url).hostname;
      const id = hostname;
      const name = hostname
        .split('-')
        .map((w) => w.charAt(0).toUpperCase() + w.slice(1))
        .join(' ');

      const pathPredicates = route.predicates
        .filter((p) => p.name === 'Path')
        .flatMap((p) => Object.values(p.args));

      if (serviceMap.has(id)) {
        serviceMap.get(id)!.routes.push(...pathPredicates);
      } else {
        serviceMap.set(id, {
          id,
          name,
          url,
          healthPath: '/actuator/health',
          routes: [...pathPredicates],
        });
      }
    }

    return Array.from(serviceMap.values());
  }
}
