# NaSCon 2026 Multi-Sport League Platform

## Start

```sh
DAY1_SALT=your-team-salt docker compose up -d --build
curl http://localhost:8080/health
./run_tests.sh
```

The API is on port 8080. Copy `.env.example` to `.env` only for local overrides; never commit `DAY1_SALT`.

## Current verification

The API, PostgreSQL and Redis are healthy; the worker is running and an `order.paid` event was observed being consumed. Migrations and seeding run automatically. The Node 20 runtime image builds with `npm ci --omit=dev`.

All mandatory audit route patterns are implemented. **52 tests in 6 suites pass**, including actual PostgreSQL concurrency, PDF text extraction and JWT boundaries. See [the endpoint status table](docs/BACKEND_STATUS.md) for precise scope and known gaps. This is not a claim of complete Part I compliance.

MinIO is not running: Docker Hub, Quay and official binary retrieval failed in earlier checks. The `object-storage` profile currently contains an unavailable binary URL and does not build. An organizer-approved artifact is needed; the complete mandatory object-store startup requirement remains unresolved.

## Tests and coverage

Install development dependencies once with `cd backend && npm ci`, then run `./run_tests.sh` from the repository root. The script needs host Node/npm, Docker access, and the running default PostgreSQL service. It creates/reuses a separate database named `nascon_test`, runs unit and HTTP integration tests, and regenerates `COVERAGE.json` from Jest measurements. For custom PostgreSQL credentials, supply a reachable `DATABASE_URL` ending in `/nascon_test`. Never point the suite at application data.

The current measured line percentage is 100 for each required domain; overall domain branch coverage is 89.47%. Dense source lines make line coverage a weaker signal than the branch and behavior tests. Generated detailed reports are in `backend/coverage/` and are not committed.

## API and configuration

The full audit contract is in the repository's official Part I PDF; route verification is listed in `docs/BACKEND_STATUS.md`. Audit routes are intentionally unauthenticated except the seven RBAC probes. All errors contain `error.code`, `error.message` and object `error.details`.

Use `POST /api/v1/login` with email/password for a signed JWT, then `GET /api/v1/bootstrap` with `Authorization: Bearer <token>` for tenant-filtered data. `/api/v1/*` is limited to 100 requests per rolling 10 seconds; audit and health routes are exempt. Set `JWT_SECRET` for sessions that survive API restart; otherwise the API generates a process-local random signing key. `DAY1_SALT` is required by the server and Compose, is never returned by an API, and must not be committed.

## Demo accounts

| Role | Email | Password |
|---|---|---|
| Platform Super-Admin | admin@nascon.test | Nascon@26 |
| League Organizer | organizer@nascon.test | Nascon@26 |
| Club Manager | manager@nascon.test | Nascon@26 |
| Player | player@nascon.test | Nascon@26 |
| General Viewer | viewer@nascon.test | Nascon@26 |

## Android

The native Kotlin/XML package is `com.example.nasconsports`. Use `http://10.0.2.2:8080` in an emulator, or the laptop LAN address on a phone. The release APK has not yet been built.

Follow [the Android handoff](docs/ANDROID_HANDOFF.md) for API bodies, Room queue format, permissions, demo-data preparation and build steps. WebSocket/SSE transport remains missing; score polling is available. Full protected mobile mutation endpoints, venue conflict tools, broad store lifecycle and deferred roster lifecycle are remaining work detailed in the status document.
