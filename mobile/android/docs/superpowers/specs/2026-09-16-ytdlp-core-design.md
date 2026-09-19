# FRXE v0.6.12 yt-dlp Core Design

## Status
Approved direction: Approach A — FRXE yt-dlp Core.

## Goal
Make embedded local yt-dlp the first-class engine for URL inspection, shared-link intake, playback stream resolution, and downloads, while keeping FRXE's existing queue, playback service, offline library, TV/Cast/voice/Android Auto, and download UI intact.

FRXE must keep resolver/backend names hidden from the normal user-facing player and download UI. Diagnostic surfaces may retain backend details.

## Version
- versionName: 0.6.12
- versionCode: 30
- release artifact: FRXE-0.6.12-APK
- This feature takes the former v0.6.12 roadmap slot; lyrics/player-animation work moves to a later version.

## Existing foundation to reuse
FRXE already has:
- youtubedl-android library 0.18.1 embedded.
- A yt-dlp-first playback resolver chain with InnerTube and NewPipe fallbacks.
- A persistent foreground download queue with pause/resume/retry/Wi-Fi-only/restart recovery.
- A Save/Downloads UI and local save pipeline.
- FFmpegKit for existing media conversion/post-processing.
- Playback/offline registry integration.

The implementation must extend these paths rather than replace them.

## Architecture

### 1. Local yt-dlp Runtime Core
Add a focused `YtDlpCore` adapter responsible for:
- initialization and health state;
- URL inspection/metadata extraction;
- format enumeration;
- playlist enumeration;
- download command construction/execution;
- progress parsing;
- cancellation;
- custom template validation;
- controlled runtime update/retry.

The adapter is the only FRXE layer that should know youtubedl-android command details.

Initialize:
- `YoutubeDL`
- youtubedl-android `FFmpeg`
- youtubedl-android `Aria2c`

Add Gradle modules matching the current 0.18.1 library:
- `io.github.junkfood02.youtubedl-android:ffmpeg:0.18.1`
- `io.github.junkfood02.youtubedl-android:aria2c:0.18.1`

Keep existing FFmpegKit for current FRXE flows unless a migrated path is explicitly proven safe.

### 2. Playback priority
Playback resolution remains strictly:
1. local yt-dlp
2. InnerTube
3. NewPipe
4. existing configured remote fallbacks only after local resolution fails

The normal player UI must only show generic states such as:
- Preparing audio…
- Preparing a backup audio source…
- Audio ready…

Resolver names remain internal diagnostics only.

If a full local resolution pass fails, FRXE may perform one controlled yt-dlp runtime refresh/retry subject to a cooldown, then continue normal fallbacks. It must not loop or repeatedly update the runtime on every tap.

### 3. URL intake
Add a new URL intake flow with two entry points:
- Paste URL inside FRXE.
- Android share target (`ACTION_SEND`, `text/plain`) so a shared link opens FRXE.

`MainActivity` normalizes the incoming text and routes valid HTTP(S) URLs to a new download/import screen without disrupting normal app launch behavior.

The intake layer never runs yt-dlp on the main thread.

### 4. URL inspection
`YtDlpCore.inspect(url)` returns a FRXE-owned model containing:
- canonical URL;
- media title;
- uploader/artist/channel;
- thumbnail URL;
- duration;
- site/extractor display category;
- whether the URL is single media or playlist;
- available audio/video formats;
- subtitle languages;
- playlist entries when applicable.

FRXE models must not expose raw library classes to UI code.

### 5. Download execution
For normal URL downloads, yt-dlp becomes the primary download engine instead of only resolving a stream URL for another downloader.

Flow:
`URL -> inspect -> user options -> DownloadQueueStore -> FrxeDownloadService -> YtDlpCore.download()`

Reuse `DownloadQueueStore` and `FrxeDownloadService` for persistence, foreground execution, pause/cancel/retry, Wi-Fi-only policy, restart recovery, notifications, and queue ordering.

The queue item must persist enough information to reconstruct the yt-dlp request after process death, including:
- source URL;
- selected format/preset;
- audio/video choice;
- subtitle preferences;
- playlist entry identity when relevant;
- metadata/embed options;
- custom template ID or normalized argument set.

### 6. aria2c
Use embedded aria2c as an optional yt-dlp external downloader for compatible requests.

Default behavior:
- enabled for large/direct segment-capable downloads where yt-dlp supports it;
- disabled when yt-dlp/HTTP behavior or site compatibility makes it unsafe;
- FRXE falls back to yt-dlp's normal downloader if aria2c fails before media data is committed.

Advanced settings may expose an `Use accelerated downloader` toggle; the normal UI should not expose the binary name.

### 7. Metadata and thumbnails
Primary path:
- use yt-dlp post-processing plus embedded mutagen capability for supported audio containers;
- embed thumbnail when supported;
- write title, artist/uploader, album/playlist where available, track/disc numbers where available, source URL, and date/year where available.

Upstream youtubedl-android has mutagen support in its Python runtime lineage. FRXE must verify mutagen availability at runtime and report capability through `YtDlpCoreCapabilities` instead of assuming it is present.

If mutagen is unavailable for a specific build/runtime, FRXE may fall back to existing FFmpeg/MediaStore metadata handling for that item, but the requested download must still complete when possible.

### 8. Subtitles
Support:
- subtitle discovery during inspection;
- manual language selection;
- optional auto-generated subtitles where yt-dlp exposes them;
- download sidecar subtitles;
- embed subtitles into compatible video containers when requested.

Audio-only downloads must ignore subtitle embedding while still allowing optional sidecar subtitle download if explicitly selected.

### 9. Playlist support
Inspection distinguishes a playlist from a single media item.

Playlist UX:
- show playlist title and item count;
- Select all / deselect all;
- allow individual item selection;
- choose one shared download preset or per-item override only when necessary;
- enqueue each selected item into the existing persistent queue;
- preserve playlist order;
- persist playlist title/index metadata for tagging when available.

A playlist is not downloaded as one opaque foreground task. Individual entries remain independently retryable/cancellable.

### 10. Custom yt-dlp templates
Add an Advanced settings area with named templates.

A template contains normalized yt-dlp arguments, not an arbitrary shell command.

Safety/robustness rules:
- no shell evaluation;
- no arbitrary executable invocation;
- FRXE owns output paths;
- block arguments that override FRXE's protected output directory, progress transport, or lifecycle controls unless explicitly whitelisted;
- validate before save and again before execution;
- store templates in app preferences/database, never in public storage by default.

Provide built-in presets for:
- best audio;
- MP3;
- M4A/Opus where available;
- best video;
- video + subtitles;
- archive/source-quality download.

### 11. UI
Add a dedicated URL Download screen reachable from:
- Save/Downloads screen;
- paste action;
- Android share target.

Normal UI states stay backend-neutral:
- Inspecting link
- Loading formats
- Preparing download
- Downloading
- Processing media
- Embedding metadata
- Complete
- Retry needed

Advanced Diagnostics may show actual resolver/downloader/runtime names.

### 12. Storage
Continue using Android-compliant app/download storage and MediaStore integration already used by FRXE.

Yt-dlp temporary output must live in FRXE-controlled cache/work directories. Final files move through the existing save/media registration path so offline playback and library discovery remain consistent.

Partial files must be cleaned or resumed according to queue state. A cancelled item must not be registered as complete.

### 13. Error handling
Classify failures into FRXE-owned categories:
- invalid/unsupported URL;
- extractor/runtime outdated;
- verification/sign-in/challenge required;
- no compatible format;
- network failure;
- storage failure;
- post-processing failure;
- subtitle/metadata non-fatal failure;
- cancelled/paused.

Non-fatal metadata/subtitle failures should not fail a successfully downloaded media file unless the user explicitly marked that output as required.

A stale runtime may trigger one controlled update/retry. Repeated failures obey cooldown and then expose a generic retry message.

### 14. Privacy and backend hiding
The normal UI must not expose:
- yt-dlp;
- aria2c;
- mutagen;
- InnerTube;
- NewPipe;
- ZEXL;
- Cobalt.

Those names are allowed only in diagnostics/logging intended for the owner/developer.

Shared URLs remain local to the device unless FRXE reaches an existing remote fallback after local paths fail.

### 15. Compatibility and add-only rule
Do not remove or replace existing FRXE capabilities.

The following must continue working:
- ordinary song tap playback;
- offline playback;
- existing Save/Downloads queue;
- pause/resume/retry/cancel/Wi-Fi-only;
- Android Auto;
- system media controls;
- TV;
- Cast;
- voice controls;
- lyrics;
- resolver diagnostics;
- existing remote download/playback fallbacks.

Existing direct-download routes remain supported.

## Implementation decomposition
This architecture is intentionally split into five independently verifiable milestones. Each milestone stays on the same `feature/v0.6.12-ytdlp-core` branch, but gets its own TDD cycle and CI gate so failures are isolated.

### Milestone 1 — Playback reliability + privacy
- finish inherited v0.6.11.1 resolver-name hiding;
- add one controlled yt-dlp refresh/retry cooldown path;
- verify ordinary song tap playback still resolves yt-dlp first;
- keep diagnostics internal.

### Milestone 2 — Runtime capabilities + URL/share inspection
- initialize youtubedl-android FFmpeg + Aria2c modules;
- add `YtDlpCoreCapabilities`;
- add URL normalization/share parsing;
- add Android share target;
- add local URL inspection and FRXE-owned format/playlist/subtitle models.

### Milestone 3 — yt-dlp-native queued downloads
- add FRXE-owned yt-dlp request builder/executor;
- route URL downloads through the existing persistent queue/service;
- persist selected format/preset and recovery data;
- integrate aria2c acceleration with safe fallback;
- keep current direct-download routes working.

### Milestone 4 — Playlist, subtitles, metadata/artwork
- expand playlists into independently queued entries;
- add subtitle selection/sidecar/embed behavior;
- verify mutagen capability at runtime;
- embed metadata/artwork where supported;
- fall back to existing FRXE post-processing when optional helpers are unavailable.

### Milestone 5 — Advanced templates + final UI
- add built-in presets and named custom templates;
- validate protected arguments;
- finish URL Download screen and advanced settings;
- full regression/device verification;
- version/release packaging and final PR review.

## Testing
Use TDD for all new behavior.

Pure unit tests:
- URL normalization/share parsing;
- yt-dlp-first priority policy;
- playback recovery cooldown;
- backend-name privacy policy;
- format/preset command construction;
- protected-argument validation;
- aria2c enable/fallback policy;
- playlist expansion/order;
- subtitle option construction;
- metadata/post-processing option construction;
- error classification;
- persisted queue request round-trip.

Integration/build verification:
- compile with youtubedl-android library + ffmpeg + aria2c modules;
- debug APK build after every milestone;
- release APK build at final milestone;
- share intent manifest validation;
- existing unit suite remains green.

Manual/device checks before calling the feature production-ready:
- paste a single URL;
- Android Share -> FRXE;
- inspect formats;
- play a tapped song;
- audio download;
- video download;
- playlist multi-item queue;
- pause/resume/retry;
- app/process restart recovery;
- metadata artwork check;
- subtitle sidecar/embed where supported;
- offline playback of completed audio;
- resolver/backend names absent from normal UI.

## Success criteria
v0.6.12 succeeds when:
- tapping a song resolves with local yt-dlp first and plays reliably;
- normal UI hides resolver/backend names;
- pasted/shared URLs can be inspected locally;
- single items and playlists can be queued through yt-dlp locally;
- aria2c acceleration, metadata/artwork, subtitles, and templates work when selected;
- existing FRXE functionality remains intact;
- unit/debug/release verification is green on the exact merge commit.
