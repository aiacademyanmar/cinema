# AAII CINEMA — V2.3 QA and Build Status

**Date:** 2026-11-01 (Asia/Yangon)  
**Package:** `asia.aaii.cinema`  
**Version:** 2.3 (versionCode 23)  
**Branch:** `v2.3-development`

## V2.3 implementation

- Retained the V2.2 native Compose theme, branding, home layout and fixed six-item footer.
- Expanded the curated YouTube / short-film directory from 10 to 17 entries.
- Added Android document-picker import for CSV and JSON public Telegram references.
- Added duplicate detection, a 500-record per-file cap, a 1 MB file-size cap, and rejected / duplicate / imported counts.
- Private `t.me/c/` and Telegram invite links remain excluded.
- Improved Unicode-normalized multi-term search over title, subtitle, genre, language and category.
- Updated Android version to 2.3 / versionCode 23 and CI artifact labels.

## Source audit

The user-supplied Movie Channel Hub HTML rechecked for this release contains 372 Telegram URL fields: 369 private `t.me/c/` URL records (367 unique private URLs), 3 malformed / placeholder values, and no valid public Telegram channel links. None were imported into the app catalog.

The previously referenced larger archive is not merged into this repository branch; this release does not claim that all archive records are present.

## Static QA

- Catalog schema, unique IDs, HTTPS URLs and source-policy checks.
- No private Telegram path in bundled catalog.
- Import helper markers and CSV template header.
- Android manifest and vector XML parse.
- Brand image source retained.

## Pending verification

- GitHub Actions Gradle compilation for this branch.
- APK SHA-256 and artifact publication.
- Samsung install and runtime QA (not possible from repository source alone).
- Real CSV / JSON import on a phone, including private-link rejection.
- Burmese / English search, 360dp and 412dp layout, navigation and persistence.

## GitHub

`https://github.com/aiacademyanmar/cinema/tree/v2.3-development`
