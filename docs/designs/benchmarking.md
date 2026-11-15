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
any published APK. Only its `benchmark` variant is enabled: the others would
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

`BaselineProfileGenerator` writes `baseline-prof.txt` to the additional test
output directory:

```
benchmark/build/outputs/connected_android_test_additional_output/benchmarkAndroidTest/connected/<device>/baseline-prof.txt
```

To use it:

1.  Run the `benchmark` job (or `:benchmark:connectedBenchmarkAndroidTest`
    locally) on a physical device when one is available. The emulator profile
    is better than the handwritten one, but not as good as a real-device run.
2.  Replace `app/src/main/baseline-prof.txt` with the generated file.
3.  Re-run `StartupBenchmark` before and after to confirm the change is an
    improvement instead of a regression.
4.  Commit both the profile and the numbers in this document.

## Current state and next steps

*   The module compiles in CI and the benchmarks are wired to a manual dispatch.
*   No measured profile has been produced yet: `baseline-prof.txt` is still the
    handwritten list.
*   The next measurement should compare `CompilationMode.None` against the
    default mode on the same APK, to isolate how much of startup comes from the
    profile.
*   Once there are numbers, the same runs can validate (or reject) the
    `MainViewModel` split, instead of doing it blind.
