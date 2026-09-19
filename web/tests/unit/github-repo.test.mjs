import test from 'node:test';
import assert from 'node:assert/strict';
import { normalizeGithubRepo } from '../../shared/github-repo.js';

test('normalizes owner/repo and GitHub repository URLs', () => {
  assert.equal(normalizeGithubRepo('bloodvitr/Vitr-Windows'), 'bloodvitr/Vitr-Windows');
  assert.equal(normalizeGithubRepo('https://github.com/bloodvitr/vitr'), 'bloodvitr/vitr');
  assert.equal(normalizeGithubRepo('https://github.com/bloodvitr/vitr.git'), 'bloodvitr/vitr');
});

test('rejects non-GitHub and malformed repositories', () => {
  assert.equal(normalizeGithubRepo('https://example.com/bloodvitr/vitr'), null);
  assert.equal(normalizeGithubRepo('../bad'), null);
  assert.equal(normalizeGithubRepo('owner/repo/extra'), null);
});
