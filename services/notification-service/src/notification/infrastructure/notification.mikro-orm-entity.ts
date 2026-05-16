import { Entity, Enum, PrimaryKey, Property } from '@mikro-orm/core';
import { NotificationStatus } from '../domain/notification-status.enum';
import { NotificationType } from '../domain/notification-type.enum';

@Entity({ tableName: 'notifications', schema: 'notification_svc' })
export class NotificationMikroOrmEntity {
  @PrimaryKey({ type: 'uuid' })
  id!: string;

  @Property({ fieldName: 'user_id', length: 64 })
  userId!: string;

  @Enum(() => NotificationType)
  type!: NotificationType;

  @Property({ length: 255 })
  title!: string;

  @Property({ type: 'text' })
  body!: string;

  @Property({ type: 'jsonb' })
  data: Record<string, unknown> = {};

  @Property({ type: 'string[]' })
  channels!: string[];

  @Enum(() => NotificationStatus)
  status!: NotificationStatus;

  @Property({ fieldName: 'created_at', type: 'timestamptz' })
  createdAt: Date = new Date();
}
