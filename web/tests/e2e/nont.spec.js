import { expect, test } from '@playwright/test';

const TRACKS = [
  { id: 'dQw4w9WgXcQ', title: 'Signal', artist: 'Daft Punk', thumbnail: 'https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg', official: true },
  { id: 'M7lc1UVf-VE', title: 'Genesis', artist: 'Justice', thumbnail: 'https://i.ytimg.com/vi/M7lc1UVf-VE/hqdefault.jpg', official: true },
];

async function installFakePlayer(page) {
  await page.addInitScript(() => {
    class FakePlayer {
      constructor(_target, options = {}) {
        this.options = options;
        this.state = -1;
        this.current = 0;
        this.duration = 240;
        queueMicrotask(() => options.events?.onReady?.({ target: this }));
      }
      loadVideoById(id) { this.videoId = id; this.state = 1; this.options.events?.onStateChange?.({ target: this, data: 1 }); }
      playVideo() { this.state = 1; this.options.events?.onStateChange?.({ target: this, data: 1 }); }
      pauseVideo() { this.state = 2; this.options.events?.onStateChange?.({ target: this, data: 2 }); }
      stopVideo() { this.state = 0; }
      destroy() {}
      setVolume(value) { this.volume = value; }
      mute() { this.muted = true; }
      unMute() { this.muted = false; }
      seekTo(value) { this.current = value; }
      getCurrentTime() { return this.current; }
      getDuration() { return this.duration; }
      getPlayerState() { return this.state; }
    }
    window.YT = {
      Player: FakePlayer,
      PlayerState: { UNSTARTED: -1, ENDED: 0, PLAYING: 1, PAUSED: 2, BUFFERING: 3, CUED: 5 },
    };
  });
}

async function mockApis(page) {
  await page.route('**/api/youtube-search?*', async (route) => {
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ items: TRACKS, source: 'test' }) });
  });
  await page.route('**/api/github-app?*', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        id: 'vitr',
        name: 'Vitr',
        description: 'Vitr test metadata',
        sources: [],
        availablePlatforms: [],
        assets: [],
      }),
    });
  });
  await page.route('**/api/github-search?*', async (route) => {
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ items: [], page: 1, hasMore: false }) });
  });
}

test.beforeEach(async ({ page }) => {
  await installFakePlayer(page);
  await mockApis(page);
});

test('nont.me is the real Vitr landing page, not a mockup gallery', async ({ page }) => {
  await page.goto('/');
  await expect(page).toHaveTitle('Vitr');
  await expect(page.getByRole('heading', { name: /Your music/i })).toBeVisible();
  await expect(page.getByText('THE EXPERIENCE')).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Modern Dark' })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Liquid Glass' })).toBeVisible();
  await expect(page.locator('.showcase-desktop')).toHaveCount(0);
  await expect(page.locator('.showcase-phone')).toHaveCount(0);
  await expect(page.getByText('Neon Skies')).toHaveCount(0);
});

test('nont.me keeps real downloads, search and official player links', async ({ page }) => {
  await page.goto('/');
  await expect(page.getByRole('textbox', { name: 'Search GitHub apps' })).toBeVisible();
  await expect(page.getByRole('link', { name: /Open Web Player/i })).toHaveAttribute('href', 'https://vitr.nont.me');
  await expect(page.getByRole('link', { name: /ALL BUILDS/i }).first()).toHaveAttribute('href', 'https://github.com/bloodvitr/vitr/releases');
  await expect(page.locator('.vitr-nav')).toHaveCount(0);
});

test('nont.me stays contained at desktop and laptop widths', async ({ page }) => {
  for (const viewport of [{ width: 1294, height: 717 }, { width: 1008, height: 678 }]) {
    await page.setViewportSize(viewport);
    await page.goto('/');
    await expect(page.locator('.hero-section')).toBeVisible();
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= document.documentElement.clientWidth)).toBe(true);
  }
});

test('nont.me mobile layout uses touch-friendly quick actions', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto('/');
  await expect(page.locator('.mobile-dock')).toBeVisible();
  await expect(page.locator('.mobile-dock').getByRole('link', { name: 'Play', exact: true })).toHaveAttribute('href', 'https://vitr.nont.me');
  await expect(page.getByRole('heading', { name: 'Modern Dark' })).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= document.documentElement.clientWidth)).toBe(true);
});

test('/music opens the four-tab Vitr web player', async ({ page }) => {
  await page.goto('/music');
  await expect(page.getByRole('heading', { name: 'VITR' })).toBeVisible();

  const nav = page.locator('.vitr-nav');
  await expect(nav.getByRole('button')).toHaveCount(4);
  for (const label of ['Home', 'Search', 'Library', 'Settings']) {
    await expect(nav.getByRole('button', { name: label })).toBeVisible();
  }
});

test('Vitr Web exposes real Modern Dark and Liquid Glass finishes', async ({ page }) => {
  await page.goto('/music');
  const nav = page.locator('.vitr-nav');
  await nav.getByRole('button', { name: 'Settings' }).click();

  const app = page.locator('.vitr-app');
  await expect(app).toHaveAttribute('data-vitr-finish', 'dark');
  await page.getByRole('button', { name: /Appearance: Modern Dark/ }).click();
  await expect(app).toHaveAttribute('data-vitr-finish', 'glass');
  await expect(page.getByRole('button', { name: /Appearance: Liquid Glass/ })).toBeVisible();
  await expect(page.getByText('v0.1.0', { exact: true })).toBeVisible();
});

test('search result starts browser playback without a worker', async ({ page }) => {
  await page.goto('/music');
  const nav = page.locator('.vitr-nav');
  await nav.getByRole('button', { name: 'Search' }).click();

  await page.getByRole('textbox', { name: 'Search VITR' }).fill('Signal');
  await page.getByRole('button', { name: 'Search music' }).click();
  await expect(page.locator('.vitr-result-title', { hasText: /^Signal$/ }).first()).toBeVisible();

  await page.getByRole('button', { name: 'Play Signal', exact: true }).first().click();
  await expect(page.locator('.vitr-mini-player')).toBeVisible();
  await expect(page.locator('.vitr-mini-player').getByRole('button', { name: 'Pause' })).toBeVisible();
});

test('Reset VITR Web clears Vitr data and stays on the web player', async ({ page }) => {
  await page.goto('/music');
  await page.evaluate(() => {
    localStorage.setItem('vitr.web.library.v1', JSON.stringify([{ id: 'saved-track' }]));
    localStorage.setItem('vitr.web.playlists.manual.v1', JSON.stringify([{ id: 'p1', name: 'Old', tracks: [] }]));
    localStorage.setItem('other.app.keep', 'yes');
  });

  await page.locator('.vitr-nav').getByRole('button', { name: 'Settings' }).click();
  page.once('dialog', (dialog) => dialog.accept());
  await Promise.all([
    page.waitForNavigation(),
    page.getByRole('button', { name: 'Reset VITR Web' }).click(),
  ]);

  expect(new URL(page.url()).pathname).toBe('/music');
  await expect(page.getByRole('heading', { name: 'VITR' })).toBeVisible();

  const snapshot = await page.evaluate(() => ({
    library: JSON.parse(localStorage.getItem('vitr.web.library.v1') || '[]'),
    playlists: JSON.parse(localStorage.getItem('vitr.web.playlists.manual.v1') || '[]'),
    other: localStorage.getItem('other.app.keep'),
  }));
  expect(snapshot).toEqual({ library: [], playlists: [], other: 'yes' });
});

test('mobile web player has no page-level horizontal overflow', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto('/music');
  await expect(page.getByRole('heading', { name: 'VITR' })).toBeVisible();
  await expect(page.locator('.vitr-nav')).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= document.documentElement.clientWidth)).toBe(true);
});


test('Vitr Web adapts to compact windows without horizontal overflow', async ({ page }) => {
  for (const viewport of [{ width: 1180, height: 720 }, { width: 960, height: 680 }, { width: 720, height: 700 }, { width: 390, height: 844 }]) {
    await page.setViewportSize(viewport);
    await page.goto('/music');
    await expect(page.getByRole('heading', { name: 'VITR' })).toBeVisible();
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= document.documentElement.clientWidth)).toBe(true);
  }
});

test('Search header and search bar stay visible while results scroll', async ({ page }) => {
  await page.setViewportSize({ width: 1008, height: 678 });
  await page.goto('/music');
  await page.locator('.vitr-nav').getByRole('button', { name: 'Search' }).click();
  await page.getByRole('textbox', { name: 'Search VITR' }).fill('Signal');
  await page.getByRole('button', { name: 'Search music' }).click();
  await expect(page.locator('.vitr069-sticky-search')).toBeVisible();
  await page.evaluate(() => window.scrollTo(0, document.body.scrollHeight));
  const top = await page.locator('.vitr069-sticky-search').evaluate((node) => node.getBoundingClientRect().top);
  expect(top).toBeGreaterThanOrEqual(0);
  expect(top).toBeLessThan(40);
});


test('Clear search removes the query and results without reloading Vitr Web', async ({ page }) => {
  await page.goto('/music');
  await page.locator('.vitr-nav').getByRole('button', { name: 'Search' }).click();
  const input = page.getByRole('textbox', { name: 'Search VITR' });
  await input.fill('Signal');
  await page.getByRole('button', { name: 'Search music' }).click();
  await expect(page.locator('.vitr-result-title', { hasText: /^Signal$/ }).first()).toBeVisible();
  await page.getByRole('button', { name: 'Clear search' }).click();
  await expect(input).toHaveValue('');
  await expect(page.locator('.vitr-result-title')).toHaveCount(0);
  await expect(page.getByText(/Search for anything/)).toBeVisible();
  expect(new URL(page.url()).pathname).toBe('/music');
});


test('Now Playing keeps previous and next controls visible in a short resizable window', async ({ page }) => {
  await page.setViewportSize({ width: 900, height: 560 });
  await page.goto('/music');
  await page.locator('.vitr-nav').getByRole('button', { name: 'Search' }).click();
  await page.getByRole('textbox', { name: 'Search VITR' }).fill('Signal');
  await page.getByRole('button', { name: 'Search music' }).click();
  await page.getByRole('button', { name: 'Play Signal', exact: true }).first().click();
  await page.locator('.vitr-mini-main').click();
  await expect(page.getByRole('button', { name: 'Previous track' })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Next track' })).toBeVisible();
  for (const name of ['Previous track', 'Next track']) {
    const box = await page.getByRole('button', { name }).boundingBox();
    expect(box).not.toBeNull();
    expect(box.y + box.height).toBeLessThanOrEqual(560);
  }
});

test('Settings only contains non-playback settings', async ({ page }) => {
  await page.goto('/music');
  await page.locator('.vitr-nav').getByRole('button', { name: 'Settings' }).click();
  await expect(page.getByRole('button', { name: /Appearance:/ })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Reset VITR Web' })).toBeVisible();
  await expect(page.getByText(/^Volume /)).toHaveCount(0);
  await expect(page.getByRole('button', { name: /Mute|Unmute|Shuffle:|Repeat:/ })).toHaveCount(0);
});
