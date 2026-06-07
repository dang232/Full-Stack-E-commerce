import { Inject, Injectable } from '@nestjs/common';
import {
  NOTIFICATION_PREFERENCES_REPOSITORY,
  NotificationPreferencesRepository,
} from '../../domain/port/outbound/notification-preferences.repository';
import { NotificationPreferences } from '../../domain/model/notification-preferences';

@Injectable()
export class GetPreferencesUseCase {
  /* istanbul ignore next */
  constructor(
    @Inject(NOTIFICATION_PREFERENCES_REPOSITORY)
    private readonly repo: NotificationPreferencesRepository,
  ) {}

  async execute(userId: string): Promise<NotificationPreferences> {
    const existing = await this.repo.findByUserId(userId);
    if (existing) return existing;

    // First access — create default preferences
    const defaults = NotificationPreferences.createDefault(userId);
    await this.repo.save(defaults);
    return defaults;
  }
}
