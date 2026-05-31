import { Module } from '@nestjs/common';
import { GatewayClient } from './gateway-client.js';
import { OpenApiFetcher } from './openapi-fetcher.js';
import { DiscoveryService } from './discovery.service.js';
import { DiscoveryController } from './discovery.controller.js';

@Module({
  controllers: [DiscoveryController],
  providers: [GatewayClient, OpenApiFetcher, DiscoveryService],
  exports: [DiscoveryService],
})
export class DiscoveryModule {}
