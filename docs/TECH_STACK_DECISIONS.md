# Technical stack decisions

- **Node 20 Alpine in Docker:** specified runtime and reproducible host-independent builds. Host Node 22 is only used for local tooling.
- **Express + Zod:** small audited HTTP layer with strict schema boundaries.
- **PostgreSQL 16 + row locks:** relational source of truth and safe last-unit checkout.
- **Redis 7 + BullMQ:** approved lightweight queue for domain events.
- **MinIO:** required self-hosted object-storage service, using the booklet’s specified version.
- **Custom JWT:** satisfies the required five-role authorization model without managed identity infrastructure.
- **PDFKit:** server-side receipt PDF generation without a browser runtime.
- **Jest + Supertest:** JavaScript-native tests suitable for the official `run_tests.sh` gate.

Implementation constraint: preserve the existing PostgreSQL JSONB domain-state storage for this sprint. State mutation now uses a transaction on one pooled connection and locks the state row, preventing lost appends. Inventory and orders remain separate relational tables with row locks and advisory locks for duplicate order IDs. This is deliberately documented as incomplete relational normalization, not presented as a fully normalized sports schema.

The database outbox persists order events in the same transaction as inventory/order changes. A worker publishes them to BullMQ with a stable event job ID, marks publication, and consumes the jobs. This avoids dropping events when Redis is temporarily unavailable.
