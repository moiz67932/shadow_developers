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

Local hotspot HTTP hosts are discovered from the laptop's private IPv4 interfaces at build time, or configured explicitly with `-PnasconDevHosts=192.168.1.42`. Network security remains deny-by-default for all other HTTP hosts. If changing to a different laptop address after building, rebuild with that explicit host or use USB forwarding / HTTPS.

## Rehearsal evidence through 14:49 PKT

- Updated backend suite: **56 / 56 passing, 7 suites**, including protected mobile scoring/compensation, manager source-club authorization, demo idempotence/reset isolation, and offline accepted/rejected/duplicate handling.
- Physical Infinix reconnected, debug installation succeeded and app launched. Actual organizer account/tenant appeared. Standings table showed Falcons played 1, won 1, differential +1 and 3 points, with qualification text.
- Physical scoring: `demo-live` went from Strikers 1–0 (version 2) to 2–0 (version 3) after submitting a score. A reasoned compensation returned it to 1–0 (version 4).
- Physical store: selected L variant, checked live stock and added to cart. Checkout/receipt rehearsal still running at this record.
- Real release variant built with local private signing key. `apksigner verify --verbose` reports **Verifies**, APK Signature Scheme v2 true, one signer. This is a release build, not a renamed debug APK.
- Release lint tasks are excluded by `build-release.sh` because the cached Material dependency references an uncached desktop lint artifact. Kotlin/resource compilation, optimization, packaging and signature verification still run. Full release lint remains unverified.
- Physical checkout completed at 14:49: order `7da27f75-a394-4320-a0ae-9132b38f1287`, L jersey ×1, **PKR 4,500.00**, status PAID. The actual server-generated PDF displayed in PdfRenderer and showed the same order/amount. Inventory was consumed normally and has not been reset.

The first optimized release failed at startup on the phone with an R8/Kotlin `IllegalAccessError` in lifecycle initialization. The inherited partial optimization configuration was disabled. A fresh **release** build (31 seconds) was generated and signature-verified, then installed for a new device rehearsal. This fixes the actual variant configuration; debug was not substituted or renamed.

## Signed artifact and current device status at 14:53 PKT

`mobile/release/app-release.apk` SHA-256:
`e0018166e02183d4dcade7a6a8b14707b0290032f549d134a52149fd9359186a`

- Package `com.example.nasconsports`, version 1.0, minSdk 24 (includes Android 10/API29), target/compile SDK37.
- INTERNET and ACCESS_NETWORK_STATE present; release is not marked debuggable.
- Exact corrected signed APK installed on the Infinix successfully. Manager signed in and Home showed the actual `club_manager` role and tenant. Club & roster navigation opened.
- USB disconnected again at approximately 14:52 before completing roster submission. Hardware was absent from both ADB and lsusb. Reconnection requested.
- Physical **completed transfer, airplane cached screens, queued score + roster, force-close persistence and sync remain UNVERIFIED** at this timestamp. Their implementation and backend tests must not be presented as a completed physical rehearsal.
- Evidence screenshots: [match score](qa/match-device.png), [actual paid PDF receipt](qa/receipt-device.png). These two screenshots are from debug rehearsal; the exact signed release launch/login was verified separately.

At 14:59, the physical signed-release manager rehearsal completed: Bilal Ahmed transfer was accepted, and a fresh backend bootstrap confirmed `demo-bilal` registered in `demo-titans` with registration_type `transfer`.

The user's final UI adjustment reduced only sign-in heading/field/button/dropdown sizes and spacing, retaining 48dp touch targets. The updated release built, verified and installed successfully. Current artifact SHA-256: `d65a85f9953f3ec150fbcbe26ab74fdd7a07be7716a4cbe73f11fb551838242b` (supersedes the earlier artifact hash). There is no sign-up route/screen in the existing backend/app; no unsupported account-registration flow was invented.
