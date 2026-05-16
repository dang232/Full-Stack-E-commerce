import { EntityManager, EntityRepository } from '@mikro-orm/core';
import { InjectRepository } from '@mikro-orm/nestjs';
import { Injectable } from '@nestjs/common';
import { Notification } from '../domain/notification';
import { NotificationStatus } from '../domain/notification-status.enum';
import { NotificationRepository } from '../domain/notification.repository';
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
    const entity = existing ?? this.repository.create(this.toRow(notification));
    if (existing) {
      this.applyRow(existing, this.toRow(notification));
    }
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
      createdAt: entity.createdAt,
    });
  }
}
