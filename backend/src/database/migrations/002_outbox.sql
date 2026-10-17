ALTER TABLE domain_events ADD COLUMN IF NOT EXISTS published_at timestamptz;
CREATE INDEX IF NOT EXISTS pending_domain_events ON domain_events(id) WHERE published_at IS NULL;
