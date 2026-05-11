import { Module } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import Redis from 'ioredis';
import { AddToCartUseCase } from './application/add-to-cart.use-case';
import { ClearCartUseCase } from './application/clear-cart.use-case';
import {
  ProductClientPort,
  PRODUCT_CLIENT,
} from './application/product-client.port';
import { RemoveCartItemUseCase } from './application/remove-cart-item.use-case';
import { UpdateCartItemUseCase } from './application/update-cart-item.use-case';
import { ViewCartUseCase } from './application/view-cart.use-case';
import { CART_REPOSITORY, CartRepository } from './domain/cart.repository';
import { CartController } from './infrastructure/cart.controller';
import { CartRedisRepository } from './infrastructure/cart.redis-repository';
import { ProductHttpClientAdapter } from './infrastructure/product-http-client.adapter';

export const REDIS_CLIENT = Symbol('REDIS_CLIENT');

@Module({
  controllers: [CartController],
  providers: [
    {
      provide: REDIS_CLIENT,
      useFactory: (configService: ConfigService): Redis =>
        new Redis({
          host: configService.get<string>('REDIS_HOST') ?? 'localhost',
          port: Number(configService.get<string>('REDIS_PORT') ?? 6379),
          password: configService.get<string>('REDIS_PASSWORD') || undefined,
          db: Number(configService.get<string>('REDIS_DB') ?? 0),
        }),
      inject: [ConfigService],
    },
    {
      provide: CART_REPOSITORY,
      useFactory: (redis: Redis): CartRepository =>
        new CartRedisRepository(redis),
      inject: [REDIS_CLIENT],
    },
    {
      provide: PRODUCT_CLIENT,
      useFactory: (configService: ConfigService): ProductClientPort =>
        new ProductHttpClientAdapter(
          configService.get<string>('PRODUCT_SERVICE_URL'),
        ),
      inject: [ConfigService],
    },
    {
      provide: AddToCartUseCase,
      useFactory: (
        repository: CartRepository,
        productClient: ProductClientPort,
      ): AddToCartUseCase => new AddToCartUseCase(repository, productClient),
      inject: [CART_REPOSITORY, PRODUCT_CLIENT],
    },
    {
      provide: ViewCartUseCase,
      useFactory: (repository: CartRepository): ViewCartUseCase =>
        new ViewCartUseCase(repository),
      inject: [CART_REPOSITORY],
    },
    {
      provide: UpdateCartItemUseCase,
      useFactory: (repository: CartRepository): UpdateCartItemUseCase =>
        new UpdateCartItemUseCase(repository),
      inject: [CART_REPOSITORY],
    },
    {
      provide: RemoveCartItemUseCase,
      useFactory: (repository: CartRepository): RemoveCartItemUseCase =>
        new RemoveCartItemUseCase(repository),
      inject: [CART_REPOSITORY],
    },
    {
      provide: ClearCartUseCase,
      useFactory: (repository: CartRepository): ClearCartUseCase =>
        new ClearCartUseCase(repository),
      inject: [CART_REPOSITORY],
    },
  ],
})
export class CartModule {}
