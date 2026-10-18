# Android Day 2 creator checkpoint

Android-only delivery; backend routes/services and existing offline queue formats were not changed.

Entry: Home or Profile → Creator tools. Native chat/merchandise is also reachable from the existing deep-link live activity.

## Added working source flows

- Broadcast Studio: athlete identity, secure saved stream-key response, actual MediaMTX camera publisher, record request and live playback navigation.
- Stream Key Controls: authenticated generate/rotate/revoke, encrypted storage scoped to account/server, copy RTMP/WHIP endpoints.
- Co-stream Lobby: 1–4 unique athletes, session creation using existing `/audit/v1/costream/session`, invitation-token join, open existing session.
- Active Stream Director: real saved key status, publisher navigation, ingest copy, confirmed revoke/end action, chat/merchandise and match QR navigation. No invented bitrate or audience metrics.
- Match Telemetry: scanner/export navigation, actual score/status/version, Match Center/live-player navigation and deep-link copying.
- Host Room: production hero, real host/layout/status tiles, current server host/participants, composite start/stop, invite, mute/unmute, spotlight/kick; protected controls use `/api/v1/costream/*`.
- QR: compatible scanner intent, validated entity/deep-link/server-URL opening, dynamic metadata, short-link sharing, real PNG/SVG/PDF export URLs.
- Athlete directory/profile: cover imagery, real follower/match/VOD tiles, attributed career statistics, club/upcoming/VOD data; explicit idempotent follow/unfollow via PUT.
- Native Live Arena: inline HLS player, chat polling and authenticated moderated posting, merchandise → existing product/cart/checkout, VOD links.
- New reads use existing account/server-scoped cache. Creator writes reject forced offline mode and are not queued as Day 1 score/roster actions.

## Essential limitations

- Manifest XML parsing, lexical Kotlin delimiter checks, and `git diff --check` passed. Android compilation and device behavior are UNVERIFIED. No Gradle, APK build, dependency installation, or backend tests were run.
- Camera broadcast embeds MediaMTX's WebRTC publisher, not a bundled native encoder. Camera/mic require HTTPS or loopback and Android permission. Media ports must be reachable; USB requires forwarding 8889/8888 in addition to API 8080. WebRTC transport still needs a reachable media network path. Publisher URL accepts an HTTPS endpoint on the configured server host. No fake live state is shown.
- There is no bundled QR decoder. Scan launches an installed compatible ZXing scanner; otherwise the UI explains using the phone QR reader and pasted links. This does not complete the standalone native-camera scanner requirement.
- QR binary exports are opened using the device's external browser/viewer. The existing server generates QR payloads with its configured localhost origin; exported QR contents need backend deployment-origin support for another device. Native copied/shared short links use the app's configured server.
- Co-stream session creation currently uses the existing audit service because no authenticated creation route exists. Host actions use protected routes. Joining does not publish camera media automatically.
- Live Arena chat refreshes every two seconds while focused. Merchandise is the server-context catalogue; it is not represented as a new backend pinning feature. Score SSE/notifications remain in the existing dedicated player.
- Creator request/response checks were by source inspection, not live mutation calls. Backend work in the shared workspace was excluded from this checkpoint.

## Retrieved Stitch inventory

Project `8772982244908829561`: latest live listing returned 17 MOBILE designs, untruncated, on 2026-10-18. No generation requested. Retrieved Solo Broadcast Studio metadata as well as the complete screen listing. User-provided four-panel reference informed Host Room, Active Stream Director, Match Telemetry and Athlete Profile layouts; unsupported meter values and private messages were not fabricated.

Relevant design references:

| Design | Screen ID |
|---|---|
| Solo Broadcast Studio | `623786654c4d4a5b899fb2bdc759d06b` |
| Stream Key Management | `ab043111a3c6476ab6631944401b82d9` |
| Co-stream Host Room | `b3ee880c6e5a439ab42e7cb4ea7325f8` |
| Native QR Scanner | `8938138bfe444f81b52e197db43e67db` |
| Public Athlete Profile | `ac0e51bc00654483806ccf542d3d8771` |

Existing navy/cyan tokens, fonts, image assets, cards, fields, buttons and bottom navigation are reused. No HTML design was substituted for the working native screens.

## Exact changed-file list

1. `mobile/app/src/main/AndroidManifest.xml`
2. `mobile/app/src/main/java/com/example/nasconsports/ApiClient.kt`
3. `mobile/app/src/main/java/com/example/nasconsports/MainActivity.kt`
4. `mobile/app/src/main/java/com/example/nasconsports/Day2LiveActivity.kt`
5. `mobile/app/src/main/java/com/example/nasconsports/CreatorScreens.kt` (new)
6. `mobile/app/src/main/java/com/example/nasconsports/BroadcastActivity.kt` (new)
7. `docs/ANDROID_CREATOR_CHECKPOINT.md` (new)
