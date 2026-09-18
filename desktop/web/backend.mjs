import { dedupeTracks } from './core.mjs';

const DISCOVERY_TERMS = [
  ['Pop', 'genre'], ['Hip-hop', 'genre'], ['R&B', 'genre'], ['Rock', 'genre'],
  ['Electronic', 'genre'], ['Indie', 'genre'], ['Jazz', 'genre'], ['Classical', 'genre'],
  ['Metal', 'genre'], ['Afrobeats', 'genre'], ['Latin', 'genre'], ['Country', 'genre'],
  ['Reggae', 'genre'], ['K-pop', 'genre'], ['Ambient', 'genre'], ['Lo-fi', 'genre'],
  ['Chill', 'mood'], ['Workout', 'mood'], ['Focus', 'mood'], ['Party', 'mood'],
  ['Sleep', 'mood'], ['Romance', 'mood'], ['Energy', 'mood'],
];

function discoverySuggestions(query) {
  const clean = String(query || '').trim().toLowerCase();
  if (!clean) return [];
  return DISCOVERY_TERMS
    .filter(([title]) => title.toLowerCase().includes(clean) || clean.includes(title.toLowerCase()))
    .slice(0, 6)
    .map(([title, kind]) => ({
      id: `suggested:${kind}:${title.toLowerCase()}`,
      kind: 'genre',
      title,
      subtitle: kind === 'mood' ? 'Mood' : 'Genre',
      cover: null,
    }));
}

function dedupeCatalogItems(groups) {
  const seen = new Set();
  const output = [];
  for (const item of groups.flat()) {
    if (!item?.title) continue;
    const key = `${item.kind || 'item'}:${item.id || item.title}`;
    if (seen.has(key)) continue;
    seen.add(key);
    output.push(item);
  }
  return output;
}

export function createBackend({ invoke, listen = null, convertFileSrc = (path) => path }) {
  if (typeof invoke !== 'function') throw new TypeError('vitr backend requires an invoke function');

  const trySearch = async (command, payload) => {
    try {
      const result = await invoke(command, payload);
      return { ok: true, results: Array.isArray(result) ? result : [] };
    } catch (error) {
      return { ok: false, error };
    }
  };

  const tryCatalog = async (payload) => {
    try {
      const result = await invoke('innertube_catalog_search', payload);
      return {
        ok: true,
        catalog: {
          tracks: Array.isArray(result?.tracks) ? result.tracks : [],
          artists: Array.isArray(result?.artists) ? result.artists : [],
          albums: Array.isArray(result?.albums) ? result.albums : [],
          playlists: Array.isArray(result?.playlists) ? result.playlists : [],
          genres: Array.isArray(result?.genres) ? result.genres : [],
        },
      };
    } catch (error) {
      return { ok: false, error, catalog: { tracks: [], artists: [], albums: [], playlists: [], genres: [] } };
    }
  };

  return {
    async search(query) {
      const clean = String(query || '').trim();
      if (!clean) return [];
      const providerQuery = /\b(song|music|audio|lyrics?|official|album|artist|playlist|remix|instrumental|soundtrack|single)\b/i.test(clean)
        ? clean
        : `${clean} song`;
      const attempts = await Promise.all([
        trySearch('innertube_search', { query: providerQuery, client: 'music' }),
        trySearch('innertube_search', { query: providerQuery, client: 'web' }),
        trySearch('ytdlp_search', { query: providerQuery }),
      ]);
      const successful = attempts.filter((attempt) => attempt.ok);
      if (!successful.length) {
        throw new Error('Search providers are temporarily unavailable. Try again.');
      }
      return dedupeTracks(successful.map((attempt) => attempt.results));
    },

    async discover(query) {
      const clean = String(query || '').trim();
      if (!clean) return { tracks: [], artists: [], albums: [], playlists: [], genres: [] };
      const providerQuery = /\b(song|music|audio|lyrics?|official|album|artist|playlist|remix|instrumental|soundtrack|single|genre|mood)\b/i.test(clean)
        ? clean
        : `${clean} music`;
      const [musicCatalog, webAttempt, ytdlpAttempt] = await Promise.all([
        tryCatalog({ query: providerQuery }),
        trySearch('innertube_search', { query: providerQuery, client: 'web' }),
        trySearch('ytdlp_search', { query: providerQuery }),
      ]);
      if (!musicCatalog.ok && !webAttempt.ok && !ytdlpAttempt.ok) {
        throw new Error('Search providers are temporarily unavailable. Try again.');
      }
      return {
        tracks: dedupeTracks([
          musicCatalog.catalog.tracks,
          webAttempt.results || [],
          ytdlpAttempt.results || [],
        ]),
        artists: dedupeCatalogItems([musicCatalog.catalog.artists]),
        albums: dedupeCatalogItems([musicCatalog.catalog.albums]),
        playlists: dedupeCatalogItems([musicCatalog.catalog.playlists]),
        genres: dedupeCatalogItems([musicCatalog.catalog.genres, discoverySuggestions(clean)]),
      };
    },

    async resolveTrack(track) {
      if (track?.kind === 'local' && track.path) {
        await invoke('authorize_media_path', { path: track.path });
        return convertFileSrc(track.path);
      }
      if (!track?.id) throw new Error('This track has no playable source.');
      return invoke('resolve_stream_url', { videoId: track.id });
    },

    scanDownloads(dir) {
      return invoke('scan_downloads', { dir });
    },

    clearRemovedDownloads(downloadDir) {
      return invoke('clear_removed_downloads', { downloadDir });
    },

    downloadAlreadyExists(videoId, outputDir) {
      return invoke('download_already_exists', { videoId, outputDir });
    },

    startDownload({ taskId, track, outputDir, format, quality, allowPlaylist = false }) {
      if (!track?.id) throw new Error('This track cannot be downloaded.');
      return invoke('download_track', {
        taskId,
        url: `https://www.youtube.com/watch?v=${track.id}`,
        outputDir,
        format,
        quality,
        allowPlaylist,
      });
    },

    cancelDownload(taskId) {
      return invoke('cancel_download', { taskId });
    },

    removeDownload(path, downloadDir) {
      return invoke('remove_download', { path, downloadDir });
    },

    lyrics(track) {
      return invoke('fetch_metadata_lyrics', {
        title: track?.title || '',
        artist: track?.artist || '',
        album: track?.album || '',
        durationSeconds: Number(track?.durationSeconds || 0),
      });
    },

    currentRuntimeStatus() {
      return invoke('current_runtime_status');
    },

    updateRuntimeDependencies() {
      return invoke('update_runtime_dependencies');
    },

    autoUpdate() {
      return invoke('auto_update');
    },

    setMiniPlayerEnabled(enabled, layout = 'bar') {
      return invoke('set_mini_player_enabled', { enabled, layout });
    },

    setMiniPlayerLayout(layout = 'bar') {
      return invoke('set_mini_player_layout', { layout });
    },

    updateMiniPlayer(snapshot) {
      return invoke('update_mini_player', { snapshot });
    },

    setTrayEnabled(enabled) {
      return invoke('set_tray_enabled', { enabled });
    },

    updateMediaControls(snapshot) {
      return invoke('update_media_controls', { snapshot });
    },

    onDownloadProgress(handler) {
      if (typeof listen !== 'function') return Promise.resolve(() => {});
      return listen('download-progress', (event) => handler(event.payload));
    },
  };
}
