ALTER TABLE day2_chat_messages ADD COLUMN created_at timestamptz NOT NULL DEFAULT now();
CREATE INDEX day2_chat_sender_window ON day2_chat_messages(chat_room_id,sender,created_at DESC);
