import { Module } from '@nestjs/common';
import { ConfigurationModule } from './configuration/configuration.module.js';

@Module({
  imports: [ConfigurationModule],
})
export class AppModule {}
