# Android delivery — 17 October 2026

Native Kotlin / XML, package `com.example.nasconsports`. Existing Gradle module retained. No new runtime dependencies.

## Run

1. Start the existing Docker stack with its externally supplied DAY1_SALT.
2. Connect and unlock the Android phone; accept USB debugging.
3. Run `/home/moiz/Android/Sdk/platform-tools/adb reverse tcp:8080 tcp:8080`.
4. Install `mobile/release/app-release.apk` (when release build completes).
5. Server defaults to `http://127.0.0.1:8080`. Emulator uses `http://10.0.2.2:8080`. Settings saves the URL and tests `/health`.
6. Sign in using the five specified demo emails and `Nascon@26`.
7. Organizer Home → Prepare competition demonstration, once. This authenticated, idempotent setup preserves existing purchases and edits. It adds a tenant-scoped cup, completed/live/upcoming matches and roster without changing the audit reset baseline.

## Journeys implemented

- Standings → select league → match → score and event timeline; active match polls at 12-second intervals while visible.
- Store → product → size/quantity → check stock → cart → optional WELCOME10 → mock payment → server PDF rendered using PdfRenderer. Stable order ID retained for retries, with saved receipt and cart.
- Manager → Club & roster → Manage player → transfer/loan/release → confirm → actual server result. Organizer → match → start/append configured event/correct event with a reason.
- Cached bootstrap, standings and score responses in SQLite. Actions stored durably with UTC timestamp, action ID, base version and nested Section 5.14 payload. Manual sync saves applied/rejected/duplicate results and server versions. Queues are scoped to server and account.
- Remembered accounts are encrypted using Android Keystore. To switch accounts offline, first sign into both accounts online, then sign out and choose Open remembered account offline with the correct email/password. Only unexpired remembered sessions open. Settings → Work offline forces cached screens and queued actions. Disable it before Sync.

## Security and build

New mobile scoring, roster and offline routes authenticate JWT, enforce roles, tenant and manager source club. Graded audit routes remain unchanged. JWT session and remembered-account vault use Keystore AES-GCM; backup is disabled. No salt or private key is in the app.

`./gradlew :app:assembleDebug --offline` builds with cached dependencies. Release signing uses NASCON_SIGNING_STORE and NASCON_SIGNING_PASSWORD environment variables. The local signing keystore/password live outside the repository under a mode-700 directory; they are never committed. A signed artifact is committed so installing does not require signing secrets.

## Evidence at 14:44 PKT checkpoint

- Debug build: passed (16s, then 26s after offline account support).
- Existing backend suite: 52 tests / 6 suites passed after mobile/demo route changes.
- Live backend HTTP verification: organizer login, authenticated idempotent demo preparation, three fixture statuses and four roster players confirmed.
- Physical device: Infinix was detected and reverse forwarding succeeded. It disconnected during debug APK installation; installation and UI journeys remain UNVERIFIED pending reconnection.
- Release: build running at checkpoint; signature and installation remain UNVERIFIED.

Do not infer device success from compilation. Updated rehearsal evidence follows below when available.
