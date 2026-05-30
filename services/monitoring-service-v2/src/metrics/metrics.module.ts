import { Module } from '@nestjs/common';
import { TypeOrmModule } from '@nestjs/typeorm';
import { HealthMetric } from './entities/health-metric.entity.js';
import { Alert } from './entities/alert.entity.js';
import { MetricsService } from './metrics.service.js';

@Module({
  imports: [TypeOrmModule.forFeature([HealthMetric, Alert])],
  providers: [MetricsService],
  exports: [MetricsService],
})
export class MetricsModule {}
