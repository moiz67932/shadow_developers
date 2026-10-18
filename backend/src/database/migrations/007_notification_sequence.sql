ALTER TABLE day2_notifications ADD COLUMN sequence bigserial;
CREATE UNIQUE INDEX day2_notification_sequence ON day2_notifications(sequence);
