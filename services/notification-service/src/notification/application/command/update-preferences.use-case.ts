import { Inject, Injectable } from '@nestjs/common';
import {
  NOTIFICATION_PREFERENCES_REPOSITORY,
  NotificationPreferencesRepository,
} from '../../domain/port/outbound/notification-preferences.repository';
import {
  NotificationPreferences,
  NotificationChannel,
  TypePreference,
} from '../../domain/model/notification-preferences';
import { NotificationType } from '../../domain/model/notification-type.enum';

export interface UpdatePreferencesCommand {
  userId: string;
  typePreferences: { type: NotificationType; channels: NotificationChannel[] }[];
  muted: boolean;
}

@Injectable()
export class UpdatePreferencesUseCase {
  constructor(
    @Inject(NOTIFICATION_PREFERENCES_REPOSITORY)
    private readonly repo: NotificationPreferencesRepository,
  ) {}

  async execute(command: UpdatePreferencesCommand): Promise<NotificationPreferences> {
    let prefs = await this.repo.findByUserId(command.userId);
    if (!prefs) {
      prefs = NotificationPreferences.createDefault(command.userId);
    }

    const typePreferences: TypePreference[] = command.typePreferences.map((tp) => ({
      type: tp.type,
      channels: tp.channels.filter((ch) =>
        Object.values(NotificationChannel).includes(ch),
      ),
    }));

    prefs.updateAll(typePreferences, command.muted);
    await this.repo.save(prefs);
    return prefs;
  }
}
