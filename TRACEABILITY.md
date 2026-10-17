# Traceability matrix

| Requirement | Implementation | Tests | Status |
|---|---|---|---|
| FR-1.1 dynamic sport configuration | `routes/audit.js`, `services/state.js` | pending API test | partial |
| FR-1.3 round robin scheduling | `domain/fixtures/roundRobin.js` | `tests/fixtures.test.js` | partial |
| FR-1.4 standings | planned `domain/standings` | pending | unimplemented |
| FR-2.2 roster history | planned `domain/rosters` | pending | unimplemented |
| FR-2.3 safe store | `routes/audit.js`, migration | pending concurrency test | partial |
| FR-5.1 RBAC | planned `domain/rbac` | pending | unimplemented |
| NFR-3 stock safety | `routes/audit.js` row locks | pending integration test | partial |
| NFR-4 offline sync | planned `domain/offline` | pending | unimplemented |
| NFR-5 input/rate security | `app.js`, middleware | pending | partial |
| NFR-7 reproducibility | Compose/Dockerfile/migrate/seed | pending Docker verification | partial |
