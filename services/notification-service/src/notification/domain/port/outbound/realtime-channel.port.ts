import { Notification } from '../../model/notification';

export interface RealtimeChannelPort {
  sendToUser(userId: string, notification: Notification): Promise<void>;
  sendBatchToUser(userId: string, notifications: Notification[]): Promise<void>;
}

export const REALTIME_CHANNEL_PORT = Symbol('REALTIME_CHANNEL_PORT');
