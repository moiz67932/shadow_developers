CREATE TABLE day2_costream_replays (
  session_id text REFERENCES day2_costream_sessions ON DELETE CASCADE,
  request_hash text NOT NULL, response jsonb NOT NULL,
  PRIMARY KEY(session_id,request_hash)
);
