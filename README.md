# NaSCon 2026 Multi-Sport League Platform

## Start

```sh
DAY1_SALT=your-team-salt DAY2_SALT=your-day2-salt docker compose up -d --build
curl http://localhost:8080/health
./run_tests.sh
```

The API is on port 8080. Supply both organizer salts using environment variables or an ignored `.env`; never commit either credential. Strict readiness checks PostgreSQL, Redis, baseline data, MediaMTX and MinIO.

## Current verification

The API, PostgreSQL and Redis are healthy; the worker is running and an `order.paid` event was observed being consumed. Migrations and seeding run automatically. The Node 20 runtime image builds with `npm ci --omit=dev`.

The existing Day 1 audit routes are implemented. Phase 1 adds account-derived sessions, guest-write guards, relational Phase II foundations and baseline seeds. Phase II business endpoint handlers are not implemented yet. See [Phase 1 scope and verification](docs/PHASE1_FOUNDATION.md) and [the Day 1 endpoint status table](docs/BACKEND_STATUS.md).

MinIO is a required default service. Its Dockerfile builds the exact historical upstream release from a pinned source commit because the prescribed image and binary are unavailable. Cold-build timing and actual service readiness must be verified; cached images are not proof of reproducibility. Selenium is pinned to 4.25.0 with two Chrome nodes and one Firefox node.

## Tests and coverage

Install development dependencies once with `cd backend && npm ci`, then run `sh scripts/test-integration.sh` from the repository root. It creates/reuses only `nascon_test` and runs unit/HTTP integration tests without rewriting coverage declarations. Jest also rejects any other integration database name. For custom PostgreSQL credentials, supply a reachable `DATABASE_URL` ending in `/nascon_test`. `./run_tests.sh` additionally regenerates historical Day 1 coverage. Never point tests at application data.

Existing `COVERAGE.json` and `backend/coverage/` are historical Day 1 measurements, not fresh Phase II evidence. Phase II coverage will be generated from its implemented modules. Dense source lines make line coverage a weaker signal than branch and behavior tests.

## API and configuration

The full audit contract is in the repository's official Part I PDF; route verification is listed in `docs/BACKEND_STATUS.md`. Audit routes are intentionally unauthenticated except the seven RBAC probes. All errors contain `error.code`, `error.message` and object `error.details`.

Use `POST /api/v1/login` with email/password for an account-derived JWT, then `GET /api/v1/bootstrap` with `Authorization: Bearer <token>` for tenant-filtered data. `/api/v1/*` is limited to 100 requests per rolling 10 seconds; audit and health routes are exempt. Set a stable `JWT_SECRET`; the fallback is domain-separated from environment `DAY1_SALT` and survives restart. Fixed Day 1 probe tokens cannot authenticate production sessions. Existing role-only sessions require re-login after upgrading.

## Demo accounts

| Role | Email | Password |
|---|---|---|
| Platform Super-Admin | admin@nascon.test | Nascon@26 |
| League Organizer | organizer@nascon.test | Nascon@26 |
| Club Manager | manager@nascon.test | Nascon@26 |
| Player | player@nascon.test | Nascon@26 |
| General Viewer | viewer@nascon.test | Nascon@26 |

## Android

The native Kotlin/XML package is `com.example.nasconsports`. Use `http://10.0.2.2:8080` in an emulator, or the laptop LAN address on a phone. The tracked release APK is the Day 1 artifact; it does not demonstrate Phase II mobile features.

Follow [the Android handoff](docs/ANDROID_HANDOFF.md) for API bodies, Room queue format, permissions, demo-data preparation and build steps. WebSocket/SSE transport remains missing; score polling is available. Full protected mobile mutation endpoints, venue conflict tools, broad store lifecycle and deferred roster lifecycle are remaining work detailed in the status document.

### Android competition app

The existing native Kotlin/XML app now includes role-aware login, fixtures, standings, match timeline/scoring, club/roster management, merchandise checkout and an in-app server PDF receipt. It uses the real Docker API and persistent SQLite cache/action history. See [Android delivery and verified rehearsal results](docs/ANDROID_DELIVERY.md).

For the USB phone, run `adb reverse tcp:8080 tcp:8080`, install `mobile/release/app-release.apk`, and use `http://127.0.0.1:8080` in Server settings. Organizer Home has an authenticated, idempotent **Prepare competition demonstration** action. It never modifies the grader reset baseline or replenishes purchased stock.

Build debug with `cd mobile && ./gradlew :app:assembleDebug --offline`. Build a genuinely signed release with `mobile/build-release.sh`; the signing key stays outside the repository. Local hotspot hosts are discovered at build time or explicitly supplied using `-PnasconDevHosts=<laptop-IP>`. Other cleartext hosts are denied. HTTPS addresses remain configurable without a rebuild.
