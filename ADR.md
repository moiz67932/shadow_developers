# Architecture decisions

1. Node 20 Alpine is pinned in the application Docker image for clean-machine compatibility.
2. Express and Zod provide a compact, auditable HTTP validation boundary.
3. PostgreSQL is the durable source of truth; checkout locks inventory rows.
4. Redis plus BullMQ is used for approved internal asynchronous work.
5. Sports are stored as JSON configuration so domain behavior is not selected by sport name.
6. Event records use append-only streams and HMAC signatures from `DAY1_SALT`.
7. Custom signed JWTs will enforce five-role tenant-scoped policy checks.
8. MinIO is designed as self-hosted object storage; its archived upstream artifact needs organizer cache restoration.
