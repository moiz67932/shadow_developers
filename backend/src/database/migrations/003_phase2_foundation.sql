-- Additive Phase II persistence. No replacement or deletion of Phase I records.
CREATE TABLE day2_stream_keys (
  stream_key text PRIMARY KEY, player_id text NOT NULL, label text NOT NULL DEFAULT 'default',
  status text NOT NULL CHECK(status IN ('active','revoked')), created_at timestamptz NOT NULL DEFAULT now(), revoked_at timestamptz
);
CREATE UNIQUE INDEX day2_one_active_key ON day2_stream_keys(player_id) WHERE status='active';
CREATE TABLE day2_costream_sessions (
  session_id text PRIMARY KEY, request jsonb NOT NULL, response jsonb NOT NULL,
  status text NOT NULL CHECK(status IN ('active','terminated')), active_host text,
  layout text NOT NULL CHECK(layout IN ('solo','grid_2','grid_4','spotlight')), created_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE day2_costream_participants (
  session_id text REFERENCES day2_costream_sessions ON DELETE CASCADE, player_id text NOT NULL,
  position integer NOT NULL CHECK(position>=0), active boolean NOT NULL DEFAULT true, muted boolean NOT NULL DEFAULT false,
  PRIMARY KEY(session_id,player_id), UNIQUE(session_id,position)
);
CREATE TABLE day2_chat_rooms (chat_room_id text PRIMARY KEY, last_sequence bigint NOT NULL DEFAULT 0);
CREATE TABLE day2_chat_messages (
  chat_room_id text REFERENCES day2_chat_rooms ON DELETE CASCADE, message_id text NOT NULL,
  sequence bigint NOT NULL, sender text NOT NULL, payload jsonb NOT NULL,
  PRIMARY KEY(chat_room_id,message_id), UNIQUE(chat_room_id,sequence)
);
CREATE TABLE day2_moderator_actions (
  id bigserial PRIMARY KEY, chat_room_id text REFERENCES day2_chat_rooms ON DELETE CASCADE,
  sender text NOT NULL, action text NOT NULL CHECK(action IN ('kick','mute','spotlight')),
  UNIQUE(chat_room_id,sender,action)
);
CREATE TABLE day2_qr_links (
  entity_id text PRIMARY KEY, entity_type text NOT NULL CHECK(entity_type IN ('match','club','player','costream')),
  short_code text NOT NULL UNIQUE, scan_count bigint NOT NULL DEFAULT 0 CHECK(scan_count>=0)
);
CREATE TABLE day2_qr_scan_buckets (
  entity_id text REFERENCES day2_qr_links ON DELETE CASCADE, utc_bucket timestamptz NOT NULL,
  device_family text NOT NULL CHECK(device_family IN ('mobile','desktop')), scans bigint NOT NULL DEFAULT 0,
  PRIMARY KEY(entity_id,utc_bucket,device_family)
);
CREATE TABLE day2_guest_tokens (
  token text PRIMARY KEY, role text NOT NULL DEFAULT 'guest', expires_at timestamptz NOT NULL,
  registered_user_id text, invalidated_at timestamptz, anonymized boolean NOT NULL DEFAULT false
);
CREATE INDEX day2_guest_user ON day2_guest_tokens(registered_user_id);
CREATE TABLE day2_carts (user_id text PRIMARY KEY, currency text NOT NULL DEFAULT 'PKR' CHECK(currency='PKR'));
CREATE TABLE day2_cart_items (
  user_id text REFERENCES day2_carts ON DELETE CASCADE, variant_id text REFERENCES inventory,
  quantity integer NOT NULL CHECK(quantity>0), PRIMARY KEY(user_id,variant_id)
);
CREATE TABLE day2_cart_merges (
  guest_token text PRIMARY KEY REFERENCES day2_guest_tokens ON DELETE CASCADE, user_id text NOT NULL,
  request jsonb NOT NULL, response jsonb NOT NULL, created_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE day2_notifications (
  notification_id text PRIMARY KEY, recipient_id text, topic text NOT NULL, payload jsonb NOT NULL,
  attempts integer NOT NULL DEFAULT 0 CHECK(attempts BETWEEN 0 AND 3),
  status text NOT NULL DEFAULT 'pending' CHECK(status IN ('pending','delivered','dead')),
  next_attempt_at timestamptz NOT NULL DEFAULT now(), created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX day2_notifications_due ON day2_notifications(next_attempt_at) WHERE status='pending';
CREATE INDEX day2_notifications_recipient ON day2_notifications(recipient_id);
CREATE TABLE day2_notification_dlq (
  notification_id text PRIMARY KEY REFERENCES day2_notifications ON DELETE CASCADE,
  payload jsonb NOT NULL, failed_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE day2_follows (follower_id text NOT NULL, athlete_id text NOT NULL, PRIMARY KEY(follower_id,athlete_id));
CREATE TABLE day2_vods (vod_id text PRIMARY KEY, player_id text, session_id text, object_key text NOT NULL, created_at timestamptz NOT NULL DEFAULT now());
CREATE TABLE day2_private_cache_refs (user_id text NOT NULL, cache_key text NOT NULL, PRIMARY KEY(user_id,cache_key));
