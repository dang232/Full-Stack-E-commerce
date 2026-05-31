import { EntityManager, EntityRepository } from "@mikro-orm/core";
import { InjectRepository } from "@mikro-orm/nestjs";
import { Injectable } from "@nestjs/common";
import { Message } from "../domain/message";
import { MessagePage, MessageRepository } from "../domain/message.repository";
import { MessageMikroOrmEntity } from "./message.mikro-orm-entity";
import { ThreadMikroOrmEntity } from "./thread.mikro-orm-entity";

@Injectable()
export class MessageMikroOrmRepository implements MessageRepository {
  constructor(
    @InjectRepository(MessageMikroOrmEntity)
    private readonly repository: EntityRepository<MessageMikroOrmEntity>,
    @InjectRepository(ThreadMikroOrmEntity)
    private readonly threads: EntityRepository<ThreadMikroOrmEntity>,
    private readonly em: EntityManager,
  ) {}

  async findByThread(
    threadId: string,
    cursor: string | null,
    limit: number,
  ): Promise<MessagePage> {
    const cursorDate = cursor ? new Date(cursor) : null;
    const cap = Math.min(Math.max(limit, 1), 100);
    const entities = await this.repository.find(
      {
        thread: threadId,
        ...(cursorDate ? { sentAt: { $lt: cursorDate } } : {}),
      },
      {
        orderBy: { sentAt: "DESC" },
        limit: cap + 1,
      },
    );

    const hasMore = entities.length > cap;
    const page = hasMore ? entities.slice(0, cap) : entities;
    const nextCursor =
      hasMore && page.length > 0
        ? page[page.length - 1].sentAt.toISOString()
        : null;

    return {
      content: page.map((entity) => this.toDomain(entity)),
      nextCursor,
      hasMore,
    };
  }

  async save(message: Message): Promise<Message> {
    const threadEntity = await this.threads.findOneOrFail({
      id: message.threadId,
    });
    const entity = new MessageMikroOrmEntity();
    entity.id = message.id;
    entity.thread = threadEntity;
    entity.senderId = message.senderId;
    entity.body = message.body;
    entity.sentAt = message.sentAt;
    await this.em.persistAndFlush(entity);
    return this.toDomain(entity);
  }

  private toDomain(entity: MessageMikroOrmEntity): Message {
    return new Message({
      id: entity.id,
      threadId: entity.thread.id,
      senderId: entity.senderId,
      body: entity.body,
      sentAt: entity.sentAt,
    });
  }
}
