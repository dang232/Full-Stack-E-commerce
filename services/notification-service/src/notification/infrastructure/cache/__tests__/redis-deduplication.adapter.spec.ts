import { Test } from '@nestjs/testing';
import RedisMock from 'ioredis-mock';
import { RedisDeduplicationAdapter } from '../redis-deduplication.adapter';
import { REDIS_CLIENT } from '../redis.module';

describe('RedisDeduplicationAdapter', () => {
  let adapter: RedisDeduplicationAdapter;

  beforeEach(async () => {
    const module = await Test.createTestingModule({
      providers: [
        RedisDeduplicationAdapter,
        { provide: REDIS_CLIENT, useValue: new RedisMock() },
      ],
    }).compile();
    adapter = module.get(RedisDeduplicationAdapter);
  });

  it('returns false for a new key', async () => {
    expect(await adapter.isDuplicate('new-key')).toBe(false);
  });

  it('returns true after markProcessed', async () => {
    await adapter.markProcessed('existing-key');
    expect(await adapter.isDuplicate('existing-key')).toBe(true);
  });

  it('different keys are independent', async () => {
    await adapter.markProcessed('key-a');
    expect(await adapter.isDuplicate('key-b')).toBe(false);
  });

  it('tryAcquire returns true on first call, false on second', async () => {
    expect(await adapter.tryAcquire('key-acquire')).toBe(true);
    expect(await adapter.tryAcquire('key-acquire')).toBe(false);
  });
});
