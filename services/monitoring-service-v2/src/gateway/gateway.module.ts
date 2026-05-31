import { Module } from '@nestjs/common';
import { MonitoringGateway } from './monitoring.gateway.js';

@Module({
  providers: [MonitoringGateway],
  exports: [MonitoringGateway],
})
export class MonitoringGatewayModule {}
