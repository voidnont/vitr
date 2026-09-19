# Vitr

Vitr is a cross-platform music project by **Blood**. The repository version is **0.1.0**.

## Repository layout

- `web/` — complete Vitr Web source, serverless API implementation, tests, Vite/Playwright config, public assets and web deployment config.
- `desktop/` — complete Tauri desktop source, including native Rust code, desktop UI, and platform tooling for Windows, Linux and macOS.
- `mobile/android/` — full native Android Vitr app migrated from `voidnont/frxe`, including playback, downloads, Android Auto, persistence, resolver logic and tests.
- `mobile/ios/` — reserved for the future native iOS source.
- `.github/workflows/` — repository CI and release orchestration.
- `api/` — tiny Vercel entry-point shims only; the real API implementation lives in `web/api/`.
- `vercel.json` — root deployment bridge that builds `web/`.
- `VERSION` — canonical repository version.

## Commands

- `npm run web:dev` — start Vitr Web.
- `npm run web:check` — validate and unit-test Vitr Web.
- `npm run web:e2e` — run Vitr Web browser tests.
- `npm run desktop:dev` — start the Tauri desktop app.
- `npm run desktop:check` — validate desktop frontend/native contracts.
- `npm run desktop:build:windows` — build Windows installers.
- `npm run desktop:build:linux` — build Linux packages.
- `npm run desktop:build:macos` — build macOS packages.
- `npm run android:build` — build the native Android Vitr 0.1.0 APK.
- `npm run check` — validate web and desktop.

The official web player is `https://vitr.nont.me`. Desktop and Android build artifacts are produced from `voidnont/vitr`.

## License

Vitr is source-available, not open source. See [LICENSE](./LICENSE).
