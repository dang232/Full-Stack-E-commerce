import { NotificationPreferences, NotificationChannel } from '../notification-preferences';
import { NotificationType } from '../notification-type.enum';

describe('NotificationPreferences', () => {
  describe('createDefault', () => {
    it('creates preferences with muted=false', () => {
      const prefs = NotificationPreferences.createDefault('user-1');
      expect(prefs.userId).toBe('user-1');
      expect(prefs.muted).toBe(false);
    });

    it('creates preferences with all NotificationType entries', () => {
      const prefs = NotificationPreferences.createDefault('user-1');
      const types = prefs.typePreferences.map((tp) => tp.type);
      for (const type of Object.values(NotificationType)) {
        expect(types).toContain(type);
      }
    });

    it('default channels include IN_APP and EMAIL', () => {
      const prefs = NotificationPreferences.createDefault('user-1');
      for (const tp of prefs.typePreferences) {
        expect(tp.channels).toContain(NotificationChannel.IN_APP);
        expect(tp.channels).toContain(NotificationChannel.EMAIL);
      }
    });
  });

  describe('reconstitute', () => {
    it('restores all fields', () => {
      const now = new Date();
      const prefs = NotificationPreferences.reconstitute({
        userId: 'user-2',
        typePreferences: [
          {
            type: NotificationType.ORDER_CREATED,
            channels: [NotificationChannel.SMS],
          },
        ],
        muted: true,
        updatedAt: now,
      });

      expect(prefs.userId).toBe('user-2');
      expect(prefs.muted).toBe(true);
      expect(prefs.updatedAt).toBe(now);
      expect(prefs.typePreferences[0].channels).toEqual([
        NotificationChannel.SMS,
      ]);
    });
  });

  describe('isChannelEnabled', () => {
    it('returns true when channel is in the type preference', () => {
      const prefs = NotificationPreferences.createDefault('user-1');
      expect(
        prefs.isChannelEnabled(
          NotificationType.ORDER_CREATED,
          NotificationChannel.EMAIL,
        ),
      ).toBe(true);
    });

    it('returns false when channel is not in the type preference', () => {
      const prefs = NotificationPreferences.createDefault('user-1');
      prefs.setTypeChannels(NotificationType.ORDER_CREATED, [
        NotificationChannel.IN_APP,
      ]);
      expect(
        prefs.isChannelEnabled(
          NotificationType.ORDER_CREATED,
          NotificationChannel.EMAIL,
        ),
      ).toBe(false);
    });

    it('returns false for any channel when muted', () => {
      const prefs = NotificationPreferences.createDefault('user-1');
      prefs.setMuted(true);
      expect(
        prefs.isChannelEnabled(
          NotificationType.ORDER_CREATED,
          NotificationChannel.IN_APP,
        ),
      ).toBe(false);
    });

    it('returns true for a type with no explicit preference (defaults to enabled)', () => {
      const prefs = NotificationPreferences.reconstitute({
        userId: 'user-3',
        typePreferences: [], // no overrides
        muted: false,
        updatedAt: new Date(),
      });
      expect(
        prefs.isChannelEnabled(
          NotificationType.ORDER_CREATED,
          NotificationChannel.PUSH,
        ),
      ).toBe(true);
    });
  });

  describe('setTypeChannels', () => {
    it('updates channels for an existing type', () => {
      const prefs = NotificationPreferences.createDefault('user-1');
      prefs.setTypeChannels(NotificationType.ORDER_CREATED, [
        NotificationChannel.SMS,
      ]);
      const pref = prefs.typePreferences.find(
        (tp) => tp.type === NotificationType.ORDER_CREATED,
      );
      expect(pref?.channels).toEqual([NotificationChannel.SMS]);
    });

    it('adds a new type preference when type not present', () => {
      const prefs = NotificationPreferences.reconstitute({
        userId: 'user-4',
        typePreferences: [],
        muted: false,
        updatedAt: new Date(),
      });
      prefs.setTypeChannels(NotificationType.ORDER_SHIPPED, [
        NotificationChannel.PUSH,
      ]);
      expect(prefs.typePreferences).toHaveLength(1);
      expect(prefs.typePreferences[0].type).toBe(NotificationType.ORDER_SHIPPED);
    });

    it('updates updatedAt timestamp', () => {
      const before = new Date(Date.now() - 1000);
      const prefs = NotificationPreferences.reconstitute({
        userId: 'user-5',
        typePreferences: [],
        muted: false,
        updatedAt: before,
      });
      prefs.setTypeChannels(NotificationType.ORDER_CREATED, []);
      expect(prefs.updatedAt.getTime()).toBeGreaterThan(before.getTime());
    });
  });

  describe('setMuted', () => {
    it('sets muted to true', () => {
      const prefs = NotificationPreferences.createDefault('user-1');
      prefs.setMuted(true);
      expect(prefs.muted).toBe(true);
    });

    it('sets muted to false', () => {
      const prefs = NotificationPreferences.reconstitute({
        userId: 'user-6',
        typePreferences: [],
        muted: true,
        updatedAt: new Date(),
      });
      prefs.setMuted(false);
      expect(prefs.muted).toBe(false);
    });
  });

  describe('updateAll', () => {
    it('replaces all type preferences and updates muted', () => {
      const prefs = NotificationPreferences.createDefault('user-1');
      prefs.updateAll(
        [
          {
            type: NotificationType.PAYMENT_COMPLETED,
            channels: [NotificationChannel.EMAIL],
          },
        ],
        true,
      );
      expect(prefs.typePreferences).toHaveLength(1);
      expect(prefs.muted).toBe(true);
    });
  });
});
