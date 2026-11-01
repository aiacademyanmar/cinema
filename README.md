# AAII CINEMA — Native Android V2.2 (APK Build Candidate)

**Status:** Validated Android Studio / GitHub Actions source; APK **not** compiled or installed in the current offline environment. Do not claim an APK exists until GitHub Actions publishes `AAII-CINEMA-V2.2-DEBUG-APK`.

## Features

- Kotlin + Jetpack Compose / Material 3 dark cinematic UI, approved AAII CINEMA blue-purple-gold icon.
- Ten-screen navigation path: Home, Explore, Detail, licensed demo Player, Search, Live TV, YouTube, Telegram, My List, Profile.
- Fixed six-item footer for Home / Explore / TV / Telegram / My List / Profile; Search reachable from the header.
- Korea / English / Japan Series rails, content filters, a persistent My List, and an informative playback error state.
- Offline **catalog** of 979 records: 959 *unverified* Telegram private-channel/post references, plus other directory entries.
- User-added **public** Telegram links only; private invite links excluded from manual import.
- Only the CC0 sample MP4 is sent to Media3 for in-app playback. Third-party channels and other videos open external official/source pages; no downloading, republishing or circumventing access.
- Profile is a Free Preview — registration, subscription billing, offline movie downloads and TV stream verification are **not** implemented.
- Previously excluded Mahar / Pyone Play / ShweStream / iStream / Sein Htay / MRTV4 Official are not reintroduced.

## Build APK via GitHub Actions

1. Use the selected existing repository: `https://github.com/aiacademyanmar/cinema`.
2. Project source is committed at the repository root including hidden `.github` workflow.
3. Open **Actions → Build AAII Cinema V2.2 Native APK → Run workflow**.
4. After a successful workflow, download the `AAII-CINEMA-V2.2-DEBUG-APK` artifact and extract `app-debug.apk`.
5. Verify `APK_SHA256.txt` before Samsung install. This is a *debug-signed* test build, not a Play Store release.

Requirements: Android SDK 35; JDK 17; Android Gradle Plugin 8.7.3; Gradle 8.10.2; Kotlin 2.0.21; Compose BOM 2025.02.00; minSdk 26.

### Run preflight locally

```bash
python3 scripts/validate_project.py
```

The preflight validates catalog schema/IDs, Telegram count and URL shape, media source policy, manifest XML and required brand images. It **does not substitute for** full Gradle compilation, SDK resolution or a real-phone UX test.

### Troubleshooting

If Actions fails, download `AAII-CINEMA-V2.2-BUILD-LOG`, locate the first compile exception, and correct the source before rerunning. The GitHub workflow uploads the actual APK **only when** the compilation succeeds.

## Source provenance

Telegram reference list was parsed from the HTML the user provided and has not been authenticated against private Telegram channels. `t.me/c/...` links generally require the viewer to be a channel member. No source videos are included in the archive and external URLs may stop working.

## Status of prior design mockups

The 10 mobile UI designs are artistic *references*, not screenshots of a compiled APK. Compose implements the same information architecture but is not a pixel-for-pixel copy; no real subscription, watch history or offline download is claimed.