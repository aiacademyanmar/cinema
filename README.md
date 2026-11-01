# AAII CINEMA — Native Android V2.3

**Status:** V2.3 development branch; GitHub Actions build and real-device QA are pending.  
**Package:** `asia.aaii.cinema`  
**Version:** 2.3 (versionCode 23)

## V2.3 changes

- Retains the V2.2 Kotlin + Jetpack Compose / Material 3 dark cinematic interface, logo, six-item footer and navigation.
- Expands the YouTube / short-film directory with National Geographic, Free Documentary, PBS Space Time, DUST, MC Movie Recap, Myanmar Tawyar Recaps and Move World By DeDe.
- Adds native Telegram **CSV / JSON bulk import** from Android document picker.
- CSV header: `title,url,genre`; accepted aliases include `name/link/category`.
- JSON accepts an array, an `items` array, a `links` array, or a single `title/url` object.
- Import safety: HTTPS public `t.me/channel` and `t.me/channel/123` references only; private `t.me/c/` paths, invite links, invalid URLs and duplicates are rejected. Maximum 500 records per import and 1 MB per file.
- Search normalizes Unicode text, punctuation and whitespace, then matches all query terms across title, description, genre, language and category.
- Imported links are stored locally in app preferences and open in Telegram / browser only. No video is copied, downloaded or internally streamed from Telegram.

## Source audit — user-supplied Movie Channel Hub HTML

The rechecked HTML supplied in the project contains **372 Telegram URL fields**. There are 369 private `t.me/c/` URL records (367 unique private URLs), 3 malformed / placeholder values, and no valid public Telegram channel links in that source. They are therefore not silently imported into the public catalog. Use the in-app CSV / JSON importer for verified public links.

The previously referenced larger full-project archive is not part of this repository branch; the current committed built-in catalog is a curated directory, not a claim that all prior archive records have been merged.

## Media and rights policy

- Official YouTube entries open the creator's channel page externally.
- Creator recap channels are directory references only; rights to third-party clips are not verified.
- Only the existing CC0 sample MP4 is eligible for the in-app demo player. Third-party movie / TV / Telegram links are external references.
- No downloading, rehosting, bypassing membership, removing ads, or rebroadcasting third-party content.
- Registration, subscription billing, offline movie downloads and live-TV feed verification are not implemented in this build.

## Build APK via GitHub Actions

1. Open [GitHub Actions](https://github.com/aiacademyanmar/cinema/actions).
2. Select **Build AAII Cinema V2.3 Native APK**.
3. Run the workflow on `v2.3-development`, or push to that branch.
4. Download `AAII-CINEMA-V2.3-DEBUG-APK` after a successful run.
5. Extract `app-debug.apk` and verify `APK_SHA256.txt`.

Requirements: Android SDK 35; JDK 17; Gradle 8.10.2; Kotlin 2.0.21; Compose BOM 2025.02.00; minSdk 26.

### Run static preflight

```bash
python3 scripts/validate_project.py
```

Static preflight checks catalog IDs and HTTPS URLs, rejects private Telegram paths in the built-in catalog, verifies the import feature markers and CSV template, and parses Android XML. It does not replace Gradle compilation or Samsung-device testing.

## Device QA still required

1. Install on Samsung Android 8+ (minSdk 26) and confirm launch, icon and layout.
2. Test 360dp and 412dp widths; check the six-item footer and Telegram import buttons.
3. Import a CSV and JSON file containing public test links; confirm added / duplicate / rejected counts.
4. Confirm `t.me/c/` links are rejected and never opened as in-app video.
5. Test Burmese and English multi-term search and genre filters.
6. Test Home / Explore / Detail / Player / Search / Live TV / YouTube / Telegram / My List / Profile navigation.
7. Restart and confirm My List and imported public links persist.
8. Verify external link failure messaging and player back behaviour.

## Source repository

`https://github.com/aiacademyanmar/cinema`  
Branch: `v2.3-development`
