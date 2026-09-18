# Vitr

Vitr is a dark, glass-forward music experience by **Blood**.

## nont.me

The root `nont.me` site is Vitr's standalone landing and download experience. It uses the same dark navy, pink accent, glass surfaces, spacing and motion language as Vitr Web instead of showing mockup galleries.

Downloads and build links are connected to `bloodvitr/vitr`.

## Vitr Web

The official web player is `https://vitr.nont.me`. The `/music` route remains as a compatibility path on `nont.me`.

Vitr Web uses the same visual system as the landing page and includes two real appearance finishes in Settings:

- **Modern Dark**
- **Liquid Glass**

Playback needs no worker service, private token, API key, or Vitr-specific server setup. Search uses Vitr's serverless YouTube Music search endpoint and playback uses the YouTube IFrame Player API directly in the browser.

Vitr Web includes search, playback, local library/history, playlists, recommendations, Media Session controls, and **Reset VITR Web**.

## Local data

Saved songs, history, playlists, recent searches, recommendations, appearance and player settings are stored in the browser. **Reset VITR Web** removes Vitr-owned local data and reloads the current player route from a clean state.

## Creator

- GitHub: `github.com/bloodvitr`
- Vitr: `github.com/bloodvitr/vitr`
- Ko-fi: `ko-fi.com/bloodvitr`


## License

Vitr is **source-available**, not open source.

You may download, view, and run unmodified copies for personal, non-commercial use. Modifications are only permitted when made privately for the purpose of contributing them back to the official Vitr project. Redistribution, forks, derivative releases, commercial use, or other modifications require prior permission from **Blood**.

See [LICENSE](./LICENSE) for the full terms.
