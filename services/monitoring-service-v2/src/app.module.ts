import { Module } from '@nestjs/common';
import { APP_GUARD } from '@nestjs/core';
import { ConfigModule, ConfigService } from '@nestjs/config';
import { TypeOrmModule } from '@nestjs/typeorm';
import { ScheduleModule } from '@nestjs/schedule';
import { ServeStaticModule } from '@nestjs/serve-static';
import { join } from 'path';
import { appConfig } from './config/app.config.js';
import { databaseConfig } from './config/database.config.js';
import { AuthModule } from './auth/auth.module.js';
import { JwtAuthGuard } from './auth/auth.guard.js';
import { RolesGuard } from './auth/roles.guard.js';
import { MetricsModule } from './metrics/metrics.module.js';
import { DiscoveryModule } from './discovery/discovery.module.js';

@Module({
  imports: [
    ConfigModule.forRoot({ isGlobal: true, load: [appConfig, databaseConfig] }),
    ScheduleModule.forRoot(),
    TypeOrmModule.forRootAsync({
      inject: [ConfigService],
      useFactory: (config: ConfigService) => ({
        type: 'postgres',
        host: config.get('database.host'),
        port: config.get('database.port'),
        database: config.get('database.database'),
        username: config.get('database.username'),
        password: config.get('database.password'),
        autoLoadEntities: true,
        synchronize: false,
      }),
    }),
    ServeStaticModule.forRoot({ rootPath: join(__dirname, '..', 'public') }),
    AuthModule,
    MetricsModule,
    DiscoveryModule,
  ],
  providers: [
    { provide: APP_GUARD, useClass: JwtAuthGuard },
    { provide: APP_GUARD, useClass: RolesGuard },
  ],
})
export class AppModule {}
