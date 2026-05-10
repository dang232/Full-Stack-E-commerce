import { randomUUID } from 'node:crypto';
import { NotificationStatus } from './notification-status.enum';
import { NotificationType } from './notification-type.enum';

export interface NotificationProperties {
  id: string;
  userId: string;
  type: NotificationType;
  title: string;
  body: string;
  data: Record<string, unknown>;
  channels: string[];
  status: NotificationStatus;
  createdAt: Date;
}

export class Notification {
  public readonly id: string;
  public readonly userId: string;
  public readonly type: NotificationType;
  public readonly title: string;
  public readonly body: string;
  public readonly data: Record<string, unknown>;
  public readonly channels: string[];
  public status: NotificationStatus;
  public readonly createdAt: Date;

  constructor(properties: NotificationProperties) {
    this.id = properties.id;
    this.userId = properties.userId;
    this.type = properties.type;
    this.title = properties.title;
    this.body = properties.body;
    this.data = properties.data;
    this.channels = properties.channels;
    this.status = properties.status;
    this.createdAt = properties.createdAt;
  }

  static create(properties: Omit<NotificationProperties, 'id' | 'status' | 'createdAt'>): Notification {
    return new Notification({
      ...properties,
      id: randomUUID(),
      status: NotificationStatus.PENDING,
      createdAt: new Date(),
    });
  }
}
