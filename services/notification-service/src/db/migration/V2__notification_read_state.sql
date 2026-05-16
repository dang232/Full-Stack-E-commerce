-- Read/unread tracking for notifications. Existing rows get read=false so the
-- bell badge surfaces them as unread until the user opens the dropdown.

ALTER TABLE notification_svc.notifications
  ADD COLUMN IF NOT EXISTS read BOOLEAN NOT NULL DEFAULT FALSE,
  ADD COLUMN IF NOT EXISTS read_at TIMESTAMPTZ NULL;

CREATE INDEX IF NOT EXISTS idx_notifications_user_unread
  ON notification_svc.notifications (user_id)
  WHERE read = FALSE;
