import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { resetVitrWebStorage } from '../../src/music/resetWebPlayer.js';

const read = (path) => readFileSync(new URL(`../../${path}`, import.meta.url), 'utf8');

function fakeStorage(initial = {}) {
  const map = new Map(Object.entries(initial));
  return {
    get length() { return map.size; },
    key(index) { return [...map.keys()][index] ?? null; },
    getItem(key) { return map.has(key) ? map.get(key) : null; },
    setItem(key, value) { map.set(key, String(value)); },
    removeItem(key) { map.delete(key); },
    snapshot() { return Object.fromEntries(map); },
  };
}

test('nont.me keeps its standalone page while Vitr routes use the web player', () => {
  const main = read('src/main.jsx');
  assert.match(main, /isVitrWebLocation/);
  assert.match(main, /import\('\.\/App\.jsx'\)/);
  assert.match(main, /import\('\.\/music\/VitrWebApp\.tsx'\)/);
  assert.doesNotMatch(main, /backgroundAudioPlayer/);
});

test('playback uses the credential-free YouTube iframe API', () => {
  const loader = read('src/music/youtubeIframeApi.js');
  const app = read('src/music/VitrWebApp.tsx');

  assert.match(loader, /https:\/\/www\.youtube\.com\/iframe_api/);
  assert.match(app, /loadYouTubeIframeApi/);
  assert.doesNotMatch(app, /\/api\/audio-stream|\/api\/media-extract|EXTRACTOR_WORKER|yt-dlp/i);
  assert.match(app, /type Tab = 'home' \| 'search' \| 'library' \| 'settings'/);
});

test('reset clears only Vitr-owned browser state', () => {
  const storage = fakeStorage({
    'vitr.web.library.v1': '[]',
    'vitr.web.player.v1': '{}',
    'vitr.web.playlists.manual.v1': '[]',
    'nont.music.youtube.favorites.v1': '[]',
    'other.app.keep': 'yes',
  });

  const removed = resetVitrWebStorage(storage);
  assert.equal(removed.length, 4);
  assert.deepEqual(storage.snapshot(), { 'other.app.keep': 'yes' });
});

test('settings reset reloads the current Vitr route', () => {
  const app = read('src/music/VitrWebApp.tsx');
  assert.match(app, /Reset VITR Web/);
  assert.match(app, /resetVitrWebStorage\(localStorage\)/);
  assert.match(app, /window\.location\.reload\(\)/);
  assert.doesNotMatch(app, /window\.location\.replace\('\/'\)/);
});


test('web player keeps Search, Library and Settings headers sticky and half-transparent', () => {
  const app = read('src/music/VitrWebApp.tsx');
  const css = read('src/music/vitr-web.css');
  assert.match(app, /vitr069-sticky-search/);
  assert.match(app, /Library[\s\S]*vitr069-sticky-title|vitr069-sticky-title[\s\S]*Library/);
  assert.match(app, /Settings[\s\S]*vitr069-sticky-title|vitr069-sticky-title[\s\S]*Settings/);
  assert.match(css, /\.vitr069-sticky-search[\s\S]*position:sticky/);
  assert.match(css, /\.vitr069-search-bar\.vitr-search-glass[\s\S]*rgba\(11,18,30,\.50\)/);
  assert.match(css, /\.vitr069-sticky-title[\s\S]*rgba\(5,8,17,\.50\)/);
});

test('web player layout is fluid across desktop and compact windows', () => {
  const css = read('src/music/vitr-web.css');
  assert.match(css, /--vitr-sidebar-width:clamp\(/);
  assert.match(css, /width:calc\(100% - var\(--vitr-sidebar-width\)\)/);
  assert.match(css, /@media\(max-width:1100px\) and \(min-width:901px\)/);
  assert.match(css, /grid-template-columns:repeat\(auto-fit/);
  assert.match(css, /overflow-x:clip/);
});


test('web player base container does not break sticky headers with overflow clipping', () => {
  const css = read('src/music/music.css');
  assert.match(css, /\.vitr-app\{[^}]*overflow:visible/);
  assert.doesNotMatch(css, /\.vitr-app\{[^}]*overflow:hidden/);
});


test('web player exposes v0.1.0 and a real clear-search control', () => {
  const app = read('src/music/VitrWebApp.tsx');
  assert.match(app, /VITR_WEB_VERSION = '0\.1\.0'/);
  assert.match(app, /aria-label="Clear search"/);
  assert.match(app, /function clearSearch\(\)/);
  assert.match(app, /searchRequestRef\.current \+= 1/);
  assert.match(app, /https:\/\/github\.com\/bloodvitr\/vitr/);
});

test('web and desktop use only the supplied PNG icon asset', () => {
  const app = read('src/music/VitrWebApp.tsx');
  const index = read('index.html');
  const landing = read('src/App.jsx');
  const desktopPackage = read('../desktop/package.json');
  assert.match(app, /\/vitr-icon\.png/);
  assert.match(index, /\/vitr-icon\.png/);
  assert.match(landing, /\/vitr-icon\.png/);
  assert.match(desktopPackage, /tauri icon web\/vitr-icon\.png/);
  assert.doesNotMatch(app + index + landing + desktopPackage, /vitr-icon\.svg/);
});


test('source adapter cannot overwrite the canonical 0.1.0 version', () => {
  const adapter = read('scripts/adapt-sources.mjs');
  assert.match(adapter, /const RELEASE_VERSION = '0\.1\.0'/);
  assert.match(adapter, /version:\s*RELEASE_VERSION/);
  assert.doesNotMatch(adapter, /version:\s*parseVitrVersion\(gradleText\)/);
});


test('Now Playing scales artwork against both viewport width and height', () => {
  const css = read('src/music/vitr-web.css');
  assert.match(css, /calc\(100dvh - 340px\)/);
  assert.match(css, /@media\(max-height:720px\)/);
  assert.match(css, /@media\(max-height:560px\)/);
  assert.match(css, /grid-template-columns:repeat\(5,minmax\(44px,1fr\)\)/);
});

test('Settings does not duplicate playback controls already present in the player', () => {
  const app = read('src/music/VitrWebApp.tsx');
  const settings = app.slice(app.indexOf('<h2>Settings</h2>'), app.indexOf('</section>', app.indexOf('<h2>Settings</h2>')));
  assert.doesNotMatch(settings, /Volume|Mute|Unmute|Shuffle:|Repeat:/);
  assert.match(settings, /Appearance:/);
  assert.match(settings, /Reset VITR Web/);
});
