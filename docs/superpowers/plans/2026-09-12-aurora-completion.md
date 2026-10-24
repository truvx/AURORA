# AURORA Completion Implementation Plan

> **For agentic workers:** Steps use checkbox (`- [ ]`) syntax for tracking. Each task ends with an independently testable deliverable and a commit.

**Goal:** Close the queue defect that silently disables listening history and queue restore, then fill the Phase 20 test gaps, add Phase 19 performance measurement, and finish what is verifiable of Phase 18.

**Architecture:** All work is additive to the existing Android client. The queue fix goes in `PlayerCoordinator`, the only owner of canonical queue state. Test gaps are filled at the layer that owns each behaviour: JVM unit tests where no `Context` is needed, instrumented tests where one is. Web testing introduces Vitest, which the project has never had.

**Tech Stack:** Kotlin 2.4.10, Compose 1.12.0, Media3 1.11.0, Room, JUnit4, Espresso 3.7.0, Vitest (new), androidx.benchmark (new).

**Spec:** `docs/IMPLEMENTATION_PLAN.md` phases 18-21, `docs/PERFORMANCE_BUDGET.md`, `docs/ACCESSIBILITY_SPEC.md`, `docs/TEST_ARCHITECTURE.md`

## Global Constraints

- JDK 17, `compileSdk 37`, `targetSdk 36`, `minSdk 26` (`docs/ADR-016-VERSION-MATRIX.md`)
- Web uses Node 24 LTS, React 19.2.7, Next.js 16.3.4
- Every gate must stay green: `:app:testDebugUnitTest`, `:app:lintDebug`, `:app:assembleRelease`, `npx tsc --noEmit`, `npm run lint`
- Unknown stays unknown: never fabricate a value to make a test or feature look complete
- No secrets in committed files; `local.properties` and `.env*` stay ignored
- Do not commit unless the task says to

## Out of Scope, With Reasons

- **Phase 17 (web client).** Building a player, queue, IndexedDB persistence, and Media Session for the browser is a second complete client. It needs its own spec and plan cycle; folding it in here would produce a plan no one can execute in one pass.
- **Phase 21 keystore and distribution.** Generating a production signing key and choosing a distribution channel are the user's decisions and require secrets an agent must not create or hold. R8, the signing config, and CI gating are already done.
- **TalkBack audit and artwork contrast judgement.** Semantics can be asserted; how a screen reader *sounds* and whether text is legible over real album art are human judgements. Task 6 produces the measurement harness for contrast, not the verdict.

---

### Task 1: Playing a track puts it in the queue

The defect: `PlayerCoordinator.loadTrack()` sets `currentTrack` but never touches `queue.items`. `skipNext()` begins with `if (q.items.isEmpty()) return`, so a finished track returns immediately, `PlaybackStatus.Completed` is never reached, `PlaybackHistoryRecorder` never records a COMPLETE, and `maybeSaveQueueSnapshot` has nothing to snapshot. Two Phase 10 features are inert on the main playback path.

**Files:**
- Modify: `android/app/src/main/java/dev/aurora/player/app/PlayerCoordinator.kt` (`loadTrack`, around line 56)
- Test: `android/app/src/test/java/dev/aurora/player/app/PlayerCoordinatorTest.kt`

**Interfaces:**
- Consumes: `PlayerCommand.Load(track)`, `QueueState(items, currentIndex, repeatMode, shuffleMode, shuffledOrder)`
- Produces: after `Load`, `state.queue.items` contains the track and `state.queue.currentIndex` points at it

- [ ] **Step 1: Write the failing tests**

Append to `PlayerCoordinatorTest.kt`:

```kotlin
@Test
fun `loading a track puts it in the queue`() = runTest(UnconfinedTestDispatcher()) {
    val coordinator = newCoordinator()
    coordinator.dispatch(PlayerCommand.Load(testTrack("local_1")))

    val queue = coordinator.state.value.queue
    assertEquals(listOf("local_1"), queue.items.map { it.id })
    assertEquals(0, queue.currentIndex)
}

@Test
fun `loading a second track replaces the single-track queue rather than growing it`() =
    runTest(UnconfinedTestDispatcher()) {
        val coordinator = newCoordinator()
        coordinator.dispatch(PlayerCommand.Load(testTrack("local_1")))
        coordinator.dispatch(PlayerCommand.Load(testTrack("local_2")))

        val queue = coordinator.state.value.queue
        assertEquals(listOf("local_2"), queue.items.map { it.id })
        assertEquals(0, queue.currentIndex)
    }

@Test
fun `loading a track already queued selects it instead of duplicating it`() =
    runTest(UnconfinedTestDispatcher()) {
        val coordinator = newCoordinator()
        coordinator.dispatch(PlayerCommand.AddToQueue(testTrack("local_1")))
        coordinator.dispatch(PlayerCommand.AddToQueue(testTrack("local_2")))
        coordinator.dispatch(PlayerCommand.Load(testTrack("local_2")))

        val queue = coordinator.state.value.queue
        assertEquals(listOf("local_1", "local_2"), queue.items.map { it.id })
        assertEquals(1, queue.currentIndex)
    }
```

Use the file's existing `newCoordinator()` / fake-adapter helpers; add `testTrack(id)` returning a `MediaItem` with `provider = ProviderKind.LOCAL` if one is not already present.

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "*PlayerCoordinatorTest"`
Expected: the three new tests FAIL — queue stays empty.

- [ ] **Step 3: Implement**

In `loadTrack`, replace the single state update that sets `currentTrack` with one that also places the track in the queue:

```kotlin
_state.update { current ->
    val existingIndex = current.queue.items.indexOfFirst { it.id == track.id }
    val queue = if (existingIndex >= 0) {
        // Already queued: select it rather than adding a duplicate.
        current.queue.copy(currentIndex = existingIndex)
    } else {
        // A direct Load replaces the queue; AddToQueue is the command that appends.
        current.queue.copy(items = listOf(track), currentIndex = 0, shuffledOrder = emptyList())
    }
    current.copy(status = PlaybackStatus.Loading, currentTrack = track, queue = queue)
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "*PlayerCoordinatorTest"`
Expected: PASS, and no previously passing test in the class regresses.

- [ ] **Step 5: Verify end to end on device**

```bash
./gradlew :app:assembleDebug
adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5554 shell am force-stop dev.aurora.player
adb -s emulator-5554 shell am start -n dev.aurora.player/.MainActivity
# tap Library, tap a track, wait past the track's duration
adb -s emulator-5554 shell run-as dev.aurora.player cat databases/aurora-database > /tmp/q.db
adb -s emulator-5554 shell run-as dev.aurora.player cat databases/aurora-database-wal > /tmp/q.db-wal
sqlite3 /tmp/q.db "SELECT mediaId, kind FROM listening_events;"
sqlite3 /tmp/q.db "SELECT currentIndex FROM queue_snapshots;"
```
Expected: a `COMPLETE` row appears alongside `PLAY`, and `queue_snapshots` has a row.

- [ ] **Step 6: Commit**

```bash
git add android/app/src/main/java/dev/aurora/player/app/PlayerCoordinator.kt \
        android/app/src/test/java/dev/aurora/player/app/PlayerCoordinatorTest.kt
git commit -m "fix(player): place a directly loaded track into the queue"
```

---

### Task 2: Cover AuroraMediaSessionService

This service drives lock-screen and notification transport controls and has never been tested. It overrides `getAvailableCommands`, `seekToNext`, `seekToPrevious`, `seekToNextMediaItem`, and `seekToPreviousMediaItem` — command surface bugs here are invisible in the app UI.

**Files:**
- Test: `android/app/src/androidTest/java/dev/aurora/player/data/player/AuroraMediaSessionServiceTest.kt` (create)

**Interfaces:**
- Consumes: `AuroraMediaSessionService`, `androidx.media3.session.MediaSession`
- Produces: nothing consumed by later tasks

- [ ] **Step 1: Write the test**

```kotlin
package dev.aurora.player.data.player

import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.ServiceTestRule
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The media session is how the lock screen, notification, and hardware keys reach the
 * player. It is only constructible on a device, which is why it had no coverage.
 */
@RunWith(AndroidJUnit4::class)
class AuroraMediaSessionServiceTest {

    @get:Rule
    val serviceRule = ServiceTestRule()

    @Test
    fun serviceStartsAndExposesASession() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val intent = Intent(context, AuroraMediaSessionService::class.java)
        // Binding proves onCreate built the session without throwing; a failure here is
        // what silently removes lock-screen controls.
        val binder = serviceRule.bindService(intent)
        assertNotNull(binder)
    }
}
```

Add the dependency if missing, in `android/app/build.gradle.kts`:
`androidTestImplementation("androidx.test:rules:1.6.1")`

- [ ] **Step 2: Run and confirm it passes**

Run: `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=dev.aurora.player.data.player.AuroraMediaSessionServiceTest`
Expected: PASS. If it fails, the failure is a real defect in service construction — debug it with `systematic-debugging` before continuing.

- [ ] **Step 3: Commit**

```bash
git add android/app/src/androidTest/java/dev/aurora/player/data/player/AuroraMediaSessionServiceTest.kt android/app/build.gradle.kts
git commit -m "test(player): cover media session service construction"
```

---

### Task 3: Cover YouTubePlayerAdapter's event contract

The adapter converts JavaScript bridge callbacks into `EngineEvent`s. Its `onTimeUpdate` guard is what stopped idle ticks from overwriting local playback, and nothing tests it.

**Files:**
- Test: `android/app/src/test/java/dev/aurora/player/data/player/YouTubePlayerAdapterTest.kt` (create)

**Interfaces:**
- Consumes: `YouTubePlayerAdapter.onPositionChanged(elapsed: Long, duration: Long?)`, `.onReady()`, `.onPaused()`, `.onTrackCompleted()`, `.onError(Throwable)` — the bridge-facing methods
- Produces: nothing consumed by later tasks

- [ ] **Step 1: Write the test**

Construct the adapter without a WebView if its constructor allows it; otherwise test the public bridge methods against a collected `events` flow:

```kotlin
@Test
fun `position updates are emitted with an unknown buffered value`() = runTest(UnconfinedTestDispatcher()) {
    val adapter = YouTubePlayerAdapter(/* existing constructor args */)
    val received = mutableListOf<EngineEvent>()
    val job = launch { adapter.events.toList(received) }

    adapter.onPositionChanged(5_000L, 180_000L)
    job.cancel()

    val position = received.filterIsInstance<EngineEvent.PositionChanged>().single()
    assertEquals(5_000L, position.elapsed)
    assertEquals(180_000L, position.duration)
    // YouTube exposes no buffered figure; reporting one would be inventing it.
    assertNull(position.buffered)
}
```

If the constructor requires a `Context`, move this file to `androidTest` instead and keep the same assertions.

- [ ] **Step 2: Run it**

Run: `./gradlew :app:testDebugUnitTest --tests "*YouTubePlayerAdapterTest"`
Expected: PASS.

- [ ] **Step 3: Commit**

```bash
git add android/app/src/test/java/dev/aurora/player/data/player/YouTubePlayerAdapterTest.kt
git commit -m "test(youtube): cover the adapter's event contract"
```

---

### Task 4: Give the web client a test suite

The web client has zero tests and no test runner. The AI gateway is the highest-risk surface: it validates model output before it can reach playback.

**Files:**
- Create: `web/vitest.config.ts`
- Create: `web/lib/ai/__tests__/gateway.test.ts`
- Modify: `web/package.json` (add `vitest`, `"test"` script)
- Modify: `.github/workflows/ci.yml` (run web tests)

**Interfaces:**
- Consumes: `TOOLS` from `web/lib/ai/tools.ts`
- Produces: `npm test` in `web/`

- [ ] **Step 1: Add the runner**

```bash
cd web && npm install -D vitest@^2
```

`web/vitest.config.ts`:

```ts
import { defineConfig } from "vitest/config";

export default defineConfig({
  test: { environment: "node", include: ["**/__tests__/**/*.test.ts"] },
});
```

Add to `package.json` scripts: `"test": "vitest run"`

- [ ] **Step 2: Write the failing-then-passing tests**

```ts
import { describe, expect, it } from "vitest";
import { TOOLS } from "../tools";

describe("AI tool allowlist", () => {
  it("declares only tools the Android executor handles", () => {
    // Drift here is what left findSimilarMusic declared but unhandled, so every
    // "find something similar" request silently returned nothing.
    expect(TOOLS.map((t) => t.name).sort()).toEqual(
      ["addToQueue", "findSimilarMusic", "playTrack", "searchMusic"].sort()
    );
  });

  it("gives every tool a description and parameter schema", () => {
    for (const tool of TOOLS) {
      expect(tool.description, `${tool.name} needs a description`).toBeTruthy();
      expect(tool.parameters.type).toBe("object");
    }
  });

  it("marks required parameters for tools that act on media", () => {
    const playTrack = TOOLS.find((t) => t.name === "playTrack")!;
    expect(playTrack.parameters.required).toContain("trackId");
  });
});
```

- [ ] **Step 3: Run**

Run: `cd web && npm test`
Expected: PASS (3 tests).

- [ ] **Step 4: Add to CI**

In `.github/workflows/ci.yml`, in the `web` job after `Lint`:

```yaml
      - name: Test
        run: npm test
```

- [ ] **Step 5: Commit**

```bash
git add web/vitest.config.ts web/package.json web/package-lock.json \
        web/lib/ai/__tests__/gateway.test.ts .github/workflows/ci.yml
git commit -m "test(web): add vitest and cover the AI tool allowlist"
```

---

### Task 5: Measure startup and frame timing (Phase 19)

`docs/PERFORMANCE_BUDGET.md` specifies a 16.7ms frame budget and startup targets that nothing measures. A baseline profile also improves real startup, not just measurement.

**Files:**
- Create: `android/benchmark/build.gradle.kts`
- Create: `android/benchmark/src/main/AndroidManifest.xml`
- Create: `android/benchmark/src/main/java/dev/aurora/benchmark/StartupBenchmark.kt`
- Modify: `android/settings.gradle.kts` (include `:benchmark`)

**Interfaces:**
- Consumes: the `dev.aurora.player` debug APK
- Produces: `:benchmark:connectedBenchmarkAndroidTest` reporting startup timings

- [ ] **Step 1: Create the module**

`android/settings.gradle.kts`: add `include(":benchmark")`

`android/benchmark/build.gradle.kts`:

```kotlin
plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "dev.aurora.benchmark"
    compileSdk = 37
    defaultConfig {
        minSdk = 26
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    targetProjectPath = ":app"
    experimentalProperties["android.experimental.self-instrumenting"] = true
}

dependencies {
    implementation("androidx.benchmark:benchmark-macro-junit4:1.3.3")
    implementation(libs.junit.ext)
    implementation(libs.espresso.core)
}
```

Add to `gradle/libs.versions.toml` under `[plugins]` if absent:
`android-test = { id = "com.android.test", version.ref = "agp" }`

- [ ] **Step 2: Write the benchmark**

`StartupBenchmark.kt`:

```kotlin
package dev.aurora.benchmark

import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Measures cold start against the budget in docs/PERFORMANCE_BUDGET.md. Emulator numbers
 * are noisier than a physical device; treat them as a regression signal, not an absolute.
 */
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun coldStartup() = benchmarkRule.measureRepeated(
        packageName = "dev.aurora.player",
        metrics = listOf(StartupTimingMetric()),
        iterations = 5,
        startupMode = StartupMode.COLD
    ) {
        pressHome()
        startActivityAndWait()
    }
}
```

`android/benchmark/src/main/AndroidManifest.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android" />
```

- [ ] **Step 3: Run it**

Run: `./gradlew :benchmark:connectedBenchmarkAndroidTest`
Expected: completes and prints `timeToInitialDisplayMs`. Record the median in the commit message.

If the module fails to configure, check the AGP `com.android.test` plugin is declared; do not silently drop the task.

- [ ] **Step 4: Commit**

```bash
git add android/benchmark android/settings.gradle.kts android/gradle/libs.versions.toml
git commit -m "perf: add macrobenchmark module measuring cold startup"
```

---

### Task 6: Contrast measurement for glass over artwork (Phase 18)

The spec requires validating text contrast "against the final artwork, blur, scrim, and surface combination." Judging real album art is a human call, but the composited contrast ratio is computable, and a test can hold the scrim honest.

**Files:**
- Create: `android/app/src/test/java/dev/aurora/player/ui/theme/ContrastTest.kt`

**Interfaces:**
- Consumes: `AuroraColors` from `dev.aurora.player.ui.theme`
- Produces: nothing consumed by later tasks

- [ ] **Step 1: Write the test**

```kotlin
package dev.aurora.player.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * WCAG 2.1 contrast for the text the app draws over glass. Artwork can be any colour, so
 * the scrim is what guarantees legibility; these pin the worst case of pure white and pure
 * black artwork behind the surface.
 */
class ContrastTest {

    private fun relativeLuminance(color: Color): Double {
        fun channel(c: Float): Double {
            val s = c.toDouble()
            return if (s <= 0.03928) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)
    }

    private fun contrastRatio(foreground: Color, background: Color): Double {
        val a = relativeLuminance(foreground)
        val b = relativeLuminance(background)
        return (max(a, b) + 0.05) / (min(a, b) + 0.05)
    }

    /** Composite `top` over `bottom` using top's alpha. */
    private fun over(top: Color, bottom: Color): Color = Color(
        red = top.red * top.alpha + bottom.red * (1 - top.alpha),
        green = top.green * top.alpha + bottom.green * (1 - top.alpha),
        blue = top.blue * top.alpha + bottom.blue * (1 - top.alpha),
        alpha = 1f
    )

    @Test
    fun `primary text stays legible over the darkest artwork`() {
        val colors = auroraDarkColors()
        val surface = over(colors.surfaceGlassPrimary, Color.Black)
        assertTrue(
            "contrast ${contrastRatio(colors.textPrimary, surface)} is below 4.5:1",
            contrastRatio(colors.textPrimary, surface) >= 4.5
        )
    }

    @Test
    fun `primary text stays legible over the brightest artwork`() {
        val colors = auroraDarkColors()
        val surface = over(colors.surfaceGlassPrimary, Color.White)
        assertTrue(
            "contrast ${contrastRatio(colors.textPrimary, surface)} is below 4.5:1",
            contrastRatio(colors.textPrimary, surface) >= 4.5
        )
    }

    @Test
    fun `the opaque fallback meets contrast without relying on artwork at all`() {
        val colors = auroraDarkColors()
        assertTrue(
            contrastRatio(colors.textPrimary, colors.surfaceOpaqueFallback) >= 4.5
        )
    }
}
```

Replace `auroraDarkColors()` with whatever the theme file actually exposes; read `AuroraColors.kt` first and use the real accessor and property names.

- [ ] **Step 2: Run**

Run: `./gradlew :app:testDebugUnitTest --tests "*ContrastTest"`

A failure here is a real finding: the scrim is too weak over extreme artwork. If it fails, report the measured ratio and raise the scrim alpha until it passes rather than lowering the threshold.

- [ ] **Step 3: Commit**

```bash
git add android/app/src/test/java/dev/aurora/player/ui/theme/ContrastTest.kt
git commit -m "test(a11y): pin text contrast over glass against extreme artwork"
```

---

### Task 7: Update progress documentation and push

**Files:**
- Modify: `docs/IMPLEMENTATION_PROGRESS.md`

- [ ] **Step 1: Record outcomes**

Add a section per phase touched, stating what was verified and what remains. State measured numbers (startup median, contrast ratios), not adjectives.

- [ ] **Step 2: Run every gate**

```bash
cd android && ./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleRelease
./gradlew :app:connectedDebugAndroidTest
cd ../web && npx tsc --noEmit && npm run lint && npm test
```

- [ ] **Step 3: Commit and push**

```bash
git add docs/IMPLEMENTATION_PROGRESS.md
git commit -m "docs: record completion status for phases 18-20"
git push
```

Then confirm CI is green before declaring the plan finished.
