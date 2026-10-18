# Phase 1 foundation and ownership

Android/Stitch files are owned by the separate UI session and were not edited.
No Git history rewrite, commit or push is part of Phase 1 execution.

## Persistence and authentication

`003_phase2_foundation.sql` adds relational tables for keys, sessions, participants,
ordered chat, moderation actions, QR aggregates, guest tokens, carts/merge receipts,
notification retries/DLQ, follows, VOD indexes and private cache references.
Unique indexes enforce one active stream key per player, one cart merge per guest,
one follow per pair, unique room sequences and deduplicated moderator actions.

Startup seeding is additive. Existing inventory/orders/rosters/match streams are
preserved. Audit reset intentionally resets all phases and must only be called on
the isolated test database during development.

Member sessions derive identity and ownership from the authenticated user, include
`sub`, and are distinct from Day 1 audit-probe tokens. Production routes reject probe
tokens. Set a stable `JWT_SECRET`; if absent, a domain-separated key derived from
environment `DAY1_SALT` survives restart. Old role-only sessions require re-login.
Explicit guest-token writes are blocked centrally. Audit endpoints retain their
published no-header behavior; this is not production authentication.

## Infrastructure

Selenium uses exactly 4.25.0: Hub, two Chrome nodes and one Firefox node, with 1 GiB
shared memory, maximum two sessions each, VNC and noVNC disabled. MediaMTX is 1.9.1.
Publishing is deliberately denied until Phase 2 adds stream-key authentication.
The internal control API is not published on a host port.

The historical MinIO image could not be pulled and the binary endpoint returned
HTTP 410. The Dockerfile builds the exact official historical release source at
commit `03e996320ebb887112fb2a15c6f27936e5f124a0`; no substitute release is used.
This adds a source-build/download cost that must be included in clean-build timing.

Compose forwards both salts, requires dependency readiness, and enables durable
Redis AOF for new/recreated Redis containers. Existing Redis must be migrated with
its data intact before recreation; changing its Compose declaration alone does
not make the already running instance persistent.

The real Day 2 credential must be supplied through an ignored `.env` or process
environment. Do not paste it into reports. Test-only credentials are not official
signature evidence. Appium, media ingest/composition and Phase 2 audit handlers
are not implemented by this foundation.

## Verification

Run `sh scripts/test-integration.sh`. Both the script and Jest setup refuse a
database name other than `nascon_test`. This runs DB-backed regressions without
rewriting coverage declarations. Full dependency health is tested separately.

Run `python3 -m unittest discover -s tests/selenium -p test_grid_infrastructure.py`
for three simultaneous real Chrome/Chrome/Firefox sessions against API health.
This is infrastructure verification, not guest-player acceptance.

## Accepted scope for subsequent phases

- Phase 3: athlete profiles, career statistics and concurrency-safe follow/unfollow.
- Phase 4: signed invites, mute/spotlight/kick, actual recording, MinIO VOD upload
  and archive indexing, alongside ingest/composition/handoff.
- Phase 5: native Android real-time notifications.
- Phase 6: verify erasure across PostgreSQL, tokens, queue records and private
  cache references. Tests accompany every implementation phase.
