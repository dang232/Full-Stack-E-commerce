import { randomUUID } from 'node:crypto';
import { NotificationType } from './notification-type.enum';
import { Priority } from './priority.enum';
import { DeliveryStatus, DeliveryStatusValue } from './delivery-status';
import { NotificationThread } from './notification-thread';
import { NotificationCreatedEvent } from '../event/notification-created.event';
import { DomainEvent } from '../event/domain-event';

export interface CreateNotificationProps {
  userId: string;
  type: NotificationType;
  title: string;
  body: string;
  deepLink?: string;
  priority?: Priority;
  thread?: NotificationThread;
  metadata?: Record<string, unknown>;
  idempotencyKey?: string;
}

export interface NotificationProps {
  id: string;
  userId: string;
  type: NotificationType;
  title: string;
  body: string;
  deepLink: string | null;
  priority: Priority;
  thread: NotificationThread | null;
  metadata: Record<string, unknown>;
  idempotencyKey: string | null;
  read: boolean;
  readAt: Date | null;
  deliveryStatus: DeliveryStatus;
  createdAt: Date;
  retryCount: number;
}

export class Notification {
  readonly id: string;
  readonly userId: string;
  readonly type: NotificationType;
  readonly title: string;
  readonly body: string;
  readonly deepLink: string | null;
  readonly priority: Priority;
  readonly thread: NotificationThread | null;
  readonly metadata: Record<string, unknown>;
  readonly idempotencyKey: string | null;
  readonly createdAt: Date;

  private _read: boolean;
  private _readAt: Date | null;
  private _deliveryStatus: DeliveryStatus;
  private _retryCount: number;
  private readonly _domainEvents: DomainEvent[] = [];

  private constructor(props: NotificationProps) {
    this.id = props.id;
    this.userId = props.userId;
    this.type = props.type;
    this.title = props.title;
    this.body = props.body;
    this.deepLink = props.deepLink;
    this.priority = props.priority;
    this.thread = props.thread;
    this.metadata = props.metadata;
    this.idempotencyKey = props.idempotencyKey;
    this.createdAt = props.createdAt;
    this._read = props.read;
    this._readAt = props.readAt;
    this._deliveryStatus = props.deliveryStatus;
    this._retryCount = props.retryCount;
  }

  static create(props: CreateNotificationProps): Notification {
    const notification = new Notification({
      id: randomUUID(),
      userId: props.userId,
      type: props.type,
      title: props.title,
      body: props.body,
      deepLink: props.deepLink ?? null,
      priority: props.priority ?? Priority.MEDIUM,
      thread: props.thread ?? null,
      metadata: props.metadata ?? {},
      idempotencyKey: props.idempotencyKey ?? null,
      read: false,
      readAt: null,
      deliveryStatus: DeliveryStatus.queued(),
      createdAt: new Date(),
      retryCount: 0,
    });
    notification._domainEvents.push(
      new NotificationCreatedEvent(
        notification.id,
        notification.userId,
        notification.type,
      ),
    );
    return notification;
  }

  static reconstitute(props: NotificationProps): Notification {
    return new Notification(props);
  }

  get read(): boolean {
    return this._read;
  }

  get readAt(): Date | null {
    return this._readAt;
  }

  get deliveryStatus(): DeliveryStatus {
    return this._deliveryStatus;
  }

  get retryCount(): number {
    return this._retryCount;
  }

  markSent(): void {
    this._deliveryStatus = this._deliveryStatus.transitionTo(
      DeliveryStatusValue.SENT,
    );
  }

  markDelivered(): void {
    this._deliveryStatus = this._deliveryStatus.transitionTo(
      DeliveryStatusValue.DELIVERED,
    );
  }

  markOpened(): void {
    this._deliveryStatus = this._deliveryStatus.transitionTo(
      DeliveryStatusValue.OPENED,
    );
  }

  markFailed(): void {
    this._deliveryStatus = this._deliveryStatus.transitionTo(
      DeliveryStatusValue.FAILED,
    );
  }

  retry(): void {
    this._deliveryStatus = this._deliveryStatus.transitionTo(
      DeliveryStatusValue.QUEUED,
    );
  }

  moveToDlq(): void {
    this._deliveryStatus = this._deliveryStatus.transitionTo(
      DeliveryStatusValue.DLQ,
    );
  }

  markRead(): void {
    if (this._read) return;
    this._read = true;
    this._readAt = new Date();
  }

  canRetry(maxRetries: number): boolean {
    return this._retryCount < maxRetries;
  }

  incrementRetry(): void {
    this._retryCount++;
  }

  pullDomainEvents(): DomainEvent[] {
    const events = [...this._domainEvents];
    this._domainEvents.length = 0;
    return events;
  }
}
