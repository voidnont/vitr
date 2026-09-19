import { useEffect, useMemo, useState } from 'react';
import {
  ChevronRight,
  Download,
  ExternalLink,
  Github,
  LoaderCircle,
  Monitor,
  Package,
  Play,
  Search,
  Smartphone,
  Sparkles,
  X,
} from 'lucide-react';
import { detectCurrentDevice } from './device.js';
import { normalizeGithubRepo } from '../shared/github-repo.js';
import { recommendAsset } from '../shared/release-classifier.js';
import { VITR_REPO } from '../shared/vitr-app.js';

const VERSION = '0.1.0';
const VITR_REPO_URL = `https://github.com/${VITR_REPO}`;
const VITR_RELEASES_URL = `${VITR_REPO_URL}/releases`;
const PLATFORM_OPTIONS = ['recommended', 'windows', 'android', 'macos', 'linux', 'ios'];
const PLATFORM_LABELS = {
  recommended: 'Recommended',
  windows: 'Windows',
  android: 'Android',
  macos: 'macOS',
  linux: 'Linux',
  ios: 'iOS',
  chromeos: 'ChromeOS',
  unknown: 'Unknown device',
};

function formatBytes(bytes = 0) {
  if (!bytes) return '';
  const units = ['B', 'KB', 'MB', 'GB'];
  const index = Math.min(Math.floor(Math.log(bytes) / Math.log(1024)), units.length - 1);
  return `${(bytes / (1024 ** index)).toFixed(index ? 1 : 0)} ${units[index]}`;
}

function bestForPlatform(assets = [], platform) {
  return assets
    .filter((asset) => asset.installable && asset.platform === platform)
    .sort((a, b) => (b.score || 0) - (a.score || 0) || (b.size || 0) - (a.size || 0))[0] || null;
}

function validDownloadUrl(value = '') {
  try {
    const url = new URL(value);
    return url.protocol === 'https:' && ['github.com', 'www.github.com'].includes(url.hostname.toLowerCase());
  } catch {
    return false;
  }
}

function sourceVersion(app, repo) {
  return app?.sources?.find((source) => source.repo?.toLowerCase() === repo.toLowerCase())?.version || '';
}

function PlatformIcon({ platform, size = 17 }) {
  if (platform === 'android' || platform === 'ios') return <Smartphone size={size} />;
  return <Monitor size={size} />;
}

function KoFiIcon({ size = 17 }) {
  return <svg className="kofi-icon" width={size} height={size} viewBox="0 0 24 24" fill="none" aria-hidden="true">
    <path d="M4.5 7.25h11.25v6.1a4.4 4.4 0 0 1-4.4 4.4H8.9a4.4 4.4 0 0 1-4.4-4.4v-6.1Z" stroke="currentColor" strokeWidth="1.8" strokeLinejoin="round" />
    <path d="M15.75 8.6h1.35a2.9 2.9 0 0 1 0 5.8h-1.35" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" />
    <path d="M8.15 10.1c.55-.72 1.73-.58 2.1.2.38-.78 1.56-.92 2.1-.2.7.94-.1 2.03-2.1 3.42-2-1.39-2.8-2.48-2.1-3.42Z" fill="#ff4d7c" />
  </svg>;
}

function DownloadDrawer({ app, onClose, onDownload }) {
  if (!app) return null;
  const assets = (app.assets || []).filter((asset) => asset.platform && asset.platform !== 'unknown');
  const grouped = PLATFORM_OPTIONS.slice(1).map((platform) => ({
    platform,
    assets: assets.filter((asset) => asset.platform === platform),
  })).filter((group) => group.assets.length);

  return <div className="drawer-backdrop" onMouseDown={(event) => event.target === event.currentTarget && onClose()}>
    <section className="download-drawer" role="dialog" aria-modal="true" aria-label={`${app.name || 'App'} downloads`}>
      <div className="drawer-head">
        <div><span className="eyebrow">ALL RELEASE ASSETS</span><h2>{app.name || app.repo || 'Downloads'}</h2></div>
        <button className="icon-button" onClick={onClose} aria-label="Close downloads"><X size={18} /></button>
      </div>
      {grouped.length ? grouped.map((group) => <div className="download-group" key={group.platform}>
        <h3><PlatformIcon platform={group.platform} /> {PLATFORM_LABELS[group.platform]}</h3>
        {group.assets.map((asset) => <div className="asset-row" key={`${asset.repo || app.repo}-${asset.id || asset.name}`}>
          <div><strong>{asset.name}</strong><span>{asset.arch !== 'unknown' ? asset.arch : 'architecture unspecified'}{asset.size ? ` · ${formatBytes(asset.size)}` : ''}</span></div>
          {asset.installable ? <button className="small-download" data-download-url={asset.url} onClick={() => onDownload(asset)}><Download size={15} /> Download</button> : <span className="not-direct">Not a direct install</span>}
        </div>)}
      </div>) : <div className="empty-panel"><Package size={24} /><strong>No installable release assets yet.</strong></div>}
    </section>
  </div>;
}

function ResultCard({ item, platform, device, onDetails, onDownload }) {
  const targetPlatform = platform === 'recommended' ? device.os : platform;
  const recommended = platform === 'recommended'
    ? recommendAsset(item.assets || [], device)
    : bestForPlatform(item.assets || [], targetPlatform);
  const badges = item.availablePlatforms || [];

  return <article className="result-card">
    <div className="result-main">
      <div className="result-icon"><Package size={20} /></div>
      <div className="result-copy">
        <div className="result-name-line"><h3>{item.name || item.repo}</h3>{item.latestVersion && <span>v{item.latestVersion}</span>}</div>
        <p className="repo-name">{item.repo}</p>
        <p>{item.description || 'No repository description provided.'}</p>
        <div className="platform-badges">{badges.map((badge) => <span key={badge}>{PLATFORM_LABELS[badge] || badge}</span>)}{!badges.length && <span>Source only</span>}</div>
      </div>
    </div>
    <div className="result-actions">
      {recommended ? <button className="primary compact" data-download-url={recommended.url} onClick={() => onDownload(recommended)}><Download size={16} /> Download</button>
        : <button className="secondary compact" onClick={() => onDetails(item)}>View downloads <ChevronRight size={15} /></button>}
      <a className="repo-link" href={item.url || `https://github.com/${item.repo}`} target="_blank" rel="noreferrer">GitHub <ExternalLink size={13} /></a>
    </div>
  </article>;
}

export default function App() {
  const [device, setDevice] = useState({ os: 'unknown', arch: 'unknown', mobile: false, confidence: 'low' });
  const [vitr, setVitr] = useState(null);
  const [query, setQuery] = useState('');
  const [results, setResults] = useState([]);
  const [platform, setPlatform] = useState('recommended');
  const [selectedApp, setSelectedApp] = useState(null);
  const [loading, setLoading] = useState({ vitr: true, search: false });
  const [error, setError] = useState('');

  useEffect(() => {
    void detectCurrentDevice().then(setDevice);
    void fetch('/api/github-app?app=vitr', { cache: 'no-store' })
      .then(async (response) => {
        const data = await response.json().catch(() => ({}));
        if (!response.ok) throw new Error(data.error || 'Could not load Vitr');
        return data;
      })
      .then(setVitr)
      .catch((reason) => setError(reason instanceof Error ? reason.message : String(reason)))
      .finally(() => setLoading((state) => ({ ...state, vitr: false })));
  }, []);

  useEffect(() => {
    const nodes = Array.from(document.querySelectorAll('[data-reveal]'));
    if (!nodes.length) return undefined;
    if (!('IntersectionObserver' in window)) {
      nodes.forEach((node) => node.classList.add('is-visible'));
      return undefined;
    }
    const observer = new IntersectionObserver((entries) => {
      entries.forEach((entry) => {
        if (!entry.isIntersecting) return;
        entry.target.classList.add('is-visible');
        observer.unobserve(entry.target);
      });
    }, { threshold: 0.08, rootMargin: '0px 0px -6% 0px' });
    nodes.forEach((node) => observer.observe(node));
    return () => observer.disconnect();
  }, []);

  useEffect(() => {
    const trimmed = query.trim();
    if (!trimmed) {
      setResults([]);
      setLoading((state) => ({ ...state, search: false }));
      return undefined;
    }
    const controller = new AbortController();
    const timer = window.setTimeout(async () => {
      setLoading((state) => ({ ...state, search: true }));
      setError('');
      try {
        const directRepo = normalizeGithubRepo(trimmed);
        const url = directRepo
          ? `/api/github-app?repo=${encodeURIComponent(directRepo)}`
          : `/api/github-search?${new URLSearchParams({ q: trimmed, ...(platform !== 'recommended' ? { platform } : {}) }).toString()}`;
        const response = await fetch(url, { signal: controller.signal, cache: 'no-store' });
        const data = await response.json().catch(() => ({}));
        if (!response.ok) throw new Error(data.error || 'Search failed');
        setResults(directRepo ? [data] : (data.items || []));
      } catch (reason) {
        if (reason?.name !== 'AbortError') setError(reason instanceof Error ? reason.message : String(reason));
      } finally {
        setLoading((state) => ({ ...state, search: false }));
      }
    }, 300);
    return () => {
      window.clearTimeout(timer);
      controller.abort();
    };
  }, [query, platform]);

  const heroRecommendation = useMemo(() => recommendAsset(vitr?.assets || [], device), [vitr, device]);
  const windowsVersion = sourceVersion(vitr, VITR_REPO);

  function startDownload(asset) {
    if (!asset?.url || !validDownloadUrl(asset.url)) {
      setError('This release did not provide a valid GitHub download URL.');
      return;
    }
    window.location.href = asset.url;
  }

  function heroAction() {
    if (heroRecommendation) return <button className="primary hero-download" data-download-url={heroRecommendation.url} onClick={() => startDownload(heroRecommendation)}><Download size={18} /> Download for {PLATFORM_LABELS[device.os]}</button>;
    if (device.os === 'windows') return <a className="primary hero-download" href={VITR_RELEASES_URL} target="_blank" rel="noreferrer"><Download size={18} /> Choose Windows download</a>;
    return <a className="primary hero-download" href={VITR_RELEASES_URL} target="_blank" rel="noreferrer"><Download size={18} /> Choose download</a>;
  }

  function mobileInstallAction() {
    if (heroRecommendation) return <button className="mobile-dock-primary" data-download-url={heroRecommendation.url} onClick={() => startDownload(heroRecommendation)}><Download size={15} /> Install</button>;
    return <a className="mobile-dock-primary" href={VITR_RELEASES_URL} target="_blank" rel="noreferrer"><Download size={15} /> Builds</a>;
  }

  return <div className={device.mobile ? 'app-shell landing-shell is-mobile-device' : 'app-shell landing-shell'}>
    <div className="site-ambient" aria-hidden="true"><span /><span /></div>

    <header className="site-header">
      <a className="brand" href="/" aria-label="Vitr home"><img src="/vitr-icon.png" alt="" /><span><strong>vitr</strong><small>by blood</small></span></a>
      <nav className="site-nav" aria-label="Primary navigation">
        <a href="#experience">Experience</a>
        <a href="#downloads">Downloads</a>
        <a href="https://vitr.nont.me">Web Player</a>
      </nav>
      <div className="header-actions">
        <a className="header-link" href="https://github.com/bloodvitr" target="_blank" rel="noreferrer"><Github size={15} /> GitHub</a>
        <a className="header-link kofi-link" href="https://ko-fi.com/bloodvitr" target="_blank" rel="noreferrer"><KoFiIcon size={15} /> Ko-fi</a>
      </div>
    </header>

    <main>
      <section className="hero-section">
        <div className="hero-copy">
          <span className="eyebrow"><Sparkles size={13} /> VITR MUSIC PLAYER</span>
          <h1>Your music.<br /><em>Your space.</em></h1>
          <p>Vitr is a focused music player built around a calm dark interface, soft glass surfaces and fast access to the music you actually want to play.</p>
          <div className="hero-actions">
            {loading.vitr ? <button className="primary hero-download" disabled><LoaderCircle className="spin" size={18} /> Checking releases…</button> : heroAction()}
            <a className="secondary hero-download" href="https://vitr.nont.me"><Play size={17} fill="currentColor" /> Open Web Player</a>
            <a className="ghost-link" href={VITR_RELEASES_URL} target="_blank" rel="noreferrer">ALL BUILDS <ChevronRight size={15} /></a>
          </div>
          <div className="hero-meta">
            <span><Monitor size={14} /> Windows {windowsVersion ? `v${windowsVersion}` : 'release channel'}</span>
            <span><Smartphone size={14} /> Mobile-ready web player</span>
            <span>v{VERSION}</span>
          </div>
        </div>

        <div className="hero-surface" aria-label="Vitr experience">
          <div className="hero-surface-glow" aria-hidden="true" />
          <div className="hero-window glass-panel">
            <div className="hero-window-top">
              <div className="hero-window-brand"><img src="/vitr-icon.png" alt="" /><span><strong>vitr</strong><small>web</small></span></div>
              <span className="live-pill"><i /> LIVE</span>
            </div>
            <div className="hero-now">
              <div className="hero-artwork"><span>V</span></div>
              <div className="hero-now-copy"><small>NOW PLAYING</small><strong>Your soundtrack, without the clutter.</strong><span>Search · save · playlists · recommendations</span></div>
            </div>
            <div className="hero-progress"><span /></div>
            <div className="hero-window-actions"><button aria-label="Previous preview">‹</button><button className="play-preview" aria-label="Play preview"><Play size={18} fill="currentColor" /></button><button aria-label="Next preview">›</button></div>
          </div>
          <div className="floating-chip chip-one">Modern Dark</div>
          <div className="floating-chip chip-two">Liquid Glass</div>
        </div>
      </section>

      <section className="experience-section reveal-block" id="experience" data-reveal>
        <div className="section-intro">
          <span className="eyebrow">THE EXPERIENCE</span>
          <h2>One interface language.<br />Two visual finishes.</h2>
          <p>The website and player share the same spacing, motion, controls and accent system. Dark stays crisp. Glass adds depth without changing how Vitr works.</p>
        </div>

        <div className="experience-grid">
          <article className="experience-card dark-card">
            <span className="card-index">01</span>
            <div className="experience-icon"><Monitor size={22} /></div>
            <h3>Modern Dark</h3>
            <p>Deep navy surfaces, subtle borders and pink highlights keep the interface quiet while the music stays central.</p>
            <div className="feature-lines"><span /><span /><span /></div>
          </article>
          <article className="experience-card glass-card">
            <span className="card-index">02</span>
            <div className="experience-icon"><Sparkles size={22} /></div>
            <h3>Liquid Glass</h3>
            <p>Layered translucency, soft blur and reflected light for a more dimensional look that still remains readable.</p>
            <div className="glass-orbs" aria-hidden="true"><i /><i /><i /></div>
          </article>
          <article className="experience-card mobile-card">
            <span className="card-index">03</span>
            <div className="experience-icon"><Smartphone size={22} /></div>
            <h3>Built for mobile</h3>
            <p>Touch-first controls, bottom navigation and layouts that collapse cleanly without turning into a separate experience.</p>
            <div className="mobile-bars"><span /><span /><span /><span /></div>
          </article>
        </div>
      </section>

      <section className="platform-section reveal-block" data-reveal>
        <div>
          <span className="eyebrow">SAME VITR, EVERYWHERE</span>
          <h2>Desktop when you want it.<br />Web when you need it.</h2>
        </div>
        <div className="platform-actions">
          <a className="platform-card" href={VITR_RELEASES_URL} target="_blank" rel="noreferrer"><Monitor size={22} /><span><strong>Desktop builds</strong><small>Connected to voidnont/vitr releases</small></span><ChevronRight size={18} /></a>
          <a className="platform-card accent-card" href="https://vitr.nont.me"><Play size={22} fill="currentColor" /><span><strong>Vitr Web</strong><small>Official player at vitr.nont.me</small></span><ChevronRight size={18} /></a>
        </div>
      </section>

      <section className="downloads-section reveal-block" id="downloads" data-reveal>
        <div className="section-heading">
          <div><span className="eyebrow">DOWNLOADS</span><h2>Get Vitr.</h2><p>Release assets come from <strong>voidnont/vitr</strong>. Until releases exist there, the build buttons open that repository’s Releases page.</p></div>
          <a className="secondary" href={VITR_RELEASES_URL} target="_blank" rel="noreferrer">ALL BUILDS <ExternalLink size={14} /></a>
        </div>

        <label className="github-search">
          <Search size={20} />
          <input aria-label="Search GitHub apps" value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Search repositories or owner/repo" autoComplete="off" />
          {loading.search && <LoaderCircle className="spin" size={18} />}
        </label>

        <div className="platform-tabs" role="tablist" aria-label="Platform filter">
          {PLATFORM_OPTIONS.map((item) => <button key={item} role="tab" aria-selected={platform === item} className={platform === item ? 'platform-tab active' : 'platform-tab'} onClick={() => setPlatform(item)}>{PLATFORM_LABELS[item]}</button>)}
        </div>

        {error && <div className="error-banner" role="status">{error}</div>}
        {!query.trim() ? <div className="release-home">
          <div><Package size={25} /><span><strong>Vitr releases</strong><small>Windows, Android, macOS, Linux and iOS assets appear here when published.</small></span></div>
          <button className="secondary" onClick={() => setSelectedApp(vitr)} disabled={!vitr}>Inspect available downloads</button>
        </div>
          : results.length ? <div className="results-list">{results.map((item) => <ResultCard key={item.repo || item.id} item={item} platform={platform} device={device} onDetails={setSelectedApp} onDownload={startDownload} />)}</div>
            : !loading.search && <div className="empty-panel"><Search size={24} /><strong>No matching releases found.</strong></div>}
      </section>
    </main>

    <footer className="site-footer">
      <div className="footer-brand"><img src="/vitr-icon.png" alt="" /><span><strong>vitr</strong><small>by blood</small></span></div>
      <p>Your music. Your space.</p>
      <div className="footer-links">
        <a href="https://vitr.nont.me">Web Player</a>
        <a href={VITR_REPO_URL} target="_blank" rel="noreferrer">Source</a>
        <a href="https://github.com/bloodvitr" target="_blank" rel="noreferrer">GitHub</a>
        <a className="kofi-link" href="https://ko-fi.com/bloodvitr" target="_blank" rel="noreferrer"><KoFiIcon size={13} /> Ko-fi</a>
      </div>
      <span className="footer-version">Vitr {VERSION}</span>
    </footer>

    <aside className="mobile-dock" aria-label="Vitr mobile quick actions">
      <div className="mobile-dock-device"><span className="device-dot" /><span>{PLATFORM_LABELS[device.os] || 'Device'}</span></div>
      {mobileInstallAction()}
      <a className="mobile-dock-player" href="https://vitr.nont.me">Play</a>
    </aside>

    <DownloadDrawer app={selectedApp} onClose={() => setSelectedApp(null)} onDownload={startDownload} />
  </div>;
}
