import { EntityManager, EntityRepository } from '@mikro-orm/core';
import { InjectRepository } from '@mikro-orm/nestjs';
import { Injectable } from '@nestjs/common';
import { Notification } from '../domain/notification';
import { NotificationStatus } from '../domain/notification-status.enum';
import {
  NotificationRepository,
  PageQuery,
  PageResult,
} from '../domain/notification.repository';
import { NotificationMikroOrmEntity } from './notification.mikro-orm-entity';

@Injectable()
export class NotificationMikroOrmRepository implements NotificationRepository {
  constructor(
    @InjectRepository(NotificationMikroOrmEntity)
    private readonly repository: EntityRepository<NotificationMikroOrmEntity>,
    private readonly em: EntityManager,
  ) {}

  async save(notification: Notification): Promise<Notification> {
    const existing = await this.repository.findOne({ id: notification.id });
    if (existing) {
      this.applyRow(existing, notification);
      await this.em.flush();
      return this.toDomain(existing);
    }
    const entity = this.toRow(notification);
    await this.em.persistAndFlush(entity);
    return this.toDomain(entity);
  }

  async findByUserId(userId: string): Promise<Notification[]> {
    const entities = await this.repository.find(
      { userId },
      { orderBy: { createdAt: 'DESC' } },
    );
    return entities.map((entity) => this.toDomain(entity));
  }

  async findByUserIdPaged(
    userId: string,
    page: PageQuery,
  ): Promise<PageResult<Notification>> {
    const [entities, total] = await this.repository.findAndCount(
      { userId },
      {
        orderBy: { createdAt: 'DESC' },
        limit: page.size,
        offset: page.page * page.size,
      },
    );
    const totalPages = page.size > 0 ? Math.ceil(total / page.size) : 0;
    return {
      content: entities.map((entity) => this.toDomain(entity)),
      totalElements: total,
      totalPages,
      number: page.page,
      size: page.size,
      first: page.page === 0,
      last: page.page >= totalPages - 1,
    };
  }

  async findById(id: string): Promise<Notification | null> {
    const entity = await this.repository.findOne({ id });
    return entity ? this.toDomain(entity) : null;
  }

  async markSent(id: string): Promise<void> {
    const entity = await this.repository.findOne({ id });
    if (!entity) return;
    entity.status = NotificationStatus.SENT;
    await this.em.flush();
  }

  async markRead(id: string, userId: string): Promise<Notification | null> {
    const entity = await this.repository.findOne({ id, userId });
    if (!entity) return null;
    if (!entity.read) {
      entity.read = true;
      entity.readAt = new Date();
      await this.em.flush();
    }
    return this.toDomain(entity);
  }

  async markAllRead(userId: string): Promise<number> {
    const now = new Date();
    return this.em.nativeUpdate(
      NotificationMikroOrmEntity,
      { userId, read: false },
      { read: true, readAt: now },
    );
  }

  async countUnread(userId: string): Promise<number> {
    return this.repository.count({ userId, read: false });
  }

  private toRow(notification: Notification): NotificationMikroOrmEntity {
    const entity = new NotificationMikroOrmEntity();
    this.applyRow(entity, notification);
    return entity;
  }

  private applyRow(
    entity: NotificationMikroOrmEntity,
    notification: Notification,
  ): void {
    entity.id = notification.id;
    entity.userId = notification.userId;
    entity.type = notification.type;
    entity.title = notification.title;
    entity.body = notification.body;
    entity.data = notification.data;
    entity.channels = notification.channels;
    entity.status = notification.status;
    entity.read = notification.read;
    entity.readAt = notification.readAt;
    entity.createdAt = notification.createdAt;
  }

  private toDomain(entity: NotificationMikroOrmEntity): Notification {
    return new Notification({
      id: entity.id,
      userId: entity.userId,
      type: entity.type,
      title: entity.title,
      body: entity.body,
      data: entity.data,
      channels: entity.channels,
      status: entity.status,
      read: entity.read,
      readAt: entity.readAt,
      createdAt: entity.createdAt,
    });
  }
}
