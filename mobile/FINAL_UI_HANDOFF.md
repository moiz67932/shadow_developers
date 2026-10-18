# Android source handoff — final compilation required

Testing agent: Android source is ready for final compilation. No Gradle build or device validation was performed by this implementation session. Resolve the new `com.google.zxing:core:3.5.3` dependency, compile, then run the navigation/device checks below. Backend, Docker, PostgreSQL and QA files were not edited by this session.

## Screen coverage

All 17 Stitch screen concepts now have native Android source counterparts. New: registration, VOD archive, native VOD player and branded splash. Added an organizer/admin operations dashboard, bundled camera QR decoder, roster player detail and club profile. Existing fixtures, scoring, standings, catalogue, cart, checkout, signed receipt and offline queue logic is retained.

Retrieved all 17 screen screenshots and HTML from Stitch project 8772982244908829561 for source review. References were downloaded to /tmp/nascon-stitch. Two VOD cover images are bundled under drawable-nodpi; fonts, crests and sports imagery reuse existing resources. Screen fidelity has been reviewed from source; rendered fidelity is NOT verified.

## Native screen status

| Stitch design | Android implementation and behavior |
|---|---|
| Splash | SplashActivity, XML branded launch, routes to session restoration |
| Sign In | XML form, demo presets, real login, encrypted remembered offline account |
| Sign Up | XML validation, existing registration API, normal session persistence and Home |
| Home | Existing bootstrap-backed dashboard, direct live/archive/studio/admin entry points |
| Fixtures | Existing fixture cards and scoring routes; real league filter chips |
| Match Center | Existing scoring/timeline plus live player and QR routes |
| Standings | Existing server table and league selection |
| Store | Existing product tiles, product/cart/checkout/receipt |
| User Profile | XML identity, role-aware operation tiles, settings and sign out |
| Athlete Profile | XML overview/fixture/replay tabs, real follow/unfollow and recordings |
| Solo Broadcast | Native studio workspace, real key/publisher/record controls |
| Stream Keys | Encrypted owner-scoped keys, reveal/hide/copy, generate/rotate/revoke |
| Co-stream Lobby | Registered athlete selection, layout validation, create/join/open room |
| Host Room | Server participants, host/admin guards, invite, mute/unmute, spotlight/remove, start/stop |
| QR Scanner | Bundled native camera decoder, permission handling, flashlight, pasted-link fallback |
| Spectator Player | XML video stage, server scoreboard, merchandise, chat/stats tabs, chat polling/posting |
| VOD Archive | Search/filter/bookmarks, actual context catalogue, native replay player |

## Connected contracts

- POST /api/v1/register, /api/v1/login; GET /api/v1/bootstrap.
- GET /web/api/context/:id for live context and the available VOD catalogue; GET /api/v1/vods/:id/play for native MP4 playback.
- POST /api/v1/streams/key; POST /api/v1/streams/:id/record; existing MediaMTX publisher/HLS endpoints.
- POST /audit/v1/costream/session and existing /api/v1/costream/:id/{join,invite,start,stop,control}.
- GET /api/v1/players/:id/profile; PUT /api/v1/players/:id/follow; GET /api/v1/chat/:room; POST /api/v1/chat.
- Existing /q/:code redirects resolve once to validated native entity destinations; existing QR inspect/export routes preserved.
- Existing scoring, standings, inventory, checkout and offline mutation contracts unchanged.

## Exact files touched by this implementation

Paths relative to mobile/:

- app/build.gradle.kts (ZXing core dependency)
- app/src/main/AndroidManifest.xml
- app/src/main/java/com/example/nasconsports/MainActivity.kt
- app/src/main/java/com/example/nasconsports/CreatorScreens.kt
- app/src/main/java/com/example/nasconsports/RosterScreens.kt
- app/src/main/java/com/example/nasconsports/SportsScreens.kt
- app/src/main/java/com/example/nasconsports/StoreScreens.kt
- app/src/main/java/com/example/nasconsports/AuthScreens.kt
- app/src/main/java/com/example/nasconsports/SplashActivity.kt
- app/src/main/java/com/example/nasconsports/VodScreens.kt
- app/src/main/java/com/example/nasconsports/NavigationScreens.kt
- app/src/main/java/com/example/nasconsports/QrScannerActivity.kt
- app/src/main/java/com/example/nasconsports/QrNavigation.kt
- app/src/main/java/com/example/nasconsports/CreatorWorkspaces.kt
- app/src/main/java/com/example/nasconsports/LiveArenaScreen.kt
- app/src/main/res/values/arena_ui.xml
- app/src/main/res/drawable/bg_arena_glow.xml
- app/src/main/res/drawable/bg_video_scrim.xml
- app/src/main/res/drawable/ic_play_circle.xml
- app/src/main/res/drawable/qr_viewfinder.xml
- app/src/main/res/drawable-nodpi/vod_arena_cover.jpg
- app/src/main/res/drawable-nodpi/vod_replay_cover.jpg
- app/src/main/res/layout/activity_main.xml
- app/src/main/res/layout/activity_splash.xml
- app/src/main/res/layout/screen_sign_in.xml
- app/src/main/res/layout/screen_sign_up.xml
- app/src/main/res/layout/screen_vod_archive.xml
- app/src/main/res/layout/item_vod_card.xml
- app/src/main/res/layout/screen_vod_player.xml
- app/src/main/res/layout/view_arena_hero.xml
- app/src/main/res/layout/item_arena_action.xml
- app/src/main/res/layout/screen_admin_dashboard.xml
- app/src/main/res/layout/screen_profile.xml
- app/src/main/res/layout/screen_qr_scanner.xml
- app/src/main/res/layout/screen_live_arena.xml
- app/src/main/res/layout/item_chat_message.xml
- app/src/main/res/layout/item_live_merch.xml
- app/src/main/res/layout/screen_creator_workspace.xml
- app/src/main/res/layout/item_arena_panel.xml
- app/src/main/res/layout/screen_athlete_profile.xml
- FINAL_UI_HANDOFF.md

## Verification and remaining limits

XML parsing and Kotlin local resource-reference checks passed; git diff --check passed before handoff. This is source verification only. Final compilation and all device flows remain for the testing session.

Check: Sign In/Home; registration/Home and logout/Sign In; fixtures/match; standings; product/cart/checkout/receipt; club/roster/player/transfer; live/chat/merchandise; studio/publisher; lobby/host/invite; camera and short-QR entity routing; athlete/follow/replay; VOD filters/bookmarks/playback; settings/offline/queue/sync. Check Back and rotation on nested product, roster, athlete and replay routes.

All newly displayed buttons have handlers. API-dependent controls require server success and appropriate permissions. Unsupported design controls (invented bitrate/viewer metrics, pending invitation requests, auto-admit, unrestricted role registration) are deliberately omitted. The server only registers viewer accounts. Catalogue metadata does not expose duration, view counts, full-match/highlight categories or a global pagination endpoint; filters reflect actual athlete/co-stream metadata and local bookmarks. VOD cover artwork is illustrative. VOD videos are not downloaded for offline playback.

Broadcast camera publishing still uses the existing self-hosted MediaMTX WebView publisher rather than a bundled native encoder. The controls around it are native. Camera/microphone permissions and HTTPS/loopback media reachability must be checked on device. Co-stream creation retains the existing audit contract because no protected creation endpoint is provided. Admin dashboard exposes existing operations, not unsupported tenant/user administration. Live arena scoreboard loads from context; the existing dedicated Day2LiveActivity retains SSE score updates and notifications.
