import { Test } from '@nestjs/testing';
import RedisMock from 'ioredis-mock';
import { RedisConnectionRegistryAdapter } from '../redis-connection-registry.adapter';
import { REDIS_CLIENT } from '../redis.module';

describe('RedisConnectionRegistryAdapter', () => {
  let adapter: RedisConnectionRegistryAdapter;

  beforeEach(async () => {
    const module = await Test.createTestingModule({
      providers: [
        RedisConnectionRegistryAdapter,
        { provide: REDIS_CLIENT, useValue: new RedisMock() },
      ],
    }).compile();
    adapter = module.get(RedisConnectionRegistryAdapter);
  });

  it('registers and retrieves socket ids', async () => {
    await adapter.register('user-1', 'socket-a');
    await adapter.register('user-1', 'socket-b');
    const ids = await adapter.getSocketIds('user-1');
    expect(ids.sort()).toEqual(['socket-a', 'socket-b']);
  });

  it('unregisters a socket', async () => {
    await adapter.register('user-2', 'socket-x');
    await adapter.unregister('user-2', 'socket-x');
    const ids = await adapter.getSocketIds('user-2');
    expect(ids).toHaveLength(0);
  });

  it('isOnline returns true when sockets exist', async () => {
    await adapter.register('user-3', 'socket-1');
    expect(await adapter.isOnline('user-3')).toBe(true);
  });

  it('isOnline returns false when no sockets', async () => {
    expect(await adapter.isOnline('user-nobody')).toBe(false);
  });

  it('returns empty array when multi.exec returns null', async () => {
    // Simulate the results[0][0] error branch in drainOfflineQueue
    const redisMock = new RedisMock();
    const originalMulti = redisMock.multi.bind(redisMock);
    jest.spyOn(redisMock, 'multi').mockImplementationOnce(() => {
      const pipeline = originalMulti();
      const originalExec = pipeline.exec.bind(pipeline);
      jest.spyOn(pipeline, 'exec').mockResolvedValueOnce(null as any);
      return pipeline;
    });

    const mod = await Test.createTestingModule({
      providers: [
        RedisConnectionRegistryAdapter,
        { provide: REDIS_CLIENT, useValue: redisMock },
      ],
    }).compile();
    const a = mod.get(RedisConnectionRegistryAdapter);

    const result = await a.drainOfflineQueue('user-null-exec');
    expect(result).toEqual([]);
  });

  it('refreshes registration TTL', async () => {
    await adapter.register('user-5', 'socket-r');
    await expect(adapter.refreshRegistration('user-5')).resolves.not.toThrow();
  });

  it('enqueues and drains offline notifications', async () => {
    await adapter.enqueueOffline('user-4', 'notif-1');
    await adapter.enqueueOffline('user-4', 'notif-2');

    const ids = await adapter.drainOfflineQueue('user-4');
    expect(ids).toEqual(['notif-1', 'notif-2']);

    // Queue is empty after drain
    const empty = await adapter.drainOfflineQueue('user-4');
    expect(empty).toHaveLength(0);
  });
});
