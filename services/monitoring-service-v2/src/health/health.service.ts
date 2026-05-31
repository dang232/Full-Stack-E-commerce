import { Injectable, Logger } from '@nestjs/common';
import { Interval } from '@nestjs/schedule';
import { DiscoveryService } from '../discovery/discovery.service.js';
import { MetricsService } from '../metrics/metrics.service.js';
import { HealthChecker, HealthCheckResult } from './health-checker.js';
import { EventEmitter2 } from '@nestjs/event-emitter';

export interface ServiceStatus {
  serviceId: string;
  name: string;
  status: 'up' | 'down' | 'degraded';
  responseMs: number;
  uptimePct: number;
  dependencies: Record<string, { status: string }>;
  lastChecked: Date;
}

@Injectable()
export class HealthService {
  private readonly logger = new Logger(HealthService.name);
  private readonly statuses = new Map<string, ServiceStatus>();
  private readonly failureCounts = new Map<string, number>();
  private readonly recoveryCounts = new Map<string, number>();
  private static readonly FAILURE_THRESHOLD = 3;
  private static readonly RECOVERY_THRESHOLD = 3;

  constructor(
    private readonly discoveryService: DiscoveryService,
    private readonly metricsService: MetricsService,
    private readonly healthChecker: HealthChecker,
    private readonly eventEmitter: EventEmitter2,
  ) {}

  @Interval(10000)
  async pollAll(): Promise<void> {
    const services = this.discoveryService.getServices();
    if (services.length === 0) return;

    const results = await Promise.allSettled(
      services.map((svc) => this.healthChecker.check(svc.id, svc.url, svc.healthPath)),
    );

    for (let i = 0; i < services.length; i++) {
      const result = results[i];
      if (result.status === 'fulfilled') {
        await this.processResult(services[i].name, result.value);
      }
    }
  }

  private async processResult(name: string, result: HealthCheckResult): Promise<void> {
    const { serviceId, status, responseMs, dependencies } = result;
    const previous = this.statuses.get(serviceId);
    const previousStatus = previous?.status ?? 'up';

    if (status === 'down') {
      this.failureCounts.set(serviceId, (this.failureCounts.get(serviceId) ?? 0) + 1);
      this.recoveryCounts.set(serviceId, 0);
    } else {
      this.recoveryCounts.set(serviceId, (this.recoveryCounts.get(serviceId) ?? 0) + 1);
      this.failureCounts.set(serviceId, 0);
    }

    let effectiveStatus = status;
    if (status === 'down' && (this.failureCounts.get(serviceId) ?? 0) < HealthService.FAILURE_THRESHOLD) {
      effectiveStatus = previousStatus;
    }
    if (previousStatus === 'down' && status === 'up' && (this.recoveryCounts.get(serviceId) ?? 0) < HealthService.RECOVERY_THRESHOLD) {
      effectiveStatus = 'down';
    }

    await this.metricsService.recordMetric(serviceId, effectiveStatus, responseMs, dependencies as unknown as Record<string, unknown>);

    const summary = await this.metricsService.getSummary(serviceId, '24h');

    this.statuses.set(serviceId, {
      serviceId,
      name,
      status: effectiveStatus,
      responseMs,
      uptimePct: summary.uptimePct,
      dependencies,
      lastChecked: new Date(),
    });

    if (previousStatus !== effectiveStatus) {
      if (effectiveStatus === 'down') {
        const alert = await this.metricsService.createAlert(serviceId, 'down', `${name} is unreachable`);
        this.eventEmitter.emit('service.alert', { serviceId, type: 'down', message: alert.message, timestamp: new Date() });
      } else if (effectiveStatus === 'degraded') {
        await this.metricsService.createAlert(serviceId, 'degraded', `${name} is degraded`);
        this.eventEmitter.emit('service.alert', { serviceId, type: 'degraded', message: `${name} is degraded`, timestamp: new Date() });
      } else if (previousStatus === 'down' && effectiveStatus === 'up') {
        await this.metricsService.resolveAlerts(serviceId);
        this.eventEmitter.emit('service.alert', { serviceId, type: 'recovered', message: `${name} recovered`, timestamp: new Date() });
      }
    }

    this.eventEmitter.emit('service.status', { serviceId, status: effectiveStatus, responseMs, timestamp: new Date() });
  }

  getAllStatuses(): ServiceStatus[] {
    return Array.from(this.statuses.values());
  }

  getStatus(serviceId: string): ServiceStatus | undefined {
    return this.statuses.get(serviceId);
  }
}
