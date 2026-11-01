# AAII CINEMA V2.4 — QA / Build Report

**Date:** 2026-11-01  
**Version:** 2.4 / versionCode 24  
**Branch:** v2.4-development

## Implemented
- Coil-powered poster / thumbnail gallery with generic placeholder art.
- Local Recently Viewed history.
- Continue Watching for in-app stream entries; playback position persisted every five seconds and restored on resume.
- Favorite genre/category persistence.
- Media3 subtitle/audio language preference settings and Fit / Fill / Zoom ratio.
- Big Buck Bunny sample stream for player/resume QA, with Blender Foundation CC BY 3.0 attribution.
- Existing V2.3 Telegram CSV/JSON import, Unicode search and external-link safety retained.

## Content notes
- 18 built-in catalog records.
- One built-in native stream: Big Buck Bunny (CC BY 3.0; attribution required; retain full credits).
- Remaining YouTube / TV / Telegram records are external references only.
- Generic Unsplash images are placeholders, not official posters.

## Static QA
- Catalog JSON schema / unique IDs / HTTPS URLs.
- All catalog entries include a poster artwork URL.
- Big Buck Bunny stream attribution present.
- Private Telegram path exclusion.
- Kotlin feature marker checks.
- Android XML parsing.
- Version name / code checks.

## Pending
- Gradle compile on GitHub Actions.
- APK artifact / SHA256 verification.
- Samsung install, layout, player resume, track settings and persistence tests.
- Poster URL availability on a real device.
