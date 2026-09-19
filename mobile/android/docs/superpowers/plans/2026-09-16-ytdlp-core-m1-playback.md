# FRXE yt-dlp Core Milestone 1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make FRXE playback reliably try local yt-dlp first, perform at most one controlled yt-dlp runtime refresh/retry after a full local resolution failure, and keep resolver/backend names hidden from normal playback UI.

**Architecture:** Keep the existing `PlaybackStreamResolver` and resolver chain intact. Add recovery orchestration to `YtDlpRuntimeUpdater`, reuse the already-tested cooldown policy, and retry the same local resolver order once before the existing remote fallback. Keep resolver identity in internal status/diagnostics fields while exposing only generic user-facing messages.

**Tech Stack:** Kotlin, Android, coroutines, Media3, youtubedl-android 0.18.1, JUnit 4, GitHub Actions/Gradle 9.6.0.

**Spec:** `docs/superpowers/specs/2026-09-16-ytdlp-core-design.md`

## Global Constraints

- Additive-only: do not remove existing playback, queue, offline, Android Auto, TV, Cast, voice, lyrics, downloads, diagnostics, or remote fallback behavior.
- Playback local resolver order remains exactly `YtDlp -> InnerTube -> NewPipe`.
- Normal UI must not expose `yt-dlp`, `InnerTube`, `NewPipe`, `ZEXL`, or `Cobalt` names.
- Runtime refresh retry is allowed at most once per 15-minute cooldown.
- Verification/challenge results must not trigger an automatic runtime update loop.
- Existing configured remote fallback runs only after the local retry path is exhausted.
- Milestone 1 does not add URL/share/download-engine features yet.

---

### Task 1: Verify inherited privacy and recovery-policy tests

**Files:**
- Existing test: `app/src/test/java/com/frxe/music/source/PlaybackResolutionPrivacyTest.kt`
- Existing test: `app/src/test/java/com/frxe/music/source/YtDlpPlaybackRecoveryPolicyTest.kt`
- Existing source: `app/src/main/java/com/frxe/music/source/PlaybackResolutionMonitor.kt`
- Existing source: `app/src/main/java/com/frxe/music/source/YtDlpPlaybackRecoveryPolicy.kt`

**Interfaces:**
- Consumes: `PlaybackResolutionMonitor` public status methods and `YtDlpPlaybackRecoveryPolicy.shouldAttempt(lastAttemptAtMs, nowMs)`.
- Produces: a verified baseline before integration work.

- [ ] **Step 1: Run the two focused tests on the exact feature head**

Run:
```bash
gradle --no-daemon :app:testDebugUnitTest --tests com.frxe.music.source.PlaybackResolutionPrivacyTest --tests com.frxe.music.source.YtDlpPlaybackRecoveryPolicyTest
```
Expected: PASS.

- [ ] **Step 2: Run the full unit/debug build baseline**

Run:
```bash
gradle --no-daemon --stacktrace :app:testDebugUnitTest :app:assembleDebug
```
Expected: PASS with only existing non-fatal warnings.

- [ ] **Step 3: Commit only if verification requires a correction**

If no correction is required, do not create a no-op commit.

---

### Task 2: Add updater-owned playback recovery gate

**Files:**
- Modify: `app/src/main/java/com/frxe/music/updates/YtDlpRuntimeUpdater.kt`
- Modify: `app/src/main/java/com/frxe/music/source/YtDlpPlaybackRecoveryPolicy.kt`
- Test: `app/src/test/java/com/frxe/music/source/YtDlpPlaybackRecoveryPolicyTest.kt`

**Interfaces:**
- Consumes: `YtDlpPlaybackRecoveryPolicy.shouldAttempt(lastAttemptAtMs, nowMs)`.
- Produces: `suspend fun recoverForPlayback(): Boolean`, returning `true` only when this call actually performed a forced yt-dlp update attempt.

- [ ] **Step 1: Extend the failing test for updater-independent timestamp semantics**

Add:
```kotlin
@Test
fun clockRollbackAllowsRecoveryInsteadOfBlockingForever() {
    assertTrue(
        YtDlpPlaybackRecoveryPolicy.shouldAttempt(
            lastAttemptAtMs = 10_000L,
            nowMs = 5_000L
        )
    )
}
```

- [ ] **Step 2: Run the focused policy test and verify RED**

Run:
```bash
gradle --no-daemon :app:testDebugUnitTest --tests com.frxe.music.source.YtDlpPlaybackRecoveryPolicyTest
```
Expected: FAIL because the current subtraction logic rejects clock rollback.

- [ ] **Step 3: Make the policy minimal and clock-safe**

Update `shouldAttempt` to use:
```kotlin
val elapsed = nowMs - lastAttempt
return elapsed < 0L || elapsed >= COOLDOWN_MS
```

- [ ] **Step 4: Add updater recovery state and API**

In `YtDlpRuntimeUpdater` add:
```kotlin
@Volatile
private var appContext: Context? = null

private val playbackRecoveryMutex = Mutex()
private var lastPlaybackRecoveryAttemptMs: Long? = null
```

At the start of `initializeAndSchedule(context)` set:
```kotlin
appContext = context.applicationContext
```

Add:
```kotlin
suspend fun recoverForPlayback(): Boolean = playbackRecoveryMutex.withLock {
    val context = appContext ?: return@withLock false
    val now = System.currentTimeMillis()
    if (!YtDlpPlaybackRecoveryPolicy.shouldAttempt(lastPlaybackRecoveryAttemptMs, now)) {
        return@withLock false
    }

    lastPlaybackRecoveryAttemptMs = now
    updateIfDue(context = context, force = true)
    true
}
```

Import `com.frxe.music.source.YtDlpPlaybackRecoveryPolicy`.

- [ ] **Step 5: Run focused tests and debug compile**

Run:
```bash
gradle --no-daemon :app:testDebugUnitTest --tests com.frxe.music.source.YtDlpPlaybackRecoveryPolicyTest :app:assembleDebug
```
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/frxe/music/updates/YtDlpRuntimeUpdater.kt app/src/main/java/com/frxe/music/source/YtDlpPlaybackRecoveryPolicy.kt app/src/test/java/com/frxe/music/source/YtDlpPlaybackRecoveryPolicyTest.kt
git commit -m "fix: gate yt-dlp playback runtime recovery"
```

---

### Task 3: Retry local playback resolution once after runtime refresh

**Files:**
- Modify: `app/src/main/java/com/frxe/music/source/PlaybackStreamResolver.kt`
- Create: `app/src/main/java/com/frxe/music/source/PlaybackRecoveryDecision.kt`
- Test: `app/src/test/java/com/frxe/music/source/PlaybackRecoveryDecisionTest.kt`

**Interfaces:**
- Consumes: `YtDlpRuntimeUpdater.recoverForPlayback()` and `PlaybackResolutionResult`.
- Produces: `PlaybackRecoveryDecision.shouldRefreshAndRetry(result: PlaybackResolutionResult): Boolean`.

- [ ] **Step 1: Write the failing decision tests**

Create:
```kotlin
package com.frxe.music.source

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackRecoveryDecisionTest {
    @Test
    fun failedResolutionCanTriggerRuntimeRecovery() {
        assertTrue(
            PlaybackRecoveryDecision.shouldRefreshAndRetry(
                PlaybackResolutionResult.Failed(
                    message = "failed",
                    attempts = emptyList()
                )
            )
        )
    }

    @Test
    fun verificationDoesNotTriggerRuntimeRecovery() {
        assertFalse(
            PlaybackRecoveryDecision.shouldRefreshAndRetry(
                PlaybackResolutionResult.VerificationRequired(
                    challenge = YouTubeChallenge(
                        kind = YouTubeChallengeKind.VerificationRequired,
                        message = "verification"
                    ),
                    attempts = emptyList()
                )
            )
        )
    }

    @Test
    fun successDoesNotTriggerRuntimeRecovery() {
        assertFalse(
            PlaybackRecoveryDecision.shouldRefreshAndRetry(
                PlaybackResolutionResult.Success(
                    stream = PlaybackResolvedStream(
                        url = "https://example.test/audio",
                        resolver = PlaybackResolverKind.YtDlp,
                        headers = emptyMap()
                    ),
                    attempts = emptyList()
                )
            )
        )
    }
}
```

- [ ] **Step 2: Run the new test and verify RED**

Run:
```bash
gradle --no-daemon :app:testDebugUnitTest --tests com.frxe.music.source.PlaybackRecoveryDecisionTest
```
Expected: FAIL because `PlaybackRecoveryDecision` does not exist.

- [ ] **Step 3: Implement the minimal decision object**

Create:
```kotlin
package com.frxe.music.source

internal object PlaybackRecoveryDecision {
    fun shouldRefreshAndRetry(result: PlaybackResolutionResult): Boolean =
        result is PlaybackResolutionResult.Failed
}
```

- [ ] **Step 4: Integrate one controlled retry in `PlaybackStreamResolver`**

Add:
```kotlin
import com.frxe.music.updates.YtDlpRuntimeUpdater
```

Replace the single immutable local resolution value with:
```kotlin
var localResolution = YouTubeAudioResolverRuntime.resolve(
    videoId = videoId,
    order = PlaybackResolverOrder.local
)

if (
    PlaybackRecoveryDecision.shouldRefreshAndRetry(localResolution) &&
    YtDlpRuntimeUpdater.recoverForPlayback()
) {
    localResolution = YouTubeAudioResolverRuntime.resolve(
        videoId = videoId,
        order = PlaybackResolverOrder.local
    )
}
```

Then keep the existing `when (localResolution)` handling unchanged. This preserves the stale-result `PlaybackResolutionMonitor.isCurrent(track.id)` guard, verification handling, and ZEXL fallback. The second pass uses the unchanged `YtDlp -> InnerTube -> NewPipe` order.

- [ ] **Step 5: Run focused tests**

Run:
```bash
gradle --no-daemon :app:testDebugUnitTest --tests com.frxe.music.source.PlaybackRecoveryDecisionTest --tests com.frxe.music.source.YtDlpPlaybackRecoveryPolicyTest --tests com.frxe.music.source.PlaybackResolutionPrivacyTest
```
Expected: PASS.

- [ ] **Step 6: Run full unit/debug build**

Run:
```bash
gradle --no-daemon --stacktrace :app:testDebugUnitTest :app:assembleDebug
```
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/frxe/music/source/PlaybackStreamResolver.kt app/src/main/java/com/frxe/music/source/PlaybackRecoveryDecision.kt app/src/test/java/com/frxe/music/source/PlaybackRecoveryDecisionTest.kt
git commit -m "fix: recover yt-dlp playback after local resolution failure"
```

---

### Task 4: Privacy regression sweep

**Files:**
- Modify only if needed: `app/src/main/java/com/frxe/music/source/PlaybackResolutionMonitor.kt`
- Test: `app/src/test/java/com/frxe/music/source/PlaybackResolutionPrivacyTest.kt`

**Interfaces:**
- Consumes: all normal playback monitor state transitions.
- Produces: normal playback status messages that stay backend-neutral.

- [ ] **Step 1: Expand privacy assertions to every configured backend name**

Change the blocked-name list to:
```kotlin
listOf(
    "yt-dlp",
    "newpipe",
    "innertube",
    "zexl",
    "cobalt",
    "aria2",
    "mutagen"
)
```

- [ ] **Step 2: Run privacy test**

Run:
```bash
gradle --no-daemon :app:testDebugUnitTest --tests com.frxe.music.source.PlaybackResolutionPrivacyTest
```
Expected: PASS. If it fails, change only user-facing message text; keep internal resolver fields/diagnostics intact.

- [ ] **Step 3: Commit privacy-test strengthening**

```bash
git add app/src/test/java/com/frxe/music/source/PlaybackResolutionPrivacyTest.kt app/src/main/java/com/frxe/music/source/PlaybackResolutionMonitor.kt
git commit -m "test: keep playback resolver details private"
```

---

### Task 5: Milestone 1 exact-head verification

**Files:**
- No production changes unless verification discovers a defect.

**Interfaces:**
- Produces: a green, reviewable Milestone 1 checkpoint for the larger yt-dlp Core feature.

- [ ] **Step 1: Run full unit/debug build**

Run:
```bash
gradle --no-daemon --stacktrace :app:testDebugUnitTest :app:assembleDebug
```
Expected: PASS.

- [ ] **Step 2: Review diff against the milestone base**

Confirm:
- yt-dlp remains first in `PlaybackResolverOrder.local`;
- only one automatic update/retry can occur inside 15 minutes;
- verification challenges do not trigger update loops;
- ZEXL fallback remains after local resolution failure;
- normal UI messages contain no backend names;
- no unrelated feature path was removed.

- [ ] **Step 3: Open/update a draft PR for the feature branch**

PR title:
```text
FRXE 0.6.12 yt-dlp Core
```

The PR remains draft while Milestones 2-5 are implemented. Record Milestone 1 verification in the PR body/comment.
