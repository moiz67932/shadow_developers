# Requirements audit

Authoritative sources reviewed in full: `NaSCon 26 App Dev Part 1-1.pdf` (22 pages) and `Official_Participant_Rules_Booklet.pdf` (4 pages). Part I is binding if verbal guidance conflicts. The Rules Booklet says repositories were empty at 10:40, while Part I requires a public repository; the existing repository predates this task, so organizer clarification is required if that rule is enforced.

## Mandatory platform requirements

- Docker Compose must expose API port 8080 and start PostgreSQL 16, Redis 7, MinIO, API and worker from one `DAY1_SALT=<salt> docker compose up -d --build` command; health must be 200 inside 120 seconds.
- Use self-hosted open source infrastructure. Managed BaaS products, third party fixture generators, real payment gateways, and hard-coded sport/event behavior are prohibited.
- `/health`, all 14 listed audit routes, seven RBAC probe routes, and `/api/v1/ping` have literal request, response, status, validation and idempotency rules.
- Baseline reset must create exact sports, tenants, merchandise, RBAC scenario and five demo users from §5.5/§8.3.
- Signing is UTF-8 HMAC-SHA256 over `id|canonical-json`, keys sorted recursively, with lowercase hex output.
- Android API 29+ APK must be at `mobile/release/app-release.apk`, with configurable server, Keystore tokens, Room caching and persisted offline queue.

## Grading and penalties

- Backend: fixtures, scoring, standings, store, rosters and RBAC total 36 points; offline persistence 4; mobile UI/HCI 10.
- Penalties: unreproducible Docker -10, hard-coded sport logic -8, managed BaaS -10, missed GitHub checkpoint -2 each (maximum -8).
- `COVERAGE.json` must state real per-area figures; a declaration more than 2 points over measured coverage loses the bonus.

## Current implementation status

- Implemented and tested locally: all mandatory audit route patterns, all four fixture algorithms, replay scoring and compensation, standings, transactional checkout and valid signed PDFs, roster operations/history, RBAC probes, offline synchronization, demo login and tenant-filtered bootstrap. Detailed evidence and limitations: `BACKEND_STATUS.md`.
- Partial: deferred roster eligibility has no automatic match-end consumer; exact Swiss backtracking is not performance-proven for adversarial maximum fields; the JSONB domain state is not a fully normalized relational schema; broad FR administration features remain incomplete.
- Missing: mandatory MinIO runtime, realtime transport, Android Room/Keystore/UI and APK. Consequently P0 and overall Part I compliance are not fully complete despite healthy API/database/cache/worker services.
- Measured verification: 52 passing tests in 6 suites; all six required area line percentages generated in root COVERAGE.json. Branch coverage is 89.47%; line coverage does not establish all hidden cases.

## Source-document conflicts requiring organizer clarification

- Part I §1.3 says public repository and desk registration; booklet §4 says private repository with evaluator invitation.
- Part I §5.2 specifies organizer Ubuntu 22.04, 4 vCPU/8 GB; booklet §3 describes GitHub ubuntu-latest/16 GB. Implementation targets the stricter Part I environment.
- Part I totals 50 points (36 backend, 4 offline, 10 UI); booklet's Day 1 schedule labels 40 points.
- Venue lists and tie-breaking rules differ. Part I-specific endpoint and scoring rules govern our implementation; confirm administrative discrepancies with organizers.
- Neither failed registry requests nor the broken binary archive proves permanent upstream removal. No replacement MinIO artifact has organizer approval in this session.
