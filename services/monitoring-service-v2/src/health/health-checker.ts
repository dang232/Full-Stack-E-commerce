import { Injectable, Logger } from '@nestjs/common';
import axios from 'axios';

export interface HealthCheckResult {
  serviceId: string;
  status: 'up' | 'down' | 'degraded';
  responseMs: number;
  dependencies: Record<string, { status: string }>;
}

@Injectable()
export class HealthChecker {
  private readonly logger = new Logger(HealthChecker.name);
  private static readonly DEGRADED_THRESHOLD_MS = 2000;

  async check(serviceId: string, url: string, healthPath: string): Promise<HealthCheckResult> {
    const start = Date.now();
    try {
      const response = await axios.get(`${url}${healthPath}`, { timeout: 5000 });
      const responseMs = Date.now() - start;
      const data = response.data as Record<string, unknown>;

      const dependencies = this.parseDependencies(data);
      const anyDepDown = Object.values(dependencies).some((d) => d.status !== 'UP');
      const slow = responseMs > HealthChecker.DEGRADED_THRESHOLD_MS;

      let status: 'up' | 'down' | 'degraded' = 'up';
      if (anyDepDown || slow) status = 'degraded';

      return { serviceId, status, responseMs, dependencies };
    } catch {
      return {
        serviceId,
        status: 'down',
        responseMs: Date.now() - start,
        dependencies: {},
      };
    }
  }

  private parseDependencies(data: Record<string, unknown>): Record<string, { status: string }> {
    const deps: Record<string, { status: string }> = {};
    const components = data.components as Record<string, { status?: string }> | undefined;
    if (!components) return deps;

    for (const [name, detail] of Object.entries(components)) {
      deps[name] = { status: detail.status ?? 'UNKNOWN' };
    }
    return deps;
  }
}
