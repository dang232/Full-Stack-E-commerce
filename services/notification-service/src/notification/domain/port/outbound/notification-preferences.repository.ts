import { NotificationPreferences } from '../../model/notification-preferences';

export interface NotificationPreferencesRepository {
  findByUserId(userId: string): Promise<NotificationPreferences | null>;
  save(preferences: NotificationPreferences): Promise<void>;
}

export const NOTIFICATION_PREFERENCES_REPOSITORY = Symbol('NOTIFICATION_PREFERENCES_REPOSITORY');
