import test from 'node:test';
import assert from 'node:assert/strict';
import { normalizeGithubRepo } from '../../shared/github-repo.js';

test('normalizes owner/repo and GitHub repository URLs', () => {
  assert.equal(normalizeGithubRepo('voidnont/Vitr-Windows'), 'voidnont/Vitr-Windows');
  assert.equal(normalizeGithubRepo('https://github.com/voidnont/vitr'), 'voidnont/vitr');
  assert.equal(normalizeGithubRepo('https://github.com/voidnont/vitr.git'), 'voidnont/vitr');
});

test('rejects non-GitHub and malformed repositories', () => {
  assert.equal(normalizeGithubRepo('https://example.com/voidnont/vitr'), null);
  assert.equal(normalizeGithubRepo('../bad'), null);
  assert.equal(normalizeGithubRepo('owner/repo/extra'), null);
});
