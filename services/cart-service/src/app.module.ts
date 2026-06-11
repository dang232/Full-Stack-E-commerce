import { Module } from '@nestjs/common';
import { ConfigModule, ConfigService } from '@nestjs/config';
import { MikroOrmModule } from '@mikro-orm/nestjs';
import { PostgreSqlDriver } from '@mikro-orm/postgresql';
import { AppController } from './app.controller';
import { CartModule } from './cart/cart.module';
import { CartMikroOrmEntity } from './cart/infrastructure/cart.mikro-orm-entity.js';

@Module({
  imports: [
    ConfigModule.forRoot({ isGlobal: true }),
    MikroOrmModule.forRootAsync({
      inject: [ConfigService],
      useFactory: (configService: ConfigService) => {
        const databaseUrl = configService.get<string>('DATABASE_URL');
        if (!databaseUrl) {
          throw new Error(
            'DATABASE_URL environment variable is required but not set. ' +
              'Example: postgresql://vnshop:vnshop@postgres-cart:5432/vnshop_cart',
          );
        }
        return {
          driver: PostgreSqlDriver,
          entities: [CartMikroOrmEntity],
          clientUrl: databaseUrl,
          pool: { min: 2, max: 10 },
          migrations: {
            path: './dist/db/migrations',
            disableForeignKeys: false,
          },
          debug: false,
        };
      },
    }),
    CartModule,
  ],
  controllers: [AppController],
})
export class AppModule {}
