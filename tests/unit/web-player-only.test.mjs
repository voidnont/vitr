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
  assert.match(main, /import\('\.\/music\/MusicApp069\.tsx'\)/);
  assert.doesNotMatch(main, /backgroundAudioPlayer/);
});

test('playback uses the credential-free YouTube iframe API', () => {
  const loader = read('src/music/youtubeIframeApi.js');
  const app = read('src/music/MusicApp069.tsx');

  assert.match(loader, /https:\/\/www\.youtube\.com\/iframe_api/);
  assert.match(app, /loadYouTubeIframeApi/);
  assert.doesNotMatch(app, /\/api\/audio-stream|\/api\/media-extract|EXTRACTOR_WORKER|yt-dlp/i);
  assert.match(app, /type Tab = 'home' \| 'search' \| 'library' \| 'settings'/);
});

test('reset clears only Vitr-owned browser state', () => {
  const storage = fakeStorage({
    'vitr.web.library.v1': '[]',
    'vitr.web.player.v1': '{}',
    'frxe.web.playlists.manual.v1': '[]',
    'nont.music.youtube.favorites.v1': '[]',
    'other.app.keep': 'yes',
  });

  const removed = resetVitrWebStorage(storage);
  assert.equal(removed.length, 4);
  assert.deepEqual(storage.snapshot(), { 'other.app.keep': 'yes' });
});

test('settings reset reloads the current Vitr route', () => {
  const app = read('src/music/MusicApp069.tsx');
  assert.match(app, /Reset VITR Web/);
  assert.match(app, /resetVitrWebStorage\(localStorage\)/);
  assert.match(app, /window\.location\.reload\(\)/);
  assert.doesNotMatch(app, /window\.location\.replace\('\/'\)/);
});
