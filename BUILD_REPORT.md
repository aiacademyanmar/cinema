# AAII CINEMA — V2.2 QA and Build Status

**Date:** 2026-11-01 (Asia/Yangon)  
**Package:** `asia.aaii.cinema`  
**Version:** 2.2 (versionCode 22)

## Verified offline
- Passed `python3 scripts/validate_project.py`.
- GitHub build seed contains 10 records so CI can compile and smoke-test the native shell. Full source ZIP retains the larger 979-record catalog (959 Telegram references); that full catalog has not yet been transferred into this repository due to connected Library storage limits.
- All catalog URLs use HTTPS; source entries are metadata and open in an external app unless explicitly classified as licensed/demo stream.
- Kotlin UI code updated with dedicated Search, improved Back behavior, Korean/English/Japanese rows, feedback for broken external links and an inline video error message.
- Android manifest and vector XML parsed; logo Base64 source is committed; workflow restores drawable resources before Gradle build.
- GitHub Actions includes SDK 35, Gradle 8.10.2, Java 17, APK artifact, SHA256, and failure log.

## Not verified
- **No Android SDK, Gradle executable or external dependency download access in the current environment**, so native compilation and device testing cannot be verified here.
- GitHub target repository exists and source commits have been pushed; workflow run still needs result verification.
- No external Telegram private-link access, movie rights, TV live stream playback, or subscription entitlements verified.

## QA to complete after APK generation
1. GitHub workflow `assembleDebug` succeeds, `app-debug.apk` is non-empty.
2. Samsung launch; branding and splash/icon correctly render.
3. Navigate Home/Explore/Detail/Player/Search/TV/YouTube/Telegram/My List/Profile.
4. Enter Burmese and English search terms; test genre filters.
5. Open external source in Telegram/browser; test unavailable URL handling.
6. Test authorized CC0 flower video and error/back behaviors.
7. Restart app and confirm My List and user-added public Telegram links persist.
8. Test 360dp and 412dp Android screens; verify buttons and footer are not cut off.
9. Confirm private `t.me/c` content is not downloaded or played in an internal player.
10. Confirm excluded providers and payments are absent.

## GitHub target
`https://github.com/aiacademyanmar/cinema` (main). Compilation not yet verified until workflow concludes.