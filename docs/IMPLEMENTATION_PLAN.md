# Implementation plan

1. **P0 foundation (current):** Compose, PostgreSQL migration/seed, Redis worker, MinIO, health/reset, environment handling and reproducible lockfile.
2. **Scoring and fixtures:** complete dynamic event projection, all bracket formats, Swiss matching, standings and edge-vector tests.
3. **Operations:** roster state/audit, transactional checkout receipt, RBAC JWT probes, offline rebasing and queues.
4. **Mobile/finalization:** Retrofit/Room/Keystore client, installable release APK, measured coverage, traceability and runbook.

Source mapping: fixtures live in `backend/src/domain/fixtures`; scoring in `domain/scoring`; standings in `domain/standings`; roster in `domain/rosters`; checkout in `routes/audit.js` then `domain/store`; RBAC in `domain/rbac`; migrations in `database/migrations`.
