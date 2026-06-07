import { Inject, Injectable } from '@nestjs/common';
import Redis from 'ioredis';
import { DeduplicationPort } from '../../domain/port/outbound/deduplication.port';
import { REDIS_CLIENT } from './redis.module';

const KEY_PREFIX = 'dedup:';
const DEFAULT_TTL_SECONDS = 86400; // 24 hours

@Injectable()
export class RedisDeduplicationAdapter implements DeduplicationPort {
  /* istanbul ignore next */
  constructor(@Inject(REDIS_CLIENT) private readonly redis: Redis) {}

  async isDuplicate(idempotencyKey: string): Promise<boolean> {
    const result = await this.redis.exists(`${KEY_PREFIX}${idempotencyKey}`);
    return result === 1;
  }

  async markProcessed(
    idempotencyKey: string,
    ttlSeconds = DEFAULT_TTL_SECONDS,
  ): Promise<void> {
    await this.redis.set(
      `${KEY_PREFIX}${idempotencyKey}`,
      '1',
      'EX',
      ttlSeconds,
    );
  }

  async tryAcquire(
    idempotencyKey: string,
    ttlSeconds = DEFAULT_TTL_SECONDS,
  ): Promise<boolean> {
    const result = await this.redis.set(
      `${KEY_PREFIX}${idempotencyKey}`,
      '1',
      'EX',
      ttlSeconds,
      'NX',
    );
    return result === 'OK';
  }
}
