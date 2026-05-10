import { Injectable } from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository } from 'typeorm';
import { Notification } from '../domain/notification';
import { NotificationStatus } from '../domain/notification-status.enum';
import { NotificationRepository } from '../domain/notification.repository';
import { NotificationTypeOrmEntity } from './notification.typeorm-entity';

@Injectable()
export class NotificationTypeOrmRepository implements NotificationRepository {
  constructor(
    @InjectRepository(NotificationTypeOrmEntity)
    private readonly repository: Repository<NotificationTypeOrmEntity>,
  ) {}

  async save(notification: Notification): Promise<Notification> {
    const entity = this.toEntity(notification);
    const savedEntity = await this.repository.save(entity);
    return this.toDomain(savedEntity);
  }

  async findByUserId(userId: string): Promise<Notification[]> {
    const entities = await this.repository.find({
      where: { userId },
      order: { createdAt: 'DESC' },
    });

    return entities.map((entity) => this.toDomain(entity));
  }

  async findById(id: string): Promise<Notification | null> {
    const entity = await this.repository.findOne({ where: { id } });
    return entity ? this.toDomain(entity) : null;
  }

  async markSent(id: string): Promise<void> {
    await this.repository.update({ id }, { status: NotificationStatus.SENT });
  }

  private toEntity(notification: Notification): NotificationTypeOrmEntity {
    const entity = new NotificationTypeOrmEntity();
    entity.id = notification.id;
    entity.userId = notification.userId;
    entity.type = notification.type;
    entity.title = notification.title;
    entity.body = notification.body;
    entity.data = notification.data;
    entity.channels = notification.channels;
    entity.status = notification.status;
    entity.createdAt = notification.createdAt;
    return entity;
  }

  private toDomain(entity: NotificationTypeOrmEntity): Notification {
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
