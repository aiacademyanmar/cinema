# AAII CINEMA V2.7 — YouTube Playlists 100 Collections

**Status:** Build-source branch; device playback and region availability NOT verified.
**Branch:** `v2.7-youtube-playlists-100`
**Android version:** 2.7 (versionCode 27)

## Playlist catalog scope

- **100 new discoverable playlist collection entries** (in addition to the existing 20 built-in catalog entries).
- **20 sourced playlist URLs** from third-party education indexes / a channel's public post. These have concrete YouTube playlist IDs, but individual availability, accurate title mapping and embedding permissions have NOT been independently verified.
- **80 playlist discovery-search entries** across five topics. These are prefilled YouTube search results filtered to Playlists, NOT verified pre-existing playlist IDs.
- Scope includes Myanmar Movie Recap, Myanmar Learning, English History, Sports, Science & Documentary, plus a sourced Myanmar Drama series playlist.
- Existing 15 YouTube channel upload references remain in the catalog; channel handle embeds may still require future account/channel-ID verification.

## Native YouTube playback

- Sourced YouTube playlist IDs use official YouTube embedded player URLs with `listType=playlist`.
- Discovery entries open YouTube search results externally and are never presented as directly playable playlist IDs.
- Myanmar subtitles requested by default using `cc_lang_pref=my`, `cc_load_policy=1`, and `hl=my`; only effective where a compatible caption track exists, and does not promise Burmese machine translation for every video.
- No video extraction, rehosting, unauthorized downloads, bypassing ads/DRM, or unlicensed use.

## Provenance and QA

Initial sample playlist IDs were sourced from:
- [Channel K Myanmar post with drama playlist](https://t.me/s/ChannelKMyanmar/3864)
- [PolyglotClub History Video Series](https://polyglotclub.com/wiki/Language/Multiple-languages/Culture/History-Video-Series)
- [Digital Education Website playlists](https://counselinghearts.com/digitaleducationwebsites/)
- [Dene Magna School](https://www.denemagna.co.uk/page/?pid=834&title=English)
- [Knowledgia video playlist citations](https://natokhd.com/video/how-old-is-england-actually/HwFJeEh-zic)
- [GCSE revision playlist resource](https://qaawyrdhistorytopics.wordpress.com/category/aqa-gcse/)

No YouTube Data API key has been supplied. The sourced 20 IDs have NOT been confirmed active through YouTube Data API; therefore this is a discovery/catalog build, not a claim of 100 verified, playable playlists.

## Run build

GitHub Actions > **Build AAII Cinema V2.7 Native APK** > Run workflow on `v2.7-youtube-playlists-100`.

Test categories, Burmese text, external playlist searches, sourced embedded playlist playback, captions, fullscreen/back and source rights on Samsung devices.
