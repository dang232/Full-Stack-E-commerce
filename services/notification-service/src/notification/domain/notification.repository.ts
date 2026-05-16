import { Notification } from './notification';

export const NOTIFICATION_REPOSITORY = Symbol('NOTIFICATION_REPOSITORY');

export interface PageQuery {
  page: number;
  size: number;
}

export interface PageResult<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
  first: boolean;
  last: boolean;
}

export interface NotificationRepository {
  save(notification: Notification): Promise<Notification>;
  findByUserId(userId: string): Promise<Notification[]>;
  findByUserIdPaged(
    userId: string,
    page: PageQuery,
  ): Promise<PageResult<Notification>>;
  findById(id: string): Promise<Notification | null>;
  markSent(id: string): Promise<void>;
  markRead(id: string, userId: string): Promise<Notification | null>;
  markAllRead(userId: string): Promise<number>;
  countUnread(userId: string): Promise<number>;
}
