import { Module } from '@nestjs/common';
import { DiscoveryModule } from '../discovery/discovery.module.js';
import { PlaygroundService } from './playground.service.js';
import { PlaygroundController } from './playground.controller.js';

@Module({
  imports: [DiscoveryModule],
  providers: [PlaygroundService],
  controllers: [PlaygroundController],
})
export class PlaygroundModule {}
