import { Inject, Injectable } from '@nestjs/common';
import Redis from 'ioredis';
import { ConnectionRegistryPort } from '../../domain/port/outbound/connection-registry.port';
import { REDIS_CLIENT } from './redis.module';

const SOCKETS_PREFIX = 'ws:connections:';
const OFFLINE_PREFIX = 'offline:';
const SOCKET_TTL = 120; // 2 minutes — stale entries expire quickly after crash
const OFFLINE_TTL = 604800; // 7 days

@Injectable()
export class RedisConnectionRegistryAdapter implements ConnectionRegistryPort {
  constructor(@Inject(REDIS_CLIENT) private readonly redis: Redis) {}

  async register(userId: string, socketId: string): Promise<void> {
    await this.redis.sadd(`${SOCKETS_PREFIX}${userId}`, socketId);
    await this.redis.expire(`${SOCKETS_PREFIX}${userId}`, SOCKET_TTL);
  }

  async refreshRegistration(userId: string): Promise<void> {
    await this.redis.expire(`${SOCKETS_PREFIX}${userId}`, SOCKET_TTL);
  }

  async unregister(userId: string, socketId: string): Promise<void> {
    await this.redis.srem(`${SOCKETS_PREFIX}${userId}`, socketId);
  }

  async getSocketIds(userId: string): Promise<string[]> {
    return this.redis.smembers(`${SOCKETS_PREFIX}${userId}`);
  }

  async isOnline(userId: string): Promise<boolean> {
    const count = await this.redis.scard(`${SOCKETS_PREFIX}${userId}`);
    return count > 0;
  }

  async enqueueOffline(userId: string, notificationId: string): Promise<void> {
    const key = `${OFFLINE_PREFIX}${userId}`;
    await this.redis.rpush(key, notificationId);
    await this.redis.ltrim(key, -500, -1); // Keep latest 500
    await this.redis.expire(key, OFFLINE_TTL);
  }

  async drainOfflineQueue(userId: string): Promise<string[]> {
    const key = `${OFFLINE_PREFIX}${userId}`;
    const multi = this.redis.multi();
    multi.lrange(key, 0, -1);
    multi.del(key);
    const results = await multi.exec();
    if (!results || !results[0] || results[0][0]) return [];
    return (results[0][1] as string[]) ?? [];
  }
}
