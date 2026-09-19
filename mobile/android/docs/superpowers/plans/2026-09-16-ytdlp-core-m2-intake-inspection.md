# FRXE yt-dlp Core Milestone 2 Implementation Plan

> **Execution:** Use TDD and keep PR #9 draft. Do not remove existing Save/Downloads behavior.

**Goal:** Add a local yt-dlp runtime capability layer plus paste/share URL inspection, while keeping the current queue/download execution untouched until Milestone 3.

**Architecture:** `MainActivity` forwards shared text into `FrxeViewModel`; a pure URL parser extracts the first HTTP(S) URL; `FrxeApp` switches to the existing Save tab; `SaveScreen` can inspect the URL. `YtDlpCore` runs `yt-dlp --dump-single-json --flat-playlist --skip-download` off the main thread and maps JSON into FRXE-owned models. The UI only shows generic statuses and media metadata/formats, never backend names.

## Constraints
- yt-dlp remains first playback resolver from Milestone 1.
- Existing queue enqueue button and SaveRequest path remain available.
- `ACTION_SEND` handling is additive and must not break launcher/TV intents.
- No shell execution or arbitrary command templates yet.
- No yt-dlp-native file downloads yet; those start in Milestone 3.
- Shared/pasted URLs stay local during inspection.
- Normal UI must not expose yt-dlp/aria2/ffmpeg/backend names.

---

### Task 1 — URL/share parsing (TDD)

**Create:**
- `app/src/main/java/com/frxe/music/intake/UrlIntakeParser.kt`
- `app/src/test/java/com/frxe/music/intake/UrlIntakeParserTest.kt`

**Behavior:**
- accept a direct `http://` or `https://` URL;
- extract the first HTTP(S) URL from ordinary shared text;
- trim common trailing punctuation such as `)`, `]`, `}`, `,`, `.`, `!`;
- reject non-http schemes and blank input;
- preserve query/fragment content.

**TDD:** write failing tests first, confirm RED, implement minimum parser, confirm GREEN.

---

### Task 2 — FRXE inspection models + JSON parser (TDD)

**Create:**
- `app/src/main/java/com/frxe/music/ytdlp/YtDlpInspectionModels.kt`
- `app/src/main/java/com/frxe/music/ytdlp/YtDlpInspectionParser.kt`
- `app/src/test/java/com/frxe/music/ytdlp/YtDlpInspectionParserTest.kt`

**Models:**
- `YtDlpInspection`
- `YtDlpMediaFormat`
- `YtDlpPlaylistEntry`
- subtitle language list / basic subtitle availability

**Parse fields:**
- canonical webpage URL, id, title, uploader/artist/channel fallback, thumbnail, duration;
- extractor/site category kept internal to model, not displayed as backend identity;
- formats: id, ext, audio/video codecs, bitrate, width/height/fps, approximate/file size;
- subtitles + automatic captions language keys when present;
- playlist entries in source order with id/title/url/thumbnail/duration;
- playlist detection via `_type == "playlist"` or non-empty entries.

Use Jackson `ObjectMapper` / `JsonNode` for parsing. Jackson is already on the app compile/runtime classpath through youtubedl-android and works in local JVM unit tests, avoiding Android `org.json` stub behavior. UI code never receives Jackson or youtubedl-android mapper classes.

**TDD fixtures:** one single-media JSON fixture and one playlist fixture with subtitles/formats.

---

### Task 3 — Add runtime modules and capability state

**Modify:**
- `app/build.gradle.kts`
- `app/src/main/java/com/frxe/music/FrxeApplication.kt`

**Create:**
- `app/src/main/java/com/frxe/music/ytdlp/YtDlpCoreCapabilities.kt`
- `app/src/main/java/com/frxe/music/ytdlp/YtDlpCore.kt`

**Dependencies (same pinned 0.18.1 family):**
- `io.github.junkfood02.youtubedl-android:ffmpeg:0.18.1`
- `io.github.junkfood02.youtubedl-android:aria2c:0.18.1`

Initialize idempotently in app startup:
- `YoutubeDL.getInstance().init(context)`
- `FFmpeg.getInstance().init(context)`
- `Aria2c.getInstance().init(context)`

Capability failures are recorded independently: yt-dlp inspection can still work if optional ffmpeg/aria2 initialization fails. Do not crash app startup.

Add/protect release keep rules if compilation/shrinking requires them; do not remove existing rules.

**Verification:** full unit/debug build after dependencies + initialization.

---

### Task 4 — Local inspection command builder and executor (TDD)

**Create:**
- `app/src/main/java/com/frxe/music/ytdlp/YtDlpInspectCommandPolicy.kt`
- `app/src/test/java/com/frxe/music/ytdlp/YtDlpInspectCommandPolicyTest.kt`

**Policy output:** normalized options for inspection:
- `--dump-single-json`
- `--flat-playlist`
- `--skip-download`
- `--no-warnings`
- `--ignore-config`

No output path, downloader override, postprocessor, or custom user args in Milestone 2.

**YtDlpCore.inspect(url):**
1. normalize/validate URL;
2. build `YoutubeDLRequest`;
3. add policy options;
4. execute on `Dispatchers.IO` with an inspection process id;
5. parse `response.out` using `YtDlpInspectionParser`;
6. map exceptions into generic `YtDlpInspectionResult.Failure(message)` without leaking backend binary names.

Expose cancellation by process id internally for future UI cancellation.

---

### Task 5 — ViewModel inspection state and share intake (TDD where pure)

**Modify:**
- `app/src/main/java/com/frxe/music/ui/FrxeViewModel.kt`
- `app/src/main/java/com/frxe/music/MainActivity.kt`

**Create:**
- `app/src/main/java/com/frxe/music/ytdlp/YtDlpInspectionUiState.kt`

`FrxeViewModel` adds:
- `pendingExternalUrl: StateFlow<String?>`
- `inspectionState: StateFlow<YtDlpInspectionUiState>`
- `acceptSharedText(text)` using `UrlIntakeParser`
- `consumeExternalUrl()`
- `inspectUrl(url)` with a cancellable `Job`
- `clearInspection()`

`MainActivity` switches from an in-Compose-created VM to one activity-scoped `by viewModels<FrxeViewModel>()`, passes the same VM into `FrxeApp`, handles the launch intent, and overrides `onNewIntent` for singleTask share deliveries.

Only accept `Intent.ACTION_SEND` + `text/plain` text. Existing launcher behavior remains unchanged.

---

### Task 6 — Manifest share target

**Modify:** `app/src/main/AndroidManifest.xml`

Add a second `intent-filter` to `MainActivity`:
- action `android.intent.action.SEND`
- category `android.intent.category.DEFAULT`
- data mimeType `text/plain`

Do not change/remove launcher or Leanback filters.

---

### Task 7 — Save screen inspection UI

**Modify:**
- `app/src/main/java/com/frxe/music/ui/FrxeApp.kt`
- `app/src/main/java/com/frxe/music/ui/screens/SaveScreen.kt`

`FrxeApp` observes `pendingExternalUrl`; when non-null, switch tab to `FrxeTab.Save`.

`SaveScreen`:
- imports pending shared URL into its existing URL field and starts inspection;
- adds an `Inspect link` action beside/above the existing enqueue action;
- while inspecting: show generic `Inspecting link…`;
- on success: show title, creator, duration, single vs playlist, format count, subtitle language count, and playlist item count;
- keep thumbnail URL in the inspection model for later metadata/artwork work, but use FRXE's existing generated artwork in this milestone rather than adding a new remote-image dependency;
- show a compact format list (format/ext, audio/video, quality, approximate size) without backend names;
- preserve the current manual title/artist/format/quality controls and current `Add to download queue` behavior until Milestone 3.

Also remove the current normal queue-card `item.backend` label from `DownloadQueueCard`; backend remains persisted for diagnostics, just hidden in ordinary UI.

---

### Task 8 — Milestone 2 verification

Run exact-head CI:
- `:app:testDebugUnitTest`
- `:app:assembleDebug`

Review diff for:
- no existing Save/queue path removed;
- launcher/TV behavior preserved;
- share intent additive;
- inspection runs off main thread;
- no backend names in normal inspection/download UI;
- no file download path changed yet;
- yt-dlp-first playback behavior from Milestone 1 intact.

Add a Milestone 2 checkpoint comment to draft PR #9. Keep PR draft.
