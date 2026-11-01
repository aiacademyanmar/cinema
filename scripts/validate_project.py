#!/usr/bin/env python3
"""Dependency-free AAII CINEMA source/catalog preflight (not a substitute for APK compilation)."""
from pathlib import Path
from urllib.parse import urlparse
from collections import Counter
from xml.etree import ElementTree
import json, sys, re

root = Path(__file__).resolve().parents[1]
json_path = root/'app/src/main/assets/catalog.json'
s = json.loads(json_path.read_text(encoding='utf-8'))
assert isinstance(s, list), 'catalog must be an array'
required = {'id','title','subtitle','category','genre','url','kind','accent','badge','language','rights','featured'}
ids=set(); links=set(); category=Counter(); errors=[]
for idx,r in enumerate(s):
    missing=required-r.keys()
    if missing: errors.append(f'{idx}: missing {sorted(missing)}'); continue
    if r['id'] in ids: errors.append(f'{idx}: repeated ID: {r["id"]}')
    ids.add(r['id'])
    if not isinstance(r['featured'],bool): errors.append(f'{idx}: featured must be boolean')
    if r['kind'] not in ('link','stream','youtube','playlist_search'): errors.append(f'{idx}: bad kind {r["kind"]}')
    parsed=urlparse(r['url'])
    if parsed.scheme!='https' or not parsed.netloc: errors.append(f'{idx}: invalid HTTPS URL')
    if r['kind']=='youtube':
        if parsed.netloc not in ('www.youtube.com','youtube.com') or parsed.path != '/embed': errors.append(f'{idx}: invalid YouTube embed URL')
        if r.get('channelUrl','').startswith('https://www.youtube.com/') is False: errors.append(f'{idx}: YouTube channel URL missing')
        if r['category'] not in ('YouTube','Shorts'): errors.append(f'{idx}: YouTube embedded item must be in YouTube or Shorts category')
        if 'YouTube' not in r['rights']: errors.append(f'{idx}: YouTube rights/source note missing')
    if r['kind']=='playlist_search':
        if parsed.netloc != 'www.youtube.com' or parsed.path != '/results': errors.append(f'{idx}: invalid YouTube playlist discovery URL')
        if r.get('playlistStatus') != 'discovery_search': errors.append(f'{idx}: search must be labeled discovery_search')
    if r['category']=='Telegram':
        if parsed.netloc not in ('t.me','telegram.me'): errors.append(f'{idx}: unsupported Telegram host')
        if r['url'] in links: errors.append(f'{idx}: duplicate Telegram link')
        links.add(r['url'])
        if r['kind']=='stream': errors.append(f'{idx}: Telegram playback must be external')
    if r['category'] in ('YouTube','Shorts','Live TV') and r['kind']=='stream':
        errors.append(f'{idx}: unofficial direct streaming disallowed')
    category[r['category']]+=1
assert len(s)>=1, 'catalog must contain at least one record'

assert len(links)==category['Telegram']
assert not any('/c/' in r['url'] for r in s if r['category']=='Telegram'), 'private Telegram links must not be bundled'
demo = next((r for r in s if r['id']=='open-film-bbb-01'), None)
assert demo is not None and demo['kind']=='stream', 'licensed demo stream missing'
assert 'CC BY 3.0' in demo['rights'] and 'Blender Foundation' in demo['credits'], 'demo attribution missing'
assert all(r.get('posterUrl','').startswith('https://') for r in s), 'poster artwork URL missing'
assert len(s) >= 120, 'V2.7 catalog should include 100 playlist/discovery records'
playlist_items=[r for r in s if r['id'].startswith('ytplaylist-')]
assert len(playlist_items)==100, f'Expected 100 playlist collection records, got {len(playlist_items)}'
assert len([r for r in playlist_items if r.get('playlistStatus')=='linked_unverified'])==20, 'Expected 20 sourced playlist URLs'
assert len([r for r in playlist_items if r.get('playlistStatus')=='discovery_search'])==80, 'Expected 80 clearly labeled playlist discovery searches'
assert not errors,'\n'.join(errors[:25])
for file in ('app/src/main/AndroidManifest.xml','app/src/main/res/drawable/cinema_logo.xml'):
    ElementTree.parse(root/file)
for f in ('app/src/main/assets/cinema_mark.b64',):
    assert (root/f).stat().st_size > 1000
src=(root/'app/src/main/java/asia/aaii/cinema/MainActivity.kt').read_text(encoding='utf-8')
for x in ('private fun CinemaApp()', '@Composable', 'ExoPlayer.Builder', 'fun toggle(', 'safeTelegramUrl', 'parseTelegramImport(', 'parseCsvRows(', 'rememberLauncherForActivityResult(', 'normalizeSearch(', 'readRecentIds(', 'readWatchProgress(', 'persistWatchProgress(', 'FavoriteGenreRail(', 'ContinueWatchingEmpty(', 'AsyncImage(', 'PlayerScreen(', 'initialPositionMs', 'Playback Settings', 'Retry Playback', 'setPlaybackSpeed(', 'LifecycleEventObserver', 'YouTubePlayerScreen(', 'cc_lang_pref', 'cc_load_policy', 'safeYouTubeEmbedUrl', 'setPreferredTextLanguage', 'RESIZE_MODE_ZOOM', 'ExploreNativeScreen(', 'ProfileNativeScreen(', 'BackHandler('):
    assert x in src, f'missing source feature: {x}'
assert 'versionName = "2.7"' in (root/'app/build.gradle.kts').read_text(encoding='utf-8')
assert 'versionCode = 27' in (root/'app/build.gradle.kts').read_text(encoding='utf-8')
template = (root/'TELEGRAM_IMPORT_TEMPLATE.csv').read_text(encoding='utf-8').splitlines()[0].strip().lower()
assert template == 'title,url,genre', 'Telegram CSV template header mismatch'
print('STATIC QA PASSED — V2.7')
print('Total records:',len(s))
print('Categories:',dict(category))
print('Unique Telegram reference URLs:',len(links))
print('Android manifest and vector XML parse: OK')
print('Brand images present: OK')
print('Kotlin source feature markers: OK')
print('NOTE: This test does NOT compile Android or test remote URLs/permissions.')