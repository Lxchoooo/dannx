# Macrobenchmark and Baseline Profile

*   **Status:** module added; profile not regenerated yet
*   **Date:** 2026-11-15

---

## Why

DannX ships `app/src/main/baseline-prof.txt`, a 65-line profile written by hand:
a list of classes and methods that *seem* worth pre-compiling at install time.
It has never been measured, so there is no evidence that it helps startup, that
it covers the right code, or that it is worth the extra bytes in the APK.

`MainViewModel` is also the largest class in the project and the feed is the
screen that opens at launch. Before splitting either of them, we need numbers.
This document describes the tooling that produces those numbers:
a `:benchmark` module with Macrobenchmark, plus a profile generator that
replaces the handwritten file with a measured one.

## Module layout

```
benchmark/
  build.gradle.kts                  com.android.test, targetProjectPath = ":app"
  src/main/java/.../BenchmarkTarget.kt      shared constants and the scroll gesture
  src/main/java/.../StartupBenchmark.kt     cold and warm startup
  src/main/java/.../FeedScrollBenchmark.kt  frame timing while scrolling
  src/main/java/.../BaselineProfileGenerator.kt
app/src/benchmark/AndroidManifest.xml       <profileable android:shell="true"/>
```

`app/build.gradle.kts` gains a `benchmark` build type that copies `release` —
R8 and resource shrinking included — and signs with the debug key so the APK can
be installed on an emulator. `app/src/benchmark/AndroidManifest.xml` marks that
variant as `profileable`; the release APK is not.

The benchmark module is self-instrumenting (`android.experimental.self-instrumenting`)
and compiles separately from the app, so it does not add dependencies or code to
any published APK. Two app build types are enabled for it: `benchmark` (a
minified copy of `release` signed with the debug key) and `nonMinifiedRelease`
(the same copy without R8 or resource shrinking), each marked as `profileable`
by its own manifest (`app/src/benchmark` and `app/src/nonMinifiedRelease`).
Only those two variants of the test module are enabled: the others would
install a debuggable app, which Macrobenchmark refuses to measure.

The app's own instrumented tests run with `./gradlew :app:connectedDebugAndroidTest`
on purpose. Without the module prefix Gradle runs the task in every module, and
the benchmark tests would try to measure a debug build.

## What is measured

| Test | Metric | What it answers |
| --- | --- | --- |
| `StartupBenchmark.coldStartup` | `StartupTimingMetric` | Time to first frame with a fresh process. |
| `StartupBenchmark.warmStartup` | `StartupTimingMetric` | Same activity restarted without killing the process; the gap against cold shows how much of startup is static initialisation. |
| `FeedScrollBenchmark.feedScroll` | `FrameTimingMetric` | `frameDurationCpuMs` at `P50`/`P90`/`P99` while flinging the feed: the jank a low-end phone would show. |
| `BaselineProfileGenerator.generate` | — | Produces `baseline-prof.txt` from a real run. |

Five iterations per test, the minimum recommended by the Macrobenchmark guide.

The scroll gesture is a raw swipe over the middle of the screen instead of a
UI Automator selector. That keeps the test independent from internal `testTag`
names, and it still measures frames when the feed is empty or loading.

## How to run it

Benchmarks run on demand, never as part of a pull request:

```bash
gh workflow run android.yml --ref main
```

The `benchmark` job starts an API 34 emulator, builds `:app:benchmark` and
`:benchmark:benchmark`, runs the three benchmarks and uploads the JSON reports
and Perfetto traces as the `benchmark-results` artifact.

`EMULATOR` and `LOW-BATTERY` errors are suppressed on purpose: the emulator is
the only device CI has, and its absolute numbers are not representative of a
real phone. Use it to compare two revisions of the same code, not to promise
milliseconds to users.

Pull requests that touch the module only compile it:

```bash
./gradlew :benchmark:assembleBenchmark :app:assembleBenchmark
```

That is the step the `checks` job runs when the `changes` job detects a
benchmark-related file, so the cost (one extra R8 pass) is paid only when it
matters.

## Generating a real profile

`BaselineProfileGenerator` writes a profile to the additional test output
directory:

```
benchmark/build/outputs/connected_android_test_additional_output/benchmark/connected/<device>/
    BaselineProfileGenerator_generate-startup-prof.txt
```

**Do not commit that file as is.** The generator runs against the `benchmark`
variant, which is minified, so every rule comes out with R8 names
(`HSPLa02;-><init>()V`). AGP already remaps `src/main/baseline-prof.txt` through
the R8 mapping when it builds a release APK; a profile that is already obfuscated
is tied to one exact mapping and goes stale on the next code change.

The `benchmark` job of `android.yml` now does this automatically. After the
minified benchmarks it runs the generator against the `nonMinifiedRelease`
variant:

```bash
./gradlew :benchmark:connectedNonMinifiedReleaseAndroidTest \
    -Pandroid.testInstrumentationRunnerArguments.class=\
com.dannx.walls.benchmark.BaselineProfileGenerator \
    -Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.suppressErrors=EMULATOR,LOW-BATTERY
```

and uploads the readable profile as the `baseline-profile-generated` artifact.
From there the flow is:

1.  Download the artifact and replace `app/src/main/baseline-prof.txt` with it.
2.  Re-run `StartupBenchmark` before and after to confirm the change is an
    improvement instead of a regression.
3.  Commit both the profile and the numbers in this document.

## First measurement (2026-11-15)

Run [`43752802276`](https://github.com/Lxchoooo/dannx/actions/runs/43752802276),
API 34 emulator (`emu64x`, 2 cores, `swiftshader_indirect`, no window), five
iterations per test:

| Test | Metric | Median | Min–max |
| --- | --- | --- | --- |
| `coldStartup` | `timeToInitialDisplayMs` | 757 ms | 727–769 ms |
| `warmStartup` | `timeToInitialDisplayMs` | 65 ms | 49–88 ms |
| `feedScroll` | `frameCount` | 2 frames | 2–2 |

What the run says:

*   Startup is dominated by the cold path: warm is about 11× faster, so the work
    worth optimising is process start (Application, ViewModel, first composition),
    not activity recreation.
*   The scroll number is not usable. A headless emulator with software rendering
    produced two frames per swipe, so `FrameTimingMetric` never saw a real
    scroll. That test needs a physical device; on CI it is only a smoke test that
    the gesture runs.
*   The report says `compilationMode: run-from-apk`, so the numbers measure the
    app with no AOT compilation. Treat them as an upper bound, not as what a
    typical install does.

They are still a useful reference: same emulator, same image, same command, so a
future revision can be compared against this row.

## Current state and next steps

*   The module, the benchmarks and the manual CI job work: run
    [`43752802276`](https://github.com/Lxchoooo/dannx/actions/runs/43752802276)
    finished green and produced the first numbers below.
*   `baseline-prof.txt` is still the handwritten list: the generated one is
    obfuscated and tied to one R8 mapping.
*   Next: the `benchmark` job now generates the profile against
    `nonMinifiedRelease` (readable names) and uploads it as the
    `baseline-profile-generated` artifact; commit that file and compare
    `CompilationMode.None` against the default mode on the same APK to isolate
    how much of startup comes from the profile.
