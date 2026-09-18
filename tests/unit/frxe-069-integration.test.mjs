import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';

const main = await readFile(new URL('../../src/main.jsx', import.meta.url), 'utf8');
let app = '';
try { app = await readFile(new URL('../../src/music/MusicApp069.tsx', import.meta.url), 'utf8'); } catch {}

test('VITR boots the dedicated 0.6.9 composition app', () => {
  assert.match(main, /MusicApp069\.tsx/);
  assert.match(app, /VITR_WEB_VERSION\s*=\s*['"]0\.6\.9['"]/);
});

test('0.6.9 composition wires discovery taxonomy and playlists', () => {
  assert.match(app, /from ['"]\.\/recommendations\.js['"]/);
  assert.match(app, /from ['"]\.\/searchTaxonomy\.js['"]/);
  assert.match(app, /from ['"]\.\/playlists\.js['"]/);
  assert.match(app, /Top Result/);
  assert.match(app, />Songs</);
  assert.match(app, />Artists</);
  assert.match(app, />Genres</);
  assert.match(app, /Related Mixes/);
  assert.match(app, /Your Playlists/);
  assert.match(app, /VITR Mixes/);
  assert.match(app, /Add to playlist/);
});

test('0.6.9 composition uses credential-free browser playback without the retired relay', () => {
  assert.match(app, /loadYouTubeIframeApi/);
  assert.doesNotMatch(app, /\/api\/audio-stream|EXTRACTOR_WORKER|backgroundAudioPlayer/);
  assert.match(app, /This track does not allow embedded web playback/);
});
