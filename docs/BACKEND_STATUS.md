# Backend checkpoint — 2026-10-17

Status definitions: **passing** means implemented and exercised by the available local tests, not proof of every hidden grader vector. **Partial** identifies known remaining behavior. All routes below use the shared error envelope.

## Mandatory endpoints

| Method | Route | Status / evidence |
|---|---|---|
| GET | /health | Passing: HTTP and Node 20 Docker checks |
| POST | /audit/v1/reset | Passing: repeated resets, stock overrides, demo accounts |
| POST | /audit/v1/sports | Passing: all three model schemas, arbitrary event config, invalid configuration |
| POST | /audit/v1/fixtures | Passing available tests: four formats, byes, graph routing, GF2, withdrawals, Swiss optimal-cost oracle |
| POST | /audit/v1/leagues | Passing: validation, match-stream creation, standings integration |
| POST | /audit/v1/events | Passing: transitions, limits, compensation, signed immutable event objects, concurrent append |
| GET | /audit/v1/score/:match_id | Passing: replay projection, all model types and compensation |
| GET | /audit/v1/standings/:league_id | Passing: mini-league restart, lots, NRR, qualification |
| POST | /audit/v1/roster/setup | Passing: configured squads, players, defaults |
| POST | /audit/v1/roster/transfer | Partial beyond tested contract: transfer/loan/release, precedence and deferred response pass; no automatic match-end integration for deferred eligibility |
| GET | /audit/v1/roster/history/:player_id | Passing: inclusive loan dates, return written once, historical squad |
| POST | /audit/v1/checkout | Passing: parallel stock, rollback, replay, discount, signed valid PDF |
| GET | /audit/v1/inventory/:variant_id | Passing: stock and reserved counts, missing variant |
| POST | /audit/v1/rbac-matrix | Passing: five signed roles |
| POST | /audit/v1/rbac/tenants | Passing: super-admin only |
| POST | /audit/v1/rbac/leagues/:league_id/fixtures | Passing: ownership/role policy |
| POST | /audit/v1/rbac/clubs/:club_id/roster | Passing: ownership/role policy |
| POST | /audit/v1/rbac/clubs/:club_id/products | Passing: ownership/role policy |
| PATCH | /audit/v1/rbac/players/:player_id | Passing: manager's player or self |
| GET | /audit/v1/rbac/clubs/:club_id | Passing: same-tenant reads |
| GET | /audit/v1/rbac/platform/config | Passing: super-admin only, invalid-token rejection |
| POST | /audit/v1/offline-sync | Passing: chronological processing, request-order results, stale rebasing, future rejection, persistent duplicates |
| GET | /api/v1/ping | Passing: 100 per rolling 10s, Retry-After, recovery after 11s |

## Android support added

| Route | Status |
|---|---|
| POST /api/v1/login | Passing: five demo accounts, invalid credentials |
| GET /api/v1/bootstrap | Passing: JWT required; tenant-filtered leagues/clubs/players/products/fixtures |

## Actual verification

- `./run_tests.sh`: **52 tests in 6 suites passed**, including tests using a separate real PostgreSQL database.
- Last-unit burst: one 201, nineteen 409; stock 0; reserved 0.
- Two concurrent requests with the same order ID: one creation, one identical replay.
- Twenty concurrent scoring requests: twenty different persisted sequence numbers, no lost events.
- PDFKit receipt parsed with `pdftotext`: order ID and `PKR 4500.00` verified.
- Full measured domain line coverage: six required areas 100%; overall statement coverage 95.05%, branch coverage 89.47%. Dense source lines mean line coverage alone does not describe branch completeness.
- Node 20 container smoke: health, double reset, all fixture formats, five demo logins, valid receipt.
- API, PostgreSQL and Redis healthy; worker running.
- Worker log confirms `order.paid` consumed; PostgreSQL outbox `published_at` verified.
- Manifest and lockfile dependency ranges match. Existing dependency versions reused.

## Known limitations and unverified cases

1. **MinIO unresolved:** earlier Docker Hub pull denied, Quay returned 401 and official binary archive returned 410. These observations do not independently establish permanent upstream deletion. The `object-storage` profile is disabled and its current binary URL does not build. Organizer-provided approved artifact/location is needed. Full mandatory Docker compliance is therefore **not complete**.
2. Swiss uses exact backtracking with memoization, connected-component pruning and admissible cost bounds. Correctness has local oracle tests; adversarial 64-team histories under the five-second deadline remain unverified.
3. Roster deferred responses preserve old-match eligibility metadata, but there is no match-end service to consume it automatically. Loan returns are materialized on history calls with `as_of`; no wall-clock loan-return scheduler exists.
4. Scoring/roster state remains in the existing PostgreSQL JSONB document, serialized with a row lock. Event objects are append-only in service behavior, but database triggers do not enforce immutability and a fully normalized relational sports schema is not implemented.
5. General match rescheduling, venue/player overlap detection, postponed/abandoned/cancelled lifecycle, promotion/relegation and hybrid tournament administration are outside the implemented audit facade.
6. Store covers the mandatory checkout/payment cases and durable queue publication; catalogue administration, scheduled prices, refunds, packing/shipping and product pinning are not implemented.
7. WebSocket/SSE updates are missing. Android can poll score endpoints initially.
8. Audit mutation routes remain unauthenticated as expressly required. They are grading/demo interfaces; a full authenticated mobile mutation API is not implemented.
9. JWT secret is environment supplied when set; otherwise a process-local random secret is generated. Set JWT_SECRET to keep sessions valid across restarts.
10. Tests do not establish full OWASP compliance, every error combination or all hidden vectors. Clean organizer-machine reproduction and performance bounds have not been independently verified.
11. No Android integration code or APK has been built in this increment.

Backend feature expansion stops here for the Android handoff.
