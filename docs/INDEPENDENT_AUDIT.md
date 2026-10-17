# Independent implementation audit — 17 October 2026, 14:22–14:26 PKT

Scope: source inspection, official PDF reconciliation, backend suite rerun, isolated domain probes and offline Android debug build attempt. No application rewrite, dependency installation, commit or push. System clock was 14:22 at start, not the prompt's approximate 14:07.

## Completion calculations

These are transparent planning indices, not official marks or a count of every PDF assertion. One point means demonstrated implementation for that checklist item, half means partial, zero means missing/unverified. Hidden-grader correctness cannot be calculated from local coverage.

| Area | Calculation | Result |
|---|---|---|
| Infrastructure | 8/10: Node runtime, PostgreSQL, Redis, worker, migrations, baseline, health, declared cached build pass; object storage and independent clean-machine verification absent | 80% |
| API route presence | All 23 mandatory method/path patterns implemented (includes health, ping and seven probes) | 100% |
| API behavior maturity | Nine groups: sports 1, fixtures .5, league/mobile exposure .5, scoring .5, standings 1, checkout 1, roster .5, RBAC probes 1, offline .5; 6.5/9 | 72% |
| Backend security | JWT verification, probe policy, password hashing, parameterized SQL, rate limit, body/error controls = 6; schema completeness .5; generalized tenant enforcement .5; independent OWASP review 0; database-enforced immutable audit 0; 7/10 | 70% |
| Scalability/concurrency readiness | Stock race, duplicate-order race and score-append race pass; independent-match parallel writes, adversarial Swiss latency and grader-size sustained load unverified/missing; 3/6 | 50% |
| Combined backend index | Equal average of infrastructure 80, API behavior 72.22, security 70, scalability 50 | 68% |
| Android screens | 0 of 11 requested screen/flow units implemented | 0% |
| Android/backend integration | 0/5: network permission/config, API client, secure session, live screen bindings, device end-to-end demonstration | 0% |
| Android offline | 0/5: local DB cache, persistent action queue, reconnect sync, result display, force-close persistence test | 0% |
| Overall planning index | (36 × .6806 + 4 × .5 + 10 × 0)/50; offline given half for server implementation and zero for device work | 53% |

The assumed server/device 50:50 split in the last row is **our planning assumption**, not a published split of the four offline marks. Backend checklist weights are also diagnostic choices. None of these indices is a claim that 68% of hidden vectors will pass.

## Verified evidence

- `./run_tests.sh` rerun: **52 passed, 6 suites**, 28.774 seconds.
- Domain line coverage **100%**, statements **95.05%**, branches **89.47%**, functions **95.8%**. Collection is restricted to `src/domain/**/*.js`, not the entire application, middleware, workers or Android. Dense source lines inflate the usefulness of line-based reporting; no full-functional-completion inference is justified.
- PostgreSQL concurrency tests verify one sale among 20 buyers, zero remaining reservations, payment rollback, simultaneous identical-order replay and preserved scoring sequence numbers.
- Canonical HMAC, valid extracted PDF text, role probes and rate-limit recovery pass available tests.
- API/PostgreSQL/Redis healthy; worker running. No MinIO container.
- Independent probes reveal additional failures below; these are not in the passing suite.

## Android and judge readiness

Actual `MainActivity.kt` only sets `activity_main.xml`, whose only content is a centered **Hello World!** TextView. Manifest has no INTERNET permission. Dependencies include Material/AppCompat but no Retrofit/OkHttp/Room. Material3 theme exists as a template, not a functioning design system.

| Required UI | Actual implementation |
|---|---|
| Login | Missing |
| Fixtures | Missing |
| Match detail | Missing |
| Standings | Missing |
| Club profile | Missing |
| Store | Missing |
| Cart/checkout | Missing |
| Receipt | Missing |
| Roster management | Missing |
| Scoring/correction entry | Missing |
| Persistent connection/sync status | Missing |

Navigation, role-aware routes, loading/empty/error states, 48dp interactive targets, meaningful small-screen layout and all three judge tasks are therefore **not demonstrated**. No Room cache, pending queue, reconnect code or Keystore token implementation exists. minSdk 24 is not a violation: it includes Android API 29 devices.

SDK platform android-37.0 exists. A physical **Infinix X6853** is connected via SDK adb (not on PATH). No release signing configuration or required `mobile/release/app-release.apk` was found. Offline `:app:assembleDebug` **succeeded in 3m56s**, producing `mobile/app/build/outputs/apk/debug/app-debug.apk` at approximately 14:26. This builds only Hello World; no judge task works. Installation was not performed.

## Newly reproduced gaps and code risks

1. **Demo discovery blocker:** `createLeague` drops tenant_id, while `/api/v1/bootstrap` filters by tenant_id. A league created through the audit endpoint is invisible to organizer/manager/viewer bootstrap. Seeded leagues have no fixtures. Current handoff's simple 'create a league' instruction is insufficient.
2. **Identifier failure:** legal match ID `__proto__` passed schema but `match_init` returned 404 MATCH_NOT_FOUND. Ordinary JavaScript object dictionaries mishandle special property names. Similar dictionary reads deserve scrutiny.
3. **Schema failure:** `params.third_place_match: "false"` is accepted and creates TP rather than returning 400. Optional fixture parameters are not fully type-validated.
4. **Double-elimination ambiguity/risk:** after GF1 participants are known, GF2 participants remain null until GF1 is resolved, although the contract describes GF2 as having the same teams. Reproduced; confirm expected pre-result fields before claiming exact compliance.
5. **Offline initialization:** version lookup requires an existing match before calling the event service; a queued match_init cannot create a stream. Other scoring requests work in existing tested streams. Whether the grader includes init in offline actions remains unknown.
6. Deferred roster response stores old eligibility metadata but immediately changes registration; no consumer applies eligibility at match end. Historical loan return is implemented; autonomous scheduled return is absent.
7. All sports/matches/rosters/offline history share one JSONB row and its write lock. Correct small races pass, but unrelated matches serialize. No normalized foreign keys, database-enforced append-only event table or tenant database policy.
8. RBAC probes use fixed scenario IDs. They do not establish full dynamic tenant authorization. Audit mutation routes intentionally remain unauthenticated by contract; do not count this alone as a grader security violation.
9. Redis queue publication exists, but realtime WebSocket/SSE and product pinning do not. Polling can support a demo, not full realtime compliance.
10. Missing general scheduling conflicts, full match status lifecycle, seasons/divisions/hybrid admin, richer club data and broader store fulfillment/refunds remain broader FR gaps.

MinIO is optional/disabled in Compose and its retained binary URL previously returned 410. This is unresolved, not a compliant replacement. No repeated download was attempted. JWT secret falls back to process-local randomness, so set a persistent environment secret for demo stability. The audit found no use of prohibited managed BaaS or third-party fixture generators.

## Marks and submission risk

Official Part I: backend 36, offline 4, UI/HCI 10. Current app demonstrates **0/10 UI marks** and none of the device-side offline tasks. Server-side offline allocation is unpublished; do not assign a certain number.

Conditional score planning: if weighted hidden-vector pass rate were 50–80%, backend formula with valid coverage bonus gives `3.6 + 32.4 × rate` = **19.8–29.5/36**. This is a scenario range, not measured prediction. With the current empty UI, a practical planning band is approximately **20–30/50 before offline-script credit and penalties**, contingent on those hidden pass rates. No score is guaranteed.

MinIO/clean-machine noncompliance could jeopardize infrastructure assessment; the explicit non-reproducible-stack penalty is -10 when its failure definition is met, not automatically proven by this audit. Missed push windows cost -2 each. Local commit timestamps do not prove GitHub push times. Latest local commit inspected is 13:09; many later features remain uncommitted/untracked. Secure an approved push before 14:45 and final push by 15:05; implementation lock is 15:00.

## Urgency ranking

1. Installable signed artifact and first working device/API vertical slice.
2. Missing navigation and all judge-facing screens.
3. Empty fixture demo data and bootstrap tenant-discovery bug.
4. Judge task A: standings → match.
5. Judge task B: catalogue → checkout → actual PDF receipt.
6. Judge task C: roster move plus role-aware controls.
7. SQLite/Room cache, durable action queue, sync result display and airplane-mode rehearsal.
8. Commit/push deadline protection; work currently outside the last commit.
9. Missing MinIO and realtime transport; pursue approved artifact without blocking mobile.
10. Reproduced contract/schema edge bugs and deferred lifecycle; isolate small fixes after mobile works.

The updated ANDROID_HANDOFF.md contains timed tasks, exact target files and acceptance criteria. A completely polished 11-flow app in the remaining roughly 35 minutes is not a credible promise; prioritize the three actual judge journeys and installable delivery.
