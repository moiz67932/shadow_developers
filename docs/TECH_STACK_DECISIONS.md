# Technical stack decisions

- **Node 20 Alpine in Docker:** specified runtime and reproducible host-independent builds. Host Node 22 is only used for local tooling.
- **Express + Zod:** small audited HTTP layer with strict schema boundaries.
- **PostgreSQL 16 + row locks:** relational source of truth and safe last-unit checkout.
- **Redis 7 + BullMQ:** approved lightweight queue for domain events.
- **MinIO:** required self-hosted object-storage service, using the booklet’s specified version.
- **Custom JWT:** satisfies the required five-role authorization model without managed identity infrastructure.
- **PDFKit:** server-side receipt PDF generation without a browser runtime.
- **Jest + Supertest:** JavaScript-native tests suitable for the official `run_tests.sh` gate.
