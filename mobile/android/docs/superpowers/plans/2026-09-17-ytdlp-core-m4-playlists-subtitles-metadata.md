# FRXE yt-dlp Core Milestone 4 Playlists, Subtitles, Metadata and Artwork Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Expand inspected playlists into independently queued native downloads, add subtitle selection/sidecars and optional video embedding, and carry metadata/artwork through the native download path without breaking legacy downloads.

**Architecture:** Keep the Milestone 3 persistent queue/service and native executor. Add pure policies for subtitle options and playlist expansion, extend the persisted native request conservatively, probe bundled mutagen capability without network access, and pass final-output metadata/artwork into the existing FRXE save/transcode layer so post-download conversion does not silently discard it. UI remains backend-neutral and legacy/manual enqueue remains additive.

**Tech Stack:** Kotlin, Android foreground service, kotlinx.coroutines, Jackson, youtubedl-android 0.18.1, embedded FFmpeg/aria2c, existing FFmpegKit/MediaStore save path, JUnit 4.

**Spec:** `docs/superpowers/specs/2026-09-16-ytdlp-core-design.md`

## Global Constraints
- Stay on `feature/v0.6.12-ytdlp-core`; PR #9 remains draft.
- Existing playback, offline, Android Auto, TV, Cast, voice, lyrics, legacy/direct downloads, pause/resume/retry/cancel, Wi-Fi-only, restart recovery and remote fallbacks remain supported.
- Normal UI and notifications must not expose yt-dlp, aria2c, mutagen, InnerTube, NewPipe, ZEXL or Cobalt names.
- Playlist entries are queued independently and preserve source order.
- Audio downloads may write subtitle sidecars but never request subtitle embedding into the audio container.
- FRXE owns output/work paths; no shell evaluation and no custom template arguments in Milestone 4.
- Metadata/artwork failures are non-fatal unless media output itself fails.
- Every behavior slice follows RED -> GREEN and exact-head `:app:testDebugUnitTest :app:assembleDebug` verification.

---

### Task 1: Subtitle Sidecar Command Policy

**Files:**
- Modify: `app/src/main/java/com/frxe/music/ytdlp/YtDlpDownloadCommandPolicy.kt`
- Modify: `app/src/test/java/com/frxe/music/ytdlp/YtDlpDownloadCommandPolicyTest.kt`

**Interfaces:**
- Consumes: existing `YtDlpDownloadRequest.subtitleLanguages` and `writeAutoSubtitles`.
- Produces: normalized yt-dlp options `--write-subs`, `--sub-langs`, and `--write-auto-subs` without shell parsing.

- [ ] **Step 1: Add failing sidecar-option tests**

Add tests that construct a request with `subtitleLanguages = listOf("en", "de")` and `writeAutoSubtitles = true`, then assert:
```kotlin
assertTrue(options.contains(YtDlpOption("--write-subs")))
assertTrue(options.contains(YtDlpOption("--sub-langs", "en,de")))
assertTrue(options.contains(YtDlpOption("--write-auto-subs")))
assertFalse(options.any { it.option == "--embed-subs" })
```
Add a second test proving an empty language list plus `writeAutoSubtitles = false` emits none of the subtitle options.

- [ ] **Step 2: Run focused test and verify RED**

Run:
```bash
gradle --no-daemon :app:testDebugUnitTest --tests com.frxe.music.ytdlp.YtDlpDownloadCommandPolicyTest
```
Expected: assertion failure because current command construction emits no subtitle options.

- [ ] **Step 3: Implement minimum sidecar options**

In `arguments(...)`, after format selection add:
```kotlin
val subtitleLanguages = request.subtitleLanguages
    .map(String::trim)
    .filter(String::isNotEmpty)
    .distinct()

if (subtitleLanguages.isNotEmpty()) {
    add(YtDlpOption("--write-subs"))
    add(YtDlpOption("--sub-langs", subtitleLanguages.joinToString(",")))
}
if (request.writeAutoSubtitles) {
    add(YtDlpOption("--write-auto-subs"))
}
```
Do not add `--embed-subs` yet.

- [ ] **Step 4: Run focused test GREEN, then full exact-head CI**

Run:
```bash
gradle --no-daemon :app:testDebugUnitTest --tests com.frxe.music.ytdlp.YtDlpDownloadCommandPolicyTest
gradle --no-daemon --stacktrace :app:testDebugUnitTest :app:assembleDebug
```
Expected: PASS.

- [ ] **Step 5: Commit**

Commit message:
```text
feat(downloads): add subtitle sidecar options
```

---

### Task 2: Persist Subtitle Embedding and Rich Metadata Fields

**Files:**
- Modify: `app/src/main/java/com/frxe/music/ytdlp/YtDlpDownloadModels.kt`
- Modify: `app/src/main/java/com/frxe/music/ytdlp/YtDlpDownloadRequestCodec.kt`
- Modify: `app/src/test/java/com/frxe/music/ytdlp/YtDlpDownloadRequestCodecTest.kt`

**Interfaces:**
- Add to `YtDlpDownloadRequest`: `embedSubtitles: Boolean = false`, `thumbnailUrl: String? = null`, `album: String? = null`, `trackNumber: Int? = null`, `discNumber: Int? = null`, `releaseYear: Int? = null`.
- Existing absent fields decode to conservative defaults.

- [ ] **Step 1: Extend round-trip test and verify RED**

Set every new field in `requestRoundTripsEveryPersistedField` and assert object equality after encode/decode. Extend the backward-compatibility test with:
```kotlin
assertFalse(decoded.embedSubtitles)
assertNull(decoded.thumbnailUrl)
assertNull(decoded.album)
assertNull(decoded.trackNumber)
assertNull(decoded.discNumber)
assertNull(decoded.releaseYear)
```
Run the focused codec test; expected RED until model/codec are extended.

- [ ] **Step 2: Extend immutable model and codec minimally**

Encode each nullable value only when present. Decode booleans through the existing tolerant helper and numeric fields only from integral JSON nodes. Ignore an invalid/non-positive track or disc number by returning null; accept release years in `1000..9999` only.

- [ ] **Step 3: Run focused + full CI GREEN and commit**

Run:
```bash
gradle --no-daemon :app:testDebugUnitTest --tests com.frxe.music.ytdlp.YtDlpDownloadRequestCodecTest
gradle --no-daemon --stacktrace :app:testDebugUnitTest :app:assembleDebug
```
Commit message:
```text
feat(downloads): persist subtitle and metadata preferences
```

---

### Task 3: Rich Inspection Metadata

**Files:**
- Modify: `app/src/main/java/com/frxe/music/ytdlp/YtDlpInspectionModels.kt`
- Modify: `app/src/main/java/com/frxe/music/ytdlp/YtDlpInspectionParser.kt`
- Modify: `app/src/test/java/com/frxe/music/ytdlp/YtDlpInspectionParserTest.kt`

**Interfaces:**
- Add to `YtDlpInspection`: `album`, `playlistTitle`, `playlistIndex`, `trackNumber`, `discNumber`, `releaseYear`.
- Add to `YtDlpPlaylistEntry`: `creator`, `playlistIndex`.
- Parse source fields with fallbacks: album <- `album`; playlist title <- `playlist_title` then `playlist`; playlist index <- `playlist_index`; track <- `track_number`; disc <- `disc_number`; year <- numeric `release_year`, otherwise first four digits of `release_date`/`upload_date` when valid.

- [ ] **Step 1: Add a failing metadata fixture test**

Use JSON containing:
```json
{
  "title":"Track",
  "artist":"Artist",
  "album":"Album",
  "playlist_title":"Collection",
  "playlist_index":3,
  "track_number":7,
  "disc_number":2,
  "release_date":"20240517",
  "thumbnail":"https://img.example/cover.jpg"
}
```
Assert all six new metadata values plus the existing thumbnail.

- [ ] **Step 2: Add playlist-entry creator/index assertions**

Extend the playlist fixture with `uploader` and `playlist_index` per entry and assert source order remains unchanged.

- [ ] **Step 3: Implement parser/model, run focused + full CI GREEN, commit**

Commit message:
```text
feat(inspection): retain playlist and tag metadata
```

---

### Task 4: Playlist Expansion Policy

**Files:**
- Create: `app/src/main/java/com/frxe/music/save/PlaylistDownloadExpansionPolicy.kt`
- Create: `app/src/test/java/com/frxe/music/save/PlaylistDownloadExpansionPolicyTest.kt`

**Interfaces:**
- Produce:
```kotlin
object PlaylistDownloadExpansionPolicy {
    fun expand(
        inspection: YtDlpInspection,
        selectedEntryIds: Set<String>,
        baseRequest: YtDlpDownloadRequest
    ): List<YtDlpDownloadRequest>
}
```
- Preserve inspection entry order; one request per selected entry with a usable HTTP(S) URL; copy format/quality/subtitle/metadata preferences from `baseRequest`; replace source/title/playlist identity/index/thumbnail with entry-specific data.

- [ ] **Step 1: Write RED tests**

Cover: selected entries remain in source order; unselected entries are skipped; entries without an HTTP(S) URL are skipped; entry title/creator/thumbnail and parent playlist title/index are propagated; base request preferences survive unchanged.

- [ ] **Step 2: Implement minimum pure policy**

Normalize entry URL with `UrlIntakeParser.extractFirstHttpUrl`. Use `entry.id` as selection identity; entries with blank IDs are selectable by a stable fallback key `index:<1-based-source-index>` exposed through a small `selectionKey(entry, index)` helper in the same object.

- [ ] **Step 3: Run focused + full CI GREEN and commit**

Commit message:
```text
feat(downloads): expand playlists into queue requests
```

---

### Task 5: Offline Mutagen Capability Probe

**Files:**
- Create: `app/src/main/java/com/frxe/music/ytdlp/PythonRuntimeCapabilityProbe.kt`
- Create: `app/src/test/java/com/frxe/music/ytdlp/PythonRuntimeCapabilityProbeTest.kt`
- Modify: `app/src/main/java/com/frxe/music/ytdlp/YtDlpCoreCapabilities.kt`
- Modify: `app/src/main/java/com/frxe/music/ytdlp/YtDlpCore.kt`
- Modify: `app/src/test/java/com/frxe/music/ytdlp/YtDlpCoreCapabilitiesTest.kt`

**Interfaces:**
- Add `mutagenReady: Boolean = false` and enum `Mutagen` to capabilities.
- Produce pure probe:
```kotlin
object PythonRuntimeCapabilityProbe {
    fun hasMutagen(pythonBundle: File): Boolean
}
```
- The probe opens `libpython.zip.so` as a ZIP and returns true only if an entry path contains `/site-packages/mutagen/__init__.py` or starts with a `mutagen/` package path; any I/O/ZIP error returns false.

- [ ] **Step 1: RED tests with temporary ZIP fixtures**

Create one ZIP containing `usr/lib/python3.11/site-packages/mutagen/__init__.py` and one without it. Assert true/false and assert a missing file returns false.

- [ ] **Step 2: Implement pure probe and capability field**

Use `java.util.zip.ZipFile` and close it with `use`.

- [ ] **Step 3: Integrate probe after runtime initialization**

In `YtDlpCore.initialize`, inspect `File(appContext.applicationInfo.nativeLibraryDir, "libpython.zip.so")`. Record this independently; a probe failure must not change yt-dlp/FFmpeg/aria2 readiness or crash startup.

- [ ] **Step 4: Run focused + full CI GREEN and commit**

Commit message:
```text
feat(runtime): detect bundled metadata capability
```

---

### Task 6: Subtitle Embedding, Metadata and Artwork Download Options

**Files:**
- Modify: `app/src/main/java/com/frxe/music/ytdlp/YtDlpDownloadCommandPolicy.kt`
- Modify: `app/src/test/java/com/frxe/music/ytdlp/YtDlpDownloadCommandPolicyTest.kt`
- Modify: `app/src/main/java/com/frxe/music/ytdlp/YtDlpDownloadExecutor.kt`

**Interfaces:**
- Extend command builder signature with `capabilities: YtDlpCoreCapabilities` rather than a bare aria flag, or pass explicit booleans derived before command construction.
- `embedSubtitles && mediaKind == Video` emits `--embed-subs`; audio never emits it.
- `embedMetadata` emits `--embed-metadata`.
- `embedThumbnail` emits `--write-thumbnail`; emit `--embed-thumbnail` only when the runtime has a supported metadata path (`mutagenReady || ffmpegReady`).
- Metadata/artwork post-processing failure after a usable media file exists is treated as non-fatal where the executor can still locate a valid media result.

- [ ] **Step 1: Add RED command-policy tests**

Assert video subtitle embedding, audio non-embedding, metadata option, thumbnail sidecar request, and capability-gated thumbnail embedding. Keep existing protected-output and no-`--exec` assertions.

- [ ] **Step 2: Implement normalized options only**

Do not consume `normalizedTemplateArgs` or allow output/postprocessor overrides from persisted user data.

- [ ] **Step 3: Extend executor result recovery**

After execution errors, keep the existing `findResult(...)` recovery path. If a usable media file exists, return it with discovered sidecars rather than failing solely because optional subtitle/metadata/artwork post-processing reported an error. Cancellation remains terminal and never retries.

- [ ] **Step 4: Run focused + full CI GREEN and commit**

Commit message:
```text
feat(downloads): add subtitle metadata and artwork options
```

---

### Task 7: Playlist and Subtitle Controls in Save UI

**Files:**
- Modify: `app/src/main/java/com/frxe/music/ui/screens/SaveScreen.kt`
- Test pure selection/expansion behavior through `PlaylistDownloadExpansionPolicyTest`; Compose behavior is build-verified.

**Interfaces:**
- For playlist inspection success, keep a selected-key set initialized to all entries with usable URLs.
- Add backend-neutral `Select all` / `Deselect all` actions and one checkbox/toggle row per playlist entry.
- Add subtitle language selection chips from the union of manual and automatic language keys; add `Include automatic captions` and `Embed subtitles in video` preferences only when applicable.
- `Add to download queue` expands the selected playlist into individual `YtDlpDownloadRequest`s and enqueues them in source order.
- For a single inspected item, build one native request carrying thumbnail/album/track/disc/year plus subtitle preferences.
- Manual/uninspected URL enqueue remains the legacy `SaveRequest` path.

- [ ] **Step 1: Wire playlist selection to the pure expansion policy**

Reset selection when a different successful inspection URL arrives so stale selection cannot apply to a new playlist.

- [ ] **Step 2: Add subtitle controls and native-request metadata mapping**

Do not display runtime/downloader names. Keep existing format/quality controls.

- [ ] **Step 3: Preserve enqueue messaging**

For multi-item enqueue, report a generic summary such as `Added 5 items to download queue`; duplicates are counted separately without exposing execution engine details.

- [ ] **Step 4: Full exact-head CI GREEN and commit**

Commit message:
```text
feat(downloads): add playlist and subtitle intake controls
```

---

### Task 8: Milestone 4 Regression and PR Checkpoint

**Files:**
- No production change unless verification exposes a defect.

- [ ] Review the Milestone 4 diff against the design spec.
- [ ] Confirm playlist expansion produces independent persisted queue items in source order.
- [ ] Confirm audio requests never embed subtitles and selected sidecars remain supported.
- [ ] Confirm mutagen probing is offline, independent and non-crashing.
- [ ] Confirm metadata/artwork failure cannot discard an otherwise usable media file.
- [ ] Confirm normal UI/notification text contains no backend names.
- [ ] Confirm manual/legacy downloads, pause/resume/retry/cancel, Wi-Fi-only and restart recovery remain routed as before.
- [ ] Run exact-head GitHub Actions for `:app:testDebugUnitTest :app:assembleDebug` and require success.
- [ ] Add a Milestone 4 checkpoint comment to draft PR #9 with exact verified head.
- [ ] Keep PR draft and continue to Milestone 5.
