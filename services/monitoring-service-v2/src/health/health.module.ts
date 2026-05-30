import { Module } from '@nestjs/common';
import { HealthChecker } from './health-checker.js';
import { HealthService } from './health.service.js';
import { HealthController } from './health.controller.js';
import { DiscoveryModule } from '../discovery/discovery.module.js';
import { MetricsModule } from '../metrics/metrics.module.js';

@Module({
  imports: [DiscoveryModule, MetricsModule],
  controllers: [HealthController],
  providers: [HealthChecker, HealthService],
  exports: [HealthService],
})
export class HealthModule {}
