import {
  EntityManager,
  EntityRepository,
  UniqueConstraintViolationException,
} from "@mikro-orm/core";
import { InjectRepository } from "@mikro-orm/nestjs";
import { Injectable } from "@nestjs/common";
import { Thread } from "../domain/thread";
import { ThreadListItem, ThreadRepository } from "../domain/thread.repository";
import { MessageMikroOrmEntity } from "./message.mikro-orm-entity";
import { ThreadMikroOrmEntity } from "./thread.mikro-orm-entity";

@Injectable()
export class ThreadMikroOrmRepository implements ThreadRepository {
  constructor(
    @InjectRepository(ThreadMikroOrmEntity)
    private readonly repository: EntityRepository<ThreadMikroOrmEntity>,
    @InjectRepository(MessageMikroOrmEntity)
    private readonly messages: EntityRepository<MessageMikroOrmEntity>,
    private readonly em: EntityManager,
  ) {}

  async findOrCreate(thread: Thread): Promise<Thread> {
    const existing = await this.findExisting(
      thread.buyerId,
      thread.sellerId,
      thread.productId,
    );
    if (existing) return existing;

    const entity = this.toRow(thread);
    try {
      await this.em.persistAndFlush(entity);
      return this.toDomain(entity);
    } catch (err) {
      // Concurrent insert from a second request — re-fetch the winner.
      if (err instanceof UniqueConstraintViolationException) {
        const winner = await this.findExisting(
          thread.buyerId,
          thread.sellerId,
          thread.productId,
        );
        if (winner) return winner;
      }
      throw err;
    }
  }

  async findExisting(
    buyerId: string,
    sellerId: string,
    productId: string | null,
  ): Promise<Thread | null> {
    const entity = await this.repository.findOne({
      buyerId,
      sellerId,
      productId,
    });
    return entity ? this.toDomain(entity) : null;
  }

  async findById(id: string): Promise<Thread | null> {
    const entity = await this.repository.findOne({ id });
    return entity ? this.toDomain(entity) : null;
  }

  async findForUser(userId: string, limit: number): Promise<ThreadListItem[]> {
    const entities = await this.repository.find(
      { $or: [{ buyerId: userId }, { sellerId: userId }] },
      { orderBy: { lastMessageAt: "DESC" }, limit },
    );
    if (entities.length === 0) return [];

    const threadIds = entities.map((entity) => entity.id);
    const latestMessages = await this.messages.find(
      { thread: { $in: threadIds } },
      { orderBy: { sentAt: "DESC" } },
    );
    const latestByThread = new Map<string, MessageMikroOrmEntity>();
    for (const msg of latestMessages) {
      const tid = msg.thread.id;
      if (!latestByThread.has(tid)) latestByThread.set(tid, msg);
    }

    const results: ThreadListItem[] = [];
    for (const entity of entities) {
      const thread = this.toDomain(entity);
      const lastReadAt =
        thread.buyerId === userId
          ? entity.buyerLastReadAt
          : entity.sellerLastReadAt;
      const unread = await this.messages.count({
        thread: entity.id,
        senderId: { $ne: userId },
        ...(lastReadAt ? { sentAt: { $gt: lastReadAt } } : {}),
      });
      const latest = latestByThread.get(entity.id);
      results.push({
        thread,
        lastMessageBody: latest?.body ?? null,
        lastMessageSenderId: latest?.senderId ?? null,
        unreadCount: unread,
      });
    }
    return results;
  }

  async save(thread: Thread): Promise<Thread> {
    const entity = await this.repository.findOne({ id: thread.id });
    if (!entity) {
      const fresh = this.toRow(thread);
      await this.em.persistAndFlush(fresh);
      return this.toDomain(fresh);
    }
    entity.lastMessageAt = thread.lastMessageAt;
    entity.buyerLastReadAt = thread.buyerLastReadAt;
    entity.sellerLastReadAt = thread.sellerLastReadAt;
    await this.em.flush();
    return this.toDomain(entity);
  }

  private toRow(thread: Thread): ThreadMikroOrmEntity {
    const entity = new ThreadMikroOrmEntity();
    entity.id = thread.id;
    entity.buyerId = thread.buyerId;
    entity.sellerId = thread.sellerId;
    entity.productId = thread.productId;
    entity.lastMessageAt = thread.lastMessageAt;
    entity.buyerLastReadAt = thread.buyerLastReadAt;
    entity.sellerLastReadAt = thread.sellerLastReadAt;
    entity.createdAt = thread.createdAt;
    return entity;
  }

  private toDomain(entity: ThreadMikroOrmEntity): Thread {
    return new Thread({
      id: entity.id,
      buyerId: entity.buyerId,
      sellerId: entity.sellerId,
      productId: entity.productId,
      lastMessageAt: entity.lastMessageAt,
      buyerLastReadAt: entity.buyerLastReadAt,
      sellerLastReadAt: entity.sellerLastReadAt,
      createdAt: entity.createdAt,
    });
  }
}
