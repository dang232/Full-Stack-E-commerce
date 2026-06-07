import { NotificationType } from './notification-type.enum';

export enum NotificationChannel {
  IN_APP = 'IN_APP',
  EMAIL = 'EMAIL',
  PUSH = 'PUSH',
  SMS = 'SMS',
}

/** Per-type channel preference for a single notification type. */
export interface TypePreference {
  type: NotificationType;
  channels: NotificationChannel[];
}

export interface NotificationPreferencesProps {
  userId: string;
  /** Per-type overrides. Types not listed default to all channels enabled. */
  typePreferences: TypePreference[];
  /** Global mute — disables all notifications regardless of type prefs. */
  muted: boolean;
  updatedAt: Date;
}

/**
 * User notification preferences aggregate.
 * Controls which channels each notification type is delivered through.
 */
export class NotificationPreferences {
  readonly userId: string;
  private _typePreferences: TypePreference[];
  private _muted: boolean;
  private _updatedAt: Date;

  private constructor(props: NotificationPreferencesProps) {
    this.userId = props.userId;
    this._typePreferences = props.typePreferences;
    this._muted = props.muted;
    this._updatedAt = props.updatedAt;
  }

  /** Create default preferences (all types → all channels enabled). */
  static createDefault(userId: string): NotificationPreferences {
    return new NotificationPreferences({
      userId,
      typePreferences: Object.values(NotificationType).map((type) => ({
        type,
        channels: [NotificationChannel.IN_APP, NotificationChannel.EMAIL],
      })),
      muted: false,
      updatedAt: new Date(),
    });
  }

  static reconstitute(
    props: NotificationPreferencesProps,
  ): NotificationPreferences {
    return new NotificationPreferences(props);
  }

  get typePreferences(): TypePreference[] {
    return this._typePreferences;
  }

  get muted(): boolean {
    return this._muted;
  }

  get updatedAt(): Date {
    return this._updatedAt;
  }

  /** Check if a specific channel is enabled for a notification type. */
  isChannelEnabled(
    type: NotificationType,
    channel: NotificationChannel,
  ): boolean {
    if (this._muted) return false;

    const pref = this._typePreferences.find((p) => p.type === type);
    // If no explicit preference, default to enabled
    if (!pref) return true;
    return pref.channels.includes(channel);
  }

  /** Update channel preferences for a specific type. */
  setTypeChannels(
    type: NotificationType,
    channels: NotificationChannel[],
  ): void {
    const existing = this._typePreferences.findIndex((p) => p.type === type);
    if (existing >= 0) {
      this._typePreferences[existing] = { type, channels };
    } else {
      this._typePreferences.push({ type, channels });
    }
    this._updatedAt = new Date();
  }

  /** Bulk update all type preferences. */
  updateAll(typePreferences: TypePreference[], muted: boolean): void {
    this._typePreferences = typePreferences;
    this._muted = muted;
    this._updatedAt = new Date();
  }

  setMuted(muted: boolean): void {
    this._muted = muted;
    this._updatedAt = new Date();
  }
}
