import test from 'node:test';
import assert from 'node:assert/strict';
import { readReleaseMetadata } from '../tools/release-metadata.mjs';

test('package.json is canonical and mirrored versions match', async () => {
  const metadata = await readReleaseMetadata();
  assert.equal(metadata.version, '1.3.2');
  assert.equal(metadata.productName, 'vitr');
});

test('release artifact names derive from the canonical version', async () => {
  const { artifacts } = await readReleaseMetadata();
  assert.deepEqual(artifacts, {
    windowsSetup: 'vitr-1.3.2-setup.exe',
    windowsMsi: 'vitr-1.3.2-x64.msi',
    linuxDeb: 'vitr-1.3.2-amd64.deb',
    linuxAppImage: 'vitr-1.3.2-x86_64.AppImage',
    macArmDmg: 'vitr-1.3.2-macos-arm64.dmg',
    macX64Dmg: 'vitr-1.3.2-macos-x64.dmg',
  });
});
