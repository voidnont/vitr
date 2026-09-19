# FRXE yt-dlp Core Milestone 3 Native Downloads Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Route inspected URL downloads through embedded local yt-dlp inside FRXE's existing persistent queue/service while preserving every current legacy/direct/remote fallback path.

**Architecture:** Add a FRXE-owned persisted native-download request alongside the legacy `SaveRequest`. Queue items record an execution kind plus a versioned JSON request payload; old persisted items default to the legacy route. A new native pipeline lets yt-dlp perform the network fetch into FRXE-controlled cache, then reuses `FrxeSaveEngine` for current audio transcoding/MediaStore export. aria2c is optional and may be retried once without acceleration if it fails before a final media file is committed.

**Tech Stack:** Kotlin, Android foreground service, SharedPreferences/JSON queue persistence, kotlinx.coroutines, youtubedl-android 0.18.1, embedded aria2c 0.18.1, existing FFmpegKit/MediaStore save path, JUnit 4.

**Spec:** `docs/superpowers/specs/2026-09-16-ytdlp-core-design.md`

## Global Constraints
- Keep `feature/v0.6.12-ytdlp-core`; never implement this milestone on `main`.
- Existing direct-download, resolver, Zexl/Cobalt fallback, pause/resume/retry/cancel, Wi-Fi-only, restart recovery and completed-download registry behavior must remain supported.
- Normal UI copy remains backend-neutral; diagnostics may retain engine identity.
- FRXE owns all output/work paths; no shell evaluation.
- Native yt-dlp requests persist enough state to reconstruct execution after process death.
- yt-dlp is the primary engine for inspected URL downloads; legacy manual/direct routes remain additive fallbacks.
- aria2c is optional, capability-gated and retries once without acceleration when safe.
- Every implementation slice uses TDD and exact-head `:app:testDebugUnitTest :app:assembleDebug` verification.

---

### Task 1: Persisted Native Download Request Model and Codec

**Files:**
- Create: `app/src/main/java/com/frxe/music/ytdlp/YtDlpDownloadModels.kt`
- Create: `app/src/main/java/com/frxe/music/ytdlp/YtDlpDownloadRequestCodec.kt`
- Test: `app/src/test/java/com/frxe/music/ytdlp/YtDlpDownloadRequestCodecTest.kt`

**Interfaces:**
- Produces `YtDlpMediaKind`, `YtDlpDownloadRequest`, and `YtDlpDownloadRequestCodec.encode/decode`.
- The model includes source URL, title, artist, media kind, existing `SaveFormat`/`SaveQuality`, optional format selector, playlist entry identity/index/title, subtitle languages/auto-subtitle flag, metadata/artwork flags, template identity/normalized args, and accelerated-downloader preference.

- [ ] Write a failing round-trip test covering every field, including lists and nullable playlist/template fields.
- [ ] Write a failing backward-compatibility test proving absent optional fields decode to safe defaults.
- [ ] Run exact-head unit/debug CI and confirm RED because the model/codec does not exist.
- [ ] Implement immutable models with conservative defaults.
- [ ] Implement JSON encode/decode with a schema version and tolerant optional-field reads; reject blank source URLs.
- [ ] Re-run exact-head CI and confirm GREEN.

---

### Task 2: Queue Execution Kind and Backward-Compatible Persistence

**Files:**
- Modify: `app/src/main/java/com/frxe/music/save/DownloadQueueModel.kt`
- Modify: `app/src/main/java/com/frxe/music/save/DownloadQueueStore.kt`
- Test: `app/src/test/java/com/frxe/music/save/DownloadQueuePolicyTest.kt`
- Test: `app/src/test/java/com/frxe/music/save/DownloadQueuePersistencePolicyTest.kt`

**Interfaces:**
- Add `DownloadExecutionKind { Legacy, YtDlp }`.
- Add `executionKind: DownloadExecutionKind = Legacy` and `engineRequestJson: String? = null` to `DownloadQueueItem`.
- Add `DownloadQueueStore.enqueueNative(request: YtDlpDownloadRequest)` while keeping `enqueue(SaveRequest)` unchanged.

- [ ] Write failing tests for native duplicate identity and legacy compatibility.
- [ ] Extract pure JSON item encode/decode helpers from Android store persistence so JVM tests can validate round-trips without Android stubs.
- [ ] Test that persisted items missing `executionKind` and `engineRequestJson` decode as `Legacy`.
- [ ] Implement native enqueue using source/format/quality identity and persisted request JSON.
- [ ] Preserve current `KEY_ITEMS`, queue ordering, recovery and history bounds.
- [ ] Run exact-head CI GREEN.

---

### Task 3: Safe Native Command Builder and aria2c Policy

**Files:**
- Create: `app/src/main/java/com/frxe/music/ytdlp/YtDlpDownloadCommandPolicy.kt`
- Create: `app/src/main/java/com/frxe/music/ytdlp/YtDlpAria2Policy.kt`
- Test: `app/src/test/java/com/frxe/music/ytdlp/YtDlpDownloadCommandPolicyTest.kt`
- Test: `app/src/test/java/com/frxe/music/ytdlp/YtDlpAria2PolicyTest.kt`

**Interfaces:**
- `YtDlpDownloadCommandPolicy.arguments(request, outputTemplate, useAria2c): List<YtDlpOption>` where `YtDlpOption` carries option + optional argument without shell parsing.
- `YtDlpAria2Policy.shouldUse(request, capabilities): Boolean`.

- [ ] Write failing tests proving `--ignore-config`, one FRXE-owned `--output`, `--no-playlist` for single items, audio/video format selection and no user-controlled output path.
- [ ] Test that custom/template args are not used in Milestone 3 even if persisted for later milestones.
- [ ] Test aria2 is enabled only when requested and runtime capability is ready.
- [ ] Test aria2 option construction uses library-compatible external downloader settings without shell commands.
- [ ] Implement minimum builder/policy and run exact-head CI GREEN.

---

### Task 4: yt-dlp Native Fetch Executor

**Files:**
- Modify: `app/src/main/java/com/frxe/music/ytdlp/YtDlpCore.kt`
- Create: `app/src/main/java/com/frxe/music/ytdlp/YtDlpDownloadResult.kt`
- Test: pure command/progress behavior remains in Task 3; Android execution is compile/integration verified.

**Interfaces:**
- `suspend fun YtDlpCore.download(request, workDir, processId, onProgress): YtDlpDownloadResult`
- `YtDlpDownloadResult` returns the produced local media file and nonfatal sidecar files if any.
- `cancelDownload(processId)` delegates to the embedded process registry.

- [ ] Create a unique per-item work directory and deterministic FRXE-owned output template.
- [ ] Execute using `YoutubeDLRequest` + normalized options only.
- [ ] Convert embedded callback percent to `0f..1f` and emit generic progress state.
- [ ] Locate the produced media file only inside the work directory; reject missing/empty/out-of-tree results.
- [ ] On accelerated attempt failure with no usable media result, retry once without aria2c.
- [ ] Never retry on coroutine cancellation.
- [ ] Clean temporary partials on terminal failure/cancel while allowing service restart recovery to recreate from persisted request.
- [ ] Exact-head CI GREEN.

---

### Task 5: Reuse Existing Transcode and MediaStore Export for Local Input

**Files:**
- Modify: `app/src/main/java/com/frxe/music/save/FrxeSaveEngine.kt`
- Test: `app/src/test/java/com/frxe/music/save/FrxeSaveEnginePolicyTest.kt` for pure ownership/naming policy if needed.

**Interfaces:**
- Add `suspend fun saveLocalInput(input: File, request: SaveRequest, onState): SaveResult`.
- Existing `save(SaveRequest, onState)` remains behavior-compatible.

- [ ] Extract the post-download transcode/export body to a shared private function.
- [ ] Preserve current direct HTTP download behavior in `save()`.
- [ ] Ensure `saveLocalInput()` never deletes caller-owned input; it may delete only its generated output temp.
- [ ] Preserve current FFmpegKit cancellation behavior and MediaStore destination `Music/Frxe`.
- [ ] Exact-head CI GREEN.

---

### Task 6: Native Download Pipeline and Existing Fallback Compatibility

**Files:**
- Create: `app/src/main/java/com/frxe/music/save/YtDlpNativeDownloadPipeline.kt`
- Modify: `app/src/main/java/com/frxe/music/save/DownloadPipeline.kt` only where needed for shared cancellation or fallback entry points.
- Test: `app/src/test/java/com/frxe/music/save/NativeDownloadRoutingPolicyTest.kt`

**Interfaces:**
- `YtDlpNativeDownloadPipeline.save(request, queueItemId, onState): SaveResult`
- `cancel(itemId)` cancels embedded yt-dlp and FFmpeg processing.

- [ ] Write failing route tests proving inspected/native items choose the native path while legacy items keep old pipeline behavior.
- [ ] Native pipeline emits backend-neutral states: Preparing download, Downloading, Processing media, Saving to Music/Frxe.
- [ ] Invoke `YtDlpCore.download`, then `FrxeSaveEngine.saveLocalInput` for current audio formats.
- [ ] Delete native work directory in `finally` after success/failure/cancel.
- [ ] Keep current `DownloadPipeline.save(SaveRequest)` untouched for legacy/direct/Zexl/Cobalt behavior.
- [ ] Exact-head CI GREEN.

---

### Task 7: Foreground Queue Service Native Execution

**Files:**
- Modify: `app/src/main/java/com/frxe/music/save/FrxeDownloadService.kt`
- Test: queue/request conversion policy tests in pure JVM code.

**Interfaces:**
- Service branches on `DownloadQueueItem.executionKind`.
- `Legacy` uses existing `DownloadPipeline` and `toSaveRequest()`.
- `YtDlp` decodes `engineRequestJson` and uses `YtDlpNativeDownloadPipeline`.

- [ ] Add pure decoder/helper test for invalid native payload -> generic failed item instead of service crash.
- [ ] Initialize both pipelines in `onCreate`.
- [ ] Preserve one-at-a-time queue processing, foreground notification, Wi-Fi-only wait, wakelock and auto-retry policy.
- [ ] Pause/cancel must cancel whichever pipeline owns the running item.
- [ ] Sanitize normal notification content through `DownloadUiPrivacyPolicy` as well as Compose UI.
- [ ] Preserve current complete/fail/requeue registry behavior.
- [ ] Exact-head CI GREEN.

---

### Task 8: Enqueue Inspected URLs as Native Requests

**Files:**
- Modify: `app/src/main/java/com/frxe/music/ui/screens/SaveScreen.kt`
- Modify: `app/src/main/java/com/frxe/music/save/FrxeDownloadService.kt` companion API

**Interfaces:**
- Add `FrxeDownloadService.enqueue(context, request: YtDlpDownloadRequest)` overload.
- Use native enqueue only when the current successful inspection URL corresponds to the current URL field; otherwise retain legacy manual enqueue.

- [ ] Add a pure matching policy test so stale inspection results cannot enqueue the wrong URL natively.
- [ ] Build a native request from inspected metadata plus current audio format/quality controls.
- [ ] Keep ordinary manual/direct URL enqueue unchanged when there is no matching successful inspection.
- [ ] Keep user-visible copy engine-neutral.
- [ ] Exact-head CI GREEN.

---

### Task 9: Milestone 3 Regression and PR Checkpoint

**Files:**
- No production change unless verification exposes a defect.

- [ ] Review diff against the Milestone 3 section of the design spec.
- [ ] Confirm no existing queue actions/routes were deleted.
- [ ] Run exact-head `:app:testDebugUnitTest :app:assembleDebug` through CI.
- [ ] Confirm normal UI/notification text contains no hidden backend names.
- [ ] Add a Milestone 3 checkpoint comment to draft PR #9 with exact verified head.
- [ ] Keep PR draft and continue to Milestone 4.
