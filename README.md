# Drishyopot

Independent Android video client. Written from scratch (Kotlin + Jetpack Compose).

## Build
1. Open this folder in **Android Studio** (Ladybug 2024.2 or newer, JDK 17).
2. Let Gradle sync. If `gradlew` is missing, Android Studio uses the settings in
   `gradle/wrapper/gradle-wrapper.properties`; or run `gradle wrapper` once.
3. Run the `app` configuration on a device/emulator with Android 6.0+ (API 23).

## Phase status
- [x] Phase 1 – Foundation: theme, settings catalog (all toggles), backup/restore, diagnostics, About, EN + BN strings
- [x] Phase 2 – Native player (Media3), gestures, subtitle style, resize modes, info dialog
- [x] Phase 3 – Background playback, PiP, mini player, queue, autoplay
- [x] Phase 4 – SponsorBlock
- [x] Phase 5 – Download manager
- [x] Phase 6 – Browser, ad blocking, voice search, deep links
- [x] Phase 7 – Casting

## Tests
`./gradlew test` runs the JVM unit tests (gesture math, SponsorBlock parsing, settings backup).

## SponsorBlock note
SponsorBlock segments are keyed by YouTube video ID. Until the YouTube resolver (Phase 6) exists,
enter the 11-character video ID in the optional field on the Home screen to test skipping on a
direct media URL of the same video.

## YouTube support (Phase 6) – read this first
- Stream resolving uses **NewPipe Extractor** (GPLv3), pulled from JitPack. The version is pinned in
  `gradle/libs.versions.toml` (`newpipe`). YouTube changes often: if playback or resolving fails, bump
  that tag to the newest release at https://github.com/TeamNewPipe/NewPipeExtractor/releases and rebuild.
  Because the extractor is GPLv3, the combined app must be distributed under GPLv3 too.
- Blocking ads and downloading/streaming outside YouTube's own apps goes against YouTube's Terms of Service.
  Do not publish this build on Google Play.
- Video links open in the native player; everything else (home, search, channels, sign-in) is browsed in
  the WebView with ads stripped (`app/src/main/assets/browser/inject.js`).
- Not included in this phase: PoToken generation. If a video returns 403 errors, update the extractor first.

## Casting (Phase 7)
- Discovery uses mDNS (`_googlecast._tcp`); the session speaks Cast V2 over TLS to the Default Media Receiver.
- **Device certificates are not verified** (Google's device CA is not bundled). Use on trusted networks only.
- The phone runs a small HTTP proxy (`LocalStreamProxy`) so the TV never talks to YouTube directly.
  It tries a DASH manifest (H.264 + AAC, up to 1080p) first and falls back to a single combined file if the
  receiver rejects it.
- "Open on any device" serves a player page from the proxy to any browser on the same Wi-Fi. When only
  separate streams exist, that page loads dash.js 4.7.4 from cdnjs, so the other device needs internet.
- This has not been tested against real hardware. Check the diagnostic log (About → Save diagnostic logs,
  lines tagged `Cast`) if a device connects but does not play.

## Downloads (Phase 5)
- Files are fetched as 4 MiB blocks over 1–8 connections (Settings → Downloads); finished blocks are saved,
  so pause/resume and crashes lose at most a few blocks.
- Video + audio are joined with Android's `MediaMuxer` (no re-encoding): H.264/HEVC + AAC → `.mp4`,
  VP9/AV1 + Opus → `.webm`. Audio-only saves `.m4a` (or `.webm` for Opus). The result is checked
  (tracks present, duration within tolerance) before it is moved to Movies/Drishyopot or Music/Drishyopot.
  Subtitles and cover images go to Download/Drishyopot.
- Before Android 10 files are saved in the app's own external folder (no storage permission needed).
- Downloads wait when offline or, with "Wi-Fi only", on a metered network, and continue automatically.
- Playlists: up to 500 videos per batch; the options dialog applies one setting set to all of them.
- Update check: set `resValue("string", "update_repo", "owner/repo")` in `app/build.gradle.kts` to show
  "Check for updates" in About (uses that repository's latest GitHub release).

## Known gaps
- Translations: English and Bengali only. Add more with `app/src/main/res/values-<code>/strings.xml`.
- No PoToken generation; signed-in cookies are not passed to the native player or downloader, so
  age-restricted or members-only videos may not play or download.
- Nothing here has been compiled or run on a device by the author: expect a few fixes on first build.
