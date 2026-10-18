-- Indexes for the Phase 3 profile/social read paths; no feature handlers yet.
CREATE INDEX day2_followers_by_athlete ON day2_follows(athlete_id);
CREATE INDEX day2_vods_by_player ON day2_vods(player_id,created_at DESC);
