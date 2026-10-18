# Architecture decisions

1. Node 20 Alpine is pinned in the application Docker image for clean-machine compatibility.
2. Express and Zod provide a compact, auditable HTTP validation boundary.
3. PostgreSQL is the durable source of truth; checkout locks inventory rows.
4. Redis plus BullMQ is used for approved internal asynchronous work.
5. Sports are stored as JSON configuration so domain behavior is not selected by sport name.
6. Event records use append-only streams and HMAC signatures from `DAY1_SALT`.
7. Custom signed JWTs will enforce five-role tenant-scoped policy checks.
8. MinIO is designed as self-hosted object storage; its archived upstream artifact needs organizer cache restoration.

## Day 2 foundation decisions

- Keep the modular monolith and existing Day 1 persistence; new hot workflows use
  dedicated relational tables and database uniqueness constraints.
- Separate account sessions from fixed grading-probe tokens. Persist actual
  ownership on accounts; never infer the acting account from its role alone.
- Read salts only from the environment. Use a stable configured JWT secret, with a
  domain-separated environment-salt fallback for restart continuity.
- Preserve the historical MinIO release by building its verified official source
  commit when both image and binary distribution are unavailable. Bundle the
  compressed, checksum-verified executable and AGPL license so default startup
  does not repeat the multi-minute source build. Keep the source recipe available.
- Use prescribed Selenium 4.25.0 and MediaMTX 1.9.1. Infrastructure readiness does
  not prove media ingest/composition or guest UI behavior.
