# AAII CINEMA — Native Android V2.4

**Status:** V2.4 development branch; CI build and real-device QA pending.  
**Package:** `asia.aaii.cinema`  
**Version:** 2.4 (versionCode 24)

## V2.4 features
- Retains V2.3 dark cinematic Compose UI, AAII CINEMA branding, six-item bottom navigation and Telegram CSV/JSON import.
- Poster / thumbnail gallery using Coil Compose with generic cinematic artwork placeholders (not official movie posters).
- Recently Viewed rail (local-only; tracks catalog details opened).
- Continue Watching rail with local playback progress for native `kind=stream` catalog items.
- Playback position is saved every five seconds and restored when the user resumes.
- Favorite Categories stored locally and editable from Home / Profile.
- Media3 player settings: subtitle preference (Off / Myanmar / English / Japanese), audio language preference and screen ratio (Fit / Fill / Zoom). Track choices only work if the selected stream contains those tracks.
- Adds **Big Buck Bunny** as the player/resume QA sample. Copyright © 2008 Blender Foundation / www.bigbuckbunny.org; CC BY 3.0. Attribution required; keep the full credits roll. [Official license information](https://peach.blender.org/about/).
- Sample MP4: `https://storage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4`.

## Content / rights
- Poster artwork is generic Unsplash imagery and is not official film artwork.
- YouTube, TV and Telegram entries remain external references; they are not extracted, copied or rebroadcast.
- Big Buck Bunny is the only built-in native video stream in this release. Its attribution is shown in the catalog and player.
- Third-party movie/TV streams require documented distribution rights before inclusion.
- Subscription billing, user accounts, server sync and offline movie downloads are not implemented.

## Build
1. Open [GitHub Actions](https://github.com/aiacademyanmar/cinema/actions).
2. Select **Build AAII Cinema V2.4 Native APK**.
3. Run on branch `v2.4-development`.
4. Download `AAII-CINEMA-V2.4-DEBUG-APK` after success and verify the included SHA256.

Requirements: Android SDK 35; JDK 17; Gradle 8.10.2; Kotlin 2.0.21; Compose BOM 2025.02.00; Media3 1.5.1; Coil Compose 2.7.0; minSdk 26.

## Static preflight
```bash
python3 scripts/validate_project.py
```
Static preflight checks catalog schema, poster URLs, demo attribution, Kotlin feature markers, Android XML and version. It does not replace Gradle compilation, live URL checks or Samsung QA.

## Samsung QA checklist
- Install on Samsung Android 8+.
- Confirm poster images load and fallback art appears offline.
- Open catalog details and verify Recently Viewed.
- Play Big Buck Bunny, pause after about one minute, exit and resume from Continue Watching.
- Verify progress restore and completion cleanup after app restart.
- Test subtitle/audio where tracks exist; test Fit / Fill / Zoom.
- Add/remove favorite genres and verify persistence.
- Check 360dp / 412dp widths, Burmese text, footer, Back navigation and player release.
- Confirm external YouTube / Telegram / TV entries open externally only.

Repository: `https://github.com/aiacademyanmar/cinema`  
Branch: `v2.4-development`
