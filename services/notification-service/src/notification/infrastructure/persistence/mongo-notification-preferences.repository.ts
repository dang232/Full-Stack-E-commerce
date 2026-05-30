import { Injectable } from '@nestjs/common';
import { InjectModel } from '@nestjs/mongoose';
import { Model } from 'mongoose';
import { NotificationPreferencesRepository } from '../../domain/port/outbound/notification-preferences.repository';
import {
  NotificationPreferences,
  NotificationChannel,
  TypePreference,
} from '../../domain/model/notification-preferences';
import { NotificationType } from '../../domain/model/notification-type.enum';
import { NotificationPreferencesSchemaClass } from './mongo-notification-preferences.schema';

@Injectable()
export class MongoNotificationPreferencesRepository implements NotificationPreferencesRepository {
  constructor(
    @InjectModel(NotificationPreferencesSchemaClass.name)
    private readonly model: Model<NotificationPreferencesSchemaClass>,
  ) {}

  async findByUserId(userId: string): Promise<NotificationPreferences | null> {
    const doc = await this.model.findOne({ userId }).lean().exec();
    if (!doc) return null;

    return NotificationPreferences.reconstitute({
      userId: doc.userId,
      typePreferences: (doc.typePreferences ?? []).map((tp) => ({
        type: tp.type as NotificationType,
        channels: tp.channels as NotificationChannel[],
      })),
      muted: doc.muted ?? false,
      updatedAt: doc.updatedAt ?? new Date(),
    });
  }

  async save(preferences: NotificationPreferences): Promise<void> {
    const typePreferences: { type: string; channels: string[] }[] =
      preferences.typePreferences.map((tp: TypePreference) => ({
        type: tp.type,
        channels: tp.channels,
      }));

    await this.model.updateOne(
      { userId: preferences.userId },
      {
        $set: {
          typePreferences,
          muted: preferences.muted,
          updatedAt: preferences.updatedAt,
        },
        $setOnInsert: { userId: preferences.userId },
      },
      { upsert: true },
    );
  }
}
