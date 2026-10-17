# NaSCon 2026 Multi-Sport League Platform

## Start

```sh
DAY1_SALT=your-team-salt docker compose up -d --build
curl http://localhost:8080/health
./run_tests.sh
```

The API is on port 8080. Copy `.env.example` to `.env` only for local overrides; never commit `DAY1_SALT`.

## Current verification

`DAY1_SALT=demo-salt docker compose up -d --build` was verified for API, PostgreSQL, Redis and worker. `/health` returned `200 {"status":"ok"}` and `/audit/v1/reset` seeded the baseline. The historical MinIO image was removed upstream after the event specification was issued; it is retained in the `object-storage` profile pending an organizer cached artifact.

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
