# AAII CINEMA V2.7 — FINAL QA REPORT (Code/CI)

**Checked date:** 2026-11-01
**Branch:** v2.7-youtube-playlists-100
**Scope:** GitHub build, source and catalog checks. No Android device emulator or physical Samsung run is available in this environment.

## Confirmed before this QA patch
- GitHub Actions build #41286317626: SUCCESS, APK artifact AAII-CINEMA-V2.7-DEBUG-APK (ZIP size 19,291,452 bytes).
- Catalog JSON parsed: 120 entries; 20 cited but live-unverified YouTube Playlist ID entries; 80 playlist discovery searches; 20 older baseline entries.
- Native app code included the preferred Burmese captions query (`cc_lang_pref=my&cc_load_policy=1&hl=my`).
- Two sourced YouTube playlist pages were indexed with recognizable title strings: Crash Course U.S. History and သုခမြိုင်. This is NOT a playback or caption verification.

## QA patch in this commit
- Redirect 15 earlier unverified YouTube `user_uploads` channel-handle entries to official channel URLs (external link) to avoid falsely claiming working in-app embedded playback.
- Keep internal YouTube embed only for 20 explicit `PL...` playlist ID entries.
- Add stricter CI catalog checks for verified URL *shape*, category coverage, counts and caption defaults.
- Use real `https://aaii.asia/` base origin for WebView content.
- Add `English Learning` to genre navigation for English-language Crash Course Economics.
- Keep an external 'Open Playlist on YouTube' fallback action for playlist entries.

## QA that is still pending
- 20 real playlist IDs' up-to-date availability, accurate title mapping, embed permissions and per-country geoblocking.
- WebView video playback and YouTube error 153 from a Samsung phone.
- Captions `my` availability: preferred language parameter does NOT generate or guarantee Burmese captions.
- WebChromeClient fullscreen behavior, orientation changes, Back button and pause/resume.
- Internet disconnected state, fallback links and device crash/ANR monitoring.
- 360dp and 412dp responsive layout and Burmese font rendering.
- Actual licensed content rights review; no bypassing YouTube streaming controls.

## Result
Source/CI success applies to the successful pre-patch APK, but it does not constitute functional playback QA. A new Build must pass after the QA patch. Runtime/contents must be verified on a physical device before production release.
