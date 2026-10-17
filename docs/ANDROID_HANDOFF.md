# Immediate Android handoff

Existing project: native Kotlin/XML, namespace and application ID `com.example.nasconsports`. No Gradle project replacement or mobile file change was made.

## URGENT AUDIT OVERRIDE — 17 Oct, approximately 14:25 PKT

The actual app is still **Hello World**. No listed screens, network permission, API client, secure token storage, cache or sync queue exists. Do not infer mobile completion from backend tests. Read `INDEPENDENT_AUDIT.md` for source evidence and new backend gaps. System time was already 14:22 when the audit started; lock is 15:00.

### Critical demo-data correction

`POST /audit/v1/leagues` does not persist a tenant_id. `/api/v1/bootstrap` hides these leagues from non-admin users. Seeded leagues have zero fixtures. Before building screens, add a small tenant-scoped demo-data provisioner or correct demo league binding in `backend/src/services/state.js` and `backend/src/routes/mobile.js`. Preserve exact audit reset data; use an explicit demo-preparation operation rather than inventing baseline match IDs. Demo roster setup is separate from bootstrap players: bind the mobile roster screen to the actual configured squad/player IDs.

### Deadline plan (stretch estimates; no promise of complete polish)

All Kotlin paths below are under `mobile/app/src/main/java/com/example/nasconsports/`; resources under `mobile/app/src/main/res/`.

| Window PKT | Priority / task | Exact files to create or modify | Dependencies / score opportunity | Acceptance |
|---|---|---|---|---|
| 14:26–14:30 (4m) | P0: device login + health + server setting | `mobile/app/src/main/AndroidManifest.xml`, `MainActivity.kt`, new `ApiClient.kt`, `SessionStore.kt`, `res/layout/activity_main.xml` | Existing backend login; foundation for all 10 UI points | Phone signs in, shows role; wrong password explained; JWT encrypted with Keystore |
| 14:30–14:36 (6m) | P0: bottom navigation, standings/fixture/match, minimal club | `MainActivity.kt`, new `SportsRepository.kt`, `res/layout/activity_main.xml`, `res/values/colors.xml`, `res/values/strings.xml`; targeted backend demo provisioning above | Login + nonempty tenant-bound data; judge A, navigation/role/readability | Organizer finds standings and opens a real match; Back works |
| 14:36–14:42 (6m) | P0: store, variant/cart, checkout and receipt | new `StoreView.kt`, `ReceiptView.kt`, `ApiClient.kt`, `MainActivity.kt` | Checkout API; judge B | Real purchase, loading/error state, PDF rendered via Android PdfRenderer or installed viewer |
| 14:42–14:44 (2m) | P0: checkpoint build/commit review | `mobile/app/build.gradle.kts`, `mobile/release/app-release.apk` | Build/signing; protects last checkpoint (-2 if missed) | Installable signed artifact; request approved push before 14:45 |
| 14:44–14:49 (5m) | P0: roster and scoring forms | new `RosterView.kt`, `ScoringView.kt`, `ApiClient.kt` | Configured squads and in-progress match; judge C | Manager transfers a real player; organizer scores and compensates |
| 14:49–14:55 (6m) | P0: durable cache/queue + sync banner | new `LocalStore.kt`, `SyncManager.kt`, `SportsRepository.kt`, `MainActivity.kt` | Up to 4 offline points shared with backend; UI feedback | Airplane-mode cached fixtures/standings/club; queued actions survive force-close; Sync shows each outcome |
| 14:55–15:00 (5m) | P0: device rehearsal, final signed APK | layouts/theme, `mobile/app/build.gradle.kts`, `mobile/release/app-release.apk` | All three judge tasks; delivery gate | Test A/B/C, rotate, back, 360dp width; verify signature and install exact artifact |
| 15:00–15:05 | Submission only | Existing verified files | Implementation locked | Approved final commit/push by deadline; no new implementation |

Use cached dependencies. Room is preferred; if its compiler/runtime cannot resolve immediately, Android SQLiteOpenHelper is explicitly allowed by §8.4 and avoids blocking the offline feature. A manual visible Sync button is allowed; WorkManager is optional. HttpURLConnection on a background executor is a permissible emergency fallback if Retrofit/OkHttp are uncached. Do not perform network I/O on the UI thread.

One consistent Material theme, labelled navigation, at least 48dp controls, readable text, scrollable forms, busy/empty/error states and confirmation on roster actions are the minimum polish. These requirements earn across the five HCI categories; there are no published per-screen marks.

Physical device found: Infinix X6853, serial `1154137468006369`. ADB path: `/home/moiz/Android/Sdk/platform-tools/adb`. SDK platform android-37.0 is installed; current minSdk 24 includes API29 and is not itself a violation. Release signing is not configured. Do not rename an unsigned APK and claim it installs. Keep private signing material out of Git.

Offline debug build completed successfully at approximately 14:26, in 3m56s: `mobile/app/build/outputs/apk/debug/app-debug.apk`. It is the Hello World template, not a competition-ready app. No release artifact exists and no installation was performed during this audit. The Gradle cache is now warm.

### Paste into a fresh implementation session if necessary

“Continue `/home/moiz/shadow_developers`. Read `docs/INDEPENDENT_AUDIT.md` and this urgent handoff. Android is the untouched Hello World template, backend has 52 passing tests but known gaps. Implement the three judge journeys immediately in native Kotlin/XML; preserve package `com.example.nasconsports`, current Gradle setup and existing work. Fix only demo-data discovery needed by the UI. Add persistent SQLite/Room cache and pending-action queue, Keystore sessions, visible Sync results and a verified signed APK. Prefer cached dependencies; do not spend remaining time refactoring backend or retrying MinIO. Check current clock and build status. No commit/push without permission.”

## Connection contract

- Emulator base URL: `http://10.0.2.2:8080/`.
- Physical phone: `http://<laptop-LAN-IP>:8080/` over the team's hotspot; campus Wi-Fi may isolate devices.
- Add INTERNET permission, configure development HTTP cleartext access, and provide a base-URL setting.
- GET `health` → `{"status":"ok"}`.
- POST `api/v1/login` with `{"email":"organizer@nascon.test","password":"Nascon@26"}` → token, token_type, expires_in, user with role and tenant_id.
- Store the JWT using Android Keystore; send `Authorization: Bearer <token>` for bootstrap and RBAC probes.
- GET `api/v1/bootstrap` returns leagues, clubs, players, products and fixtures filtered to the token tenant.
- All five account emails and the shared demo password are in README.

## Next implementation order

1. Retrofit/OkHttp client, JSON error envelope parsing, configurable address and role-aware login/home.
2. Fixtures, standings, club and store views, plus Room caches. Baseline contains scenario leagues and clubs but no fixture games; create a demonstration league with POST `audit/v1/leagues` and preserve returned match IDs.
3. Score and standings fetches: GET `audit/v1/score/{match_id}`, GET `audit/v1/standings/{league_id}`. Poll for now; server realtime transport is missing.
4. Roster setup/demo data followed by transfer/loan/release. Setup uses POST `audit/v1/roster/setup`. Player audit-entry count is the roster version.
5. Checkout with mock payment and a receipt view: decode receipt.pdf_base64 into app-private storage.
6. Room pending-actions table containing device_id, stable action_id, type, queued_at UTC seconds, base_version and the exact nested payload described in PDF §5.14.
7. Sync sorted actions using POST `audit/v1/offline-sync`. Server returns results in request order, processed_order, server_version, signature and error_code. Show applied/rejected reasons before clearing queue. Persist rejected result history separately.
8. WorkManager or explicit Sync button, online/offline/syncing state, force-close/reopen persistence check.
9. Build and verify an installable APK at `mobile/release/app-release.apk`.

## Important integration limits

Audit endpoints intentionally require no JWT except RBAC probes; this is mandated for the grader. Android may use them during the demo, but a production-grade protected mutation layer remains missing. The bootstrap provides catalogue/scenario data, not a completed sports UI. No APK exists from this work.

Never embed DAY1_SALT in mobile. Receipt and event signatures are generated by the backend.
