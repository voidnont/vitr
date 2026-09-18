import test from 'node:test';
import assert from 'node:assert/strict';
import { existsSync, readFileSync } from 'node:fs';

const read = (path) => readFileSync(new URL(`../../${path}`, import.meta.url), 'utf8');

test('public brand is Vitr by Blood', () => {
  const index = read('index.html');
  const app = read('src/App.jsx');
  const main = read('src/main.jsx');
  const music = read('src/music/MusicApp069.tsx');
  const readme = read('README.md');

  for (const source of [index, app, main, music, readme]) {
    assert.doesNotMatch(source, /\bNont\b|\bNONT\b|\bFRXE\b|\bFrxe\b/);
  }

  assert.match(index, /<title>Vitr<\/title>/);
  assert.match(index, /\/vitr-icon\.svg/);
  assert.match(app, /github\.com\/bloodvitr/);
  assert.match(app, /ko-fi\.com\/bloodvitr/);
  assert.match(music, /github\.com\/bloodvitr\/vitr/);
  assert.match(readme, /^# Vitr/m);
});

test('Vitr logo asset exists', () => {
  assert.equal(existsSync(new URL('../../public/vitr-icon.svg', import.meta.url)), true);
});


test('Ko-fi links use the Vitr cup-and-heart icon in header and footer', () => {
  const app = read('src/App.jsx');
  assert.match(app, /function KoFiIcon/);
  assert.ok((app.match(/<KoFiIcon\b/g) || []).length >= 2);
  assert.ok((app.match(/className="header-link kofi-link"/g) || []).length >= 1);
  assert.ok((app.match(/className="kofi-link"/g) || []).length >= 1);
});


test('download controls are locked to bloodvitr/vitr releases', () => {
  const app = read('src/App.jsx');
  const vitrApp = read('shared/vitr-app.js');
  assert.match(vitrApp, /export const VITR_REPO = 'bloodvitr\/vitr'/);
  assert.match(app, /VITR_RELEASES_URL = `\$\{VITR_REPO_URL\}\/releases`/);
  assert.match(app, /Choose Windows download/);
  assert.match(app, /ALL BUILDS/);
  assert.match(app, /href=\{VITR_RELEASES_URL\}/);
});
