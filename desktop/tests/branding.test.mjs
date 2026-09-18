import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = fileURLToPath(new URL('../', import.meta.url));

test('desktop and in-app vitr branding use the uploaded canonical logo', async () => {
  const [index, styles, config, pkg, icon] = await Promise.all([
    readFile(join(root, 'web', 'index.html'), 'utf8'),
    readFile(join(root, 'web', 'styles-1.css'), 'utf8'),
    readFile(join(root, 'src-tauri', 'tauri.conf.json'), 'utf8'),
    readFile(join(root, 'package.json'), 'utf8'),
    readFile(join(root, 'web', 'vitr-icon.svg'), 'utf8'),
  ]);

  assert.match(index, /<title>vitr<\/title>/);
  assert.match(index, /<link rel="icon" href="\.\/vitr-icon\.svg"/);
  assert.match(index, /<img class="brand-mark" src="\.\/vitr-icon\.svg"/);
  assert.match(styles, /\.brand-mark[^}]*url\('\.\/vitr-icon\.svg'\)/s);
  assert.match(config, /"productName"\s*:\s*"vitr"/);
  assert.match(config, /"identifier"\s*:\s*"app\.vitr\.desktop"/);
  assert.match(pkg, /"name"\s*:\s*"vitr"/);
  assert.match(pkg, /tauri icon web\/vitr-icon\.svg/);
  assert.match(icon, /<svg[\s\S]*linearGradient[\s\S]*<path/);
  assert.ok(icon.length > 500);
});
