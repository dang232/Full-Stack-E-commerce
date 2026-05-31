import { Injectable } from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository, MoreThan, IsNull } from 'typeorm';
import { HealthMetric } from './entities/health-metric.entity.js';
import { Alert } from './entities/alert.entity.js';

export interface MetricsSummary {
  avgMs: number;
  p95Ms: number;
  uptimePct: number;
}

@Injectable()
export class MetricsService {
  constructor(
    @InjectRepository(HealthMetric)
    private readonly metricRepo: Repository<HealthMetric>,
    @InjectRepository(Alert)
    private readonly alertRepo: Repository<Alert>,
  ) {}

  async recordMetric(
    serviceId: string,
    status: string,
    responseMs: number | null,
    details: Record<string, unknown> | null,
  ): Promise<void> {
    await this.metricRepo.insert({
      time: new Date(),
      serviceId,
      status,
      responseMs,
      details,
    } as any);
  }

  async getHistory(
    serviceId: string,
    period: '1h' | '24h' | '7d',
  ): Promise<HealthMetric[]> {
    const since = new Date();
    const hours = period === '1h' ? 1 : period === '24h' ? 24 : 168;
    since.setHours(since.getHours() - hours);

    return this.metricRepo.find({
      where: { serviceId, time: MoreThan(since) },
      order: { time: 'ASC' },
    });
  }

  async getSummary(serviceId: string, period: '1h' | '24h' | '7d'): Promise<MetricsSummary> {
    const since = new Date();
    const hours = period === '1h' ? 1 : period === '24h' ? 24 : 168;
    since.setHours(since.getHours() - hours);

    const result = await this.metricRepo
      .createQueryBuilder('m')
      .select('AVG(m.response_ms)::INTEGER', 'avgMs')
      .addSelect(
        "percentile_cont(0.95) WITHIN GROUP (ORDER BY m.response_ms)::INTEGER",
        'p95Ms',
      )
      .addSelect(
        "count(*) FILTER (WHERE m.status = 'up') * 100.0 / GREATEST(count(*), 1)",
        'uptimePct',
      )
      .where('m.service_id = :serviceId', { serviceId })
      .andWhere('m.time > :since', { since })
      .getRawOne();

    return {
      avgMs: result?.avgMs ?? 0,
      p95Ms: result?.p95Ms ?? 0,
      uptimePct: parseFloat(result?.uptimePct ?? '0'),
    };
  }

  async createAlert(serviceId: string, type: string, message: string): Promise<Alert> {
    const alert = this.alertRepo.create({ serviceId, type, message });
    return this.alertRepo.save(alert);
  }

  async resolveAlerts(serviceId: string): Promise<void> {
    await this.alertRepo.update(
      { serviceId, resolvedAt: IsNull() },
      { resolvedAt: new Date() },
    );
  }

  async getActiveAlerts(): Promise<Alert[]> {
    return this.alertRepo.find({
      where: { resolvedAt: IsNull() },
      order: { createdAt: 'DESC' },
    });
  }

  async getAlertHistory(days: number = 7): Promise<Alert[]> {
    const since = new Date();
    since.setDate(since.getDate() - days);
    return this.alertRepo.find({
      where: { createdAt: MoreThan(since) },
      order: { createdAt: 'DESC' },
    });
  }
}
