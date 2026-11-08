# CI and Repository Optimization for Faster Builds

*   **Author:** Lxchoooo
*   **Status:** Implemented; goals confirmed by measurement
*   **Date:** 2026-11-08

---

## Overview

DannX is compiled only by GitHub Actions: there is no local JDK or Gradle on the
author's phone, and every APK the author distributes comes from a CI run. Build
time is therefore the author's waiting time.

The repository already went through one optimization round: fast debug build on
push/PR, full build on tags and `main`, `paths-ignore`, Gradle build cache,
timeouts, concurrency cancellation, newer action majors, and a single release
invocation that excludes `lintVitalRelease`. This document records the measured
state, resolves the Stage 5 review (see
`reviews/ci-optimization-stage5.md`), and documents the change that followed:
the full path is split into a parallel correctness gate and a release build,
with publication gated on both. Six post-change runs confirm both timing goals.

## Measured baseline

Source: Actions run `42489548266` on `a056dd7` (2026-11-08), plus the four
preceding green runs. Times are wall-clock from the Actions job/step timeline.

### Fast path (`debug` job)

| Phase | Measured |
| --- | --- |
| Job total | ~86 s |
| Set up job + checkout + setup-java | ~12 s |
| `setup-gradle` restore | ~7 s |
| `./gradlew assembleDebug` | 64 s (range 57–85 s across five runs) |
| Upload debug APK | ~4 s |
| Post actions / complete | ~6 s |

The debug job is already close to its practical floor: runner provisioning,
checkout and Java setup cost ~12 s before Gradle starts, and the APK build
dominates the rest. On one repeat run with a warm build cache the Gradle step
measured 57 s, but the run-to-run range is wider than any remaining CI-only
lever.

### Full path (`release` job, pre-split)

| Phase | Measured |
| --- | --- |
| Job total | ~276 s |
| Set up job + checkout + setup-java | ~13 s |
| `setup-gradle` restore | ~5 s |
| Gradle command total | ~252 s |
| — Gradle configuration/startup | ~15 s |
| — `compileDebugKotlin` + `compileReleaseKotlin` | ~14 s |
| — `testDebugUnitTest` | ~2 s |
| — lint analysis + `lintReportDebug` | ~30 s |
| — `minifyReleaseWithR8` | **~130 s** |
| — packaging, resources, profile, upload | ~10 s |
| Upload reports + APK + publish rolling release | ~5 s |
| Post actions / complete | ~9 s |

The dominant cost is R8 minification, not configuration. `VERSION_CODE` is
`github.run_number`, so every run changes `BuildConfig`, which changes the R8
input and forces R8 to run again. This is intentional: the rolling release must
keep a monotonically increasing version code so Android accepts the update.
Release builds therefore cannot be warm-cached by design without changing
versioning semantics.

### Post-change measurements (six runs)

Source: the six `main` pushes after the split, run `42490677444` (`bf264fe`) to
run `42495162848` (`16f7e55`), all on 2026-11-08 and all green. The sample mixes
workflow-only and app-source commits. Times are job wall-clock; end-to-end runs
from the first job start to the end of `publish`.

| Run | Commit | `debug` | `checks` | `build` | `publish` | End-to-end |
| --- | --- | --- | --- | --- | --- | --- |
| `42490677444` | `bf264fe` | 42 s | 146 s | 159 s | 10 s | 172 s |
| `42491881254` | `6b763d2` | 101 s | 116 s | 130 s | 11 s | 143 s |
| `42492350266` | `c6cbeab` | 81 s | 40 s | 169 s | 9 s | 180 s |
| `42494457091` | `d580555` | 89 s | 127 s | 122 s | 10 s | 139 s |
| `42494743934` | `df4cd15` | 95 s | 31 s | 149 s | 12 s | 162 s |
| `42495162848` | `16f7e55` | 73 s | 111 s | 189 s | 11 s | 202 s |
| **Median** | | **85 s** | **114 s** | **154 s** | **11 s** | **168 s** |

Gradle-step medians: `assembleDebug` 62 s, `lintDebug testDebugUnitTest` 91 s,
`assembleRelease` 132 s. The `build` job is the critical path in five of the six
runs; in `42494457091` `checks` edged it out by 5 s, so publication started 7 s
after the build job ended. End-to-end median is 168 s against the 276 s
pre-split baseline (~108 s, 39% less); the 139–202 s spread is runner and cache
variance, not a trend, and `assembleRelease` (101–167 s) dominates it.

Both timing goals are met: `debug` median 85 s (≤ 90 s) with a 62 s Gradle step
(≤ 70 s), and the full path median 168 s (≤ 210 s). The structural saving holds:
lint and tests no longer sit on the release critical path, and publication still
waits for both gates.

## Goals

*   **G1**: Keep the fast path at or below its measured baseline. Do not add
    lint or tests to the `debug` job. Target: debug job median ≤ 90 s and Gradle
    step median ≤ 70 s over three consecutive `main` pushes. The earlier 45 s
    target was not supported by the measured setup floor and is withdrawn.
*   **G2**: Cut the full path from ~276 s to ≤ 210 s (3.5 min) by running lint
    and tests in parallel with `assembleRelease`, while keeping lint and tests
    as hard gates for publication. Do not drop either check. Confirmed by six
    post-change runs: median 168 s end-to-end (range 139–202 s).
*   **G3**: Keep every existing safety property: no signing material in the
    repository, no secrets in build logs, `minSdk 29` / `targetSdk 36`
    unchanged, release signing flow unchanged.
*   **G4**: Keep CI deterministic: a cold-cache run must still pass, and a
    failing lint or test run must block artifact publication.
*   **G5**: Land changes as small, individually verifiable commits, each green
    before moving on.

## Non-Goals

*   **NG1**: No changes to app source, resources, or behavior. CI and build
    wiring only.
*   **NG2**: No change to the release signing flow, keystores, or secret names
    (`SUPABASE_URL`, `SUPABASE_ANON_KEY`, `ANDROID_KEYSTORE_BASE64`).
*   **NG3**: No paid runners, third-party CI services, or self-hosted runners.
*   **NG4**: No removal of lint or unit tests from the release path.
*   **NG5**: No dependency upgrades whose purpose is new features.
*   **NG6**: No repository-history rewriting.
*   **NG7**: No change to `VERSION_CODE` semantics that would break rolling
    updates. A stable version code for CI validation builds is a possible
    future experiment, but it would make the rolling APK non-updatable and is
    therefore rejected for now.

## Decisions after the Stage 5 review

| Finding | Resolution |
| --- | --- |
| **B1 / catalyst M1** — configuration cache has no cross-run persistence | **Do not enable it.** Gradle stores the configuration cache in the project `.gradle/` directory; `setup-gradle` persists only Gradle User Home; each job runs one Gradle invocation. There is no reuse to win. Measured configuration time is ~15 s, not the dominant cost. Adding a hand-rolled cache for `.gradle/configuration-cache` would add save/restore time, absolute-path fragility, and a new stale-cache failure mode for a small, unproven gain. |
| **zoom M1** — Lever 5 misstated the workflow | Corrected: the full path uses **one** Gradle invocation today (`lintDebug testDebugUnitTest assembleRelease -x lintVitalRelease`). |
| **zoom M2** — diagram pre-decided PR D | The diagram now shows the current single job and the proposed split, labelled as proposed. |
| **zoom M3 / catalyst M4** — goals had no budget | Goals are restated against measured step times; G1's 45 s target is withdrawn. The savings budget is below. |
| **zoom M4 / N4** — sequence did not cover all files | The landing plan now maps every change to a job/commit; the content-free PR E is gone. |
| **zoom M5** — Dependabot premise was false | `.github/dependabot.yml` already separates `github-actions` and `gradle`; no change is proposed. |
| **zoom M6** — double-caching risk | `setup-java` does **not** enable its `cache:` input. `setup-gradle` remains the only Gradle cache owner. |
| **zoom M7** — config-cache risk list incomplete | Moot: the configuration cache is rejected. `local.properties`, keystore reads and `VERSION_CODE` are still documented as the reasons it would be fragile. |
| **zoom M8** — signing wiring mismatch | Corrected: the workflow writes `ANDROID_KEYSTORE_PATH`; `app/build.gradle.kts` reads `ANDROID_KEYSTORE_PATH`. The old `KEYSTORE_FILE` claim described an earlier version. |
| **catalyst M2** — merges not test-gated | PRs now get a `checks` job with lint + tests. On `main`, `checks` and `build` run in parallel; `publish` has `needs: [checks, build]`. |
| **catalyst M3** — measurement confounds | Runner pinned to `ubuntu-24.04`; timing is step-level; medians of three runs; cache-affecting changes get a warm-up run before timing. |
| **catalyst M5** — split could bypass release gate | `publish` declares `needs: [checks, build]`; a failed lint/test blocks the rolling and tag releases. |
| **catalyst M6** — duplicate lint / low anchor | `lintVitalRelease` is already excluded; `lintDebug` remains the hard gate. The old "lint ≈ 15 s" anchor is removed; measured lint is ~30 s. |
| **catalyst M7** — `VERSION_CODE` source unstated | Documented: `github.run_number`. It invalidates release R8 on every run by design; G2 no longer assumes a warm release cache. |
| **N5** — secret-name grep is noisy | Verification greps for secret **values**, not names. `SUPABASE_ANON_KEY` is public-by-design in the APK; keystore material is not. |
| **N6** — cache hit rate asserted | The design now records cache hits from Gradle output (`FROM-CACHE`, `UP-TO-DATE`) and does not claim cross-job cache sharing. |
| **N7** — config-cache diagnostics | Moot because the configuration cache is rejected. |
| **O1** — concurrency cancellation | Already present in the workflow; retained. |
| **O3** — remote shared build cache | Rejected under NG3 (cost/third-party service). |

## Architecture

Current fast path:

```text
push/PR ──> debug job
            checkout → setup-java → setup-gradle
            → ./gradlew assembleDebug
            → upload debug APK (7 days)
```

Current full path (single job):

```text
main/tag/manual ──> release job
                    checkout → setup-java → setup-gradle
                    → restore keystore
                    → ./gradlew lintDebug testDebugUnitTest assembleRelease -x lintVitalRelease
                    → upload lint/compose reports and release APK
                    → publish tag or rolling release
                    → remove keystore
```

Full path after the change (implemented):

```text
main/tag/manual ──> checks job (parallel)          ──┐
                    lintDebug + testDebugUnitTest    │
                                                     ├─> publish job
                    build job (parallel)             │   needs: [checks, build]
                    restore keystore                 │   download APK
                    assembleRelease -x lintVital...  │   publish tag/rolling
                    upload APK                       │
                                                     ┘
```

The debug job stays unchanged on pushes and PRs. On PRs, `checks` also runs;
`build` and `publish` do not. On `main`, `checks`, `build` and `debug` run in
parallel; `publish` waits for `checks` and `build`. On tags, `debug` is skipped
and `checks` + `build` gate `publish`.

## Savings budget

| Lever | Expected effect | Status |
| --- | --- | --- |
| Parallel `checks` vs `build` | Remove lint and tests from the release critical path; median 168 s end-to-end across six post-change runs (range 139–202 s) against the 276 s baseline | **Implemented and measured** |
| Pin `ubuntu-24.04` | Determinism; near-zero timing effect | **Implemented** |
| Configuration cache | ~0 s across ephemeral runners; not worth a hand-rolled `.gradle` cache | **Rejected** |
| `setup-gradle` home-cache cleanup | May reduce cache size; adds scan time to every saving job | Not adopted until measured |
| Action major bumps | Already applied (checkout v7, setup-java v6, setup-gradle v6, upload-artifact v7) | Done |
| Gradle build cache | Already on; debug path benefits when inputs are unchanged | Done |
| R8 minification | ~130 s, dominant; cannot be removed without changing the release artifact or versioning | Out of scope (NG1/NG7) |
| Split `assembleRelease` per ABI | Not applicable: single APK, no ABI splits configured | Rejected |
| Remote build cache | Would help cold runners but violates NG3 | Rejected |

Realistic conclusion: the CI-only changes cannot reach 2.5 min for a tag build
while R8 runs on changed code. The proposed split targets ~3.3–3.5 min and
removes the correctness gate from the critical path.

## Detailed implementation

### 1. `.github/workflows/android.yml` — split the full path

*   Pin every job to `ubuntu-24.04`.
*   Keep `debug` as-is (except the runner pin and the keystore restore
    documented in 3b).
*   Add a `checks` job: `./gradlew lintDebug testDebugUnitTest`, upload lint and
    test reports. Runs on PR, `main`, tags and manual dispatch.
*   Add a `build` job: restore keystore, `./gradlew assembleRelease -x
    lintVitalRelease`, upload compose reports and release APK. Runs on `main`,
    tags and manual dispatch.
*   Add a `publish` job: `needs: [checks, build]`, download the release
    artifact, publish the tag or rolling release. Runs only on `main` or tags.
*   Keep `concurrency` cancellation, `timeout-minutes`, 7-day artifact
    retention, and the existing secret names.
*   Keep `lintVitalRelease` excluded because `lintDebug` is the complete hard
    gate.

### 2. `docs/designs/ci-optimization.md` — this document

Records the measured baseline, the review resolutions, and the decision to
reject the configuration cache.

### 3. No speed-related changes to `gradle.properties` or `app/build.gradle.kts`

`org.gradle.caching=true`, `org.gradle.parallel=true` and the daemon setting
stay. No configuration-cache flag is added. No provider rewiring is needed
because the configuration cache is not used.

### 3b. Debug artifact signing (follow-up, not a speed lever)

The debug artifact initially used the runner-generated debug key, so it could
not be installed over the release-signed app. The follow-up aligns them:

*   `app/build.gradle.kts` assigns the release signing config to the `debug`
    build type when the release keystore is available. Without it (for example,
    a fork PR), the normal debug key is used.
*   The `debug` CI job restores the keystore and passes the same
    `VERSION_CODE` (`github.run_number`) as the release build, so the debug APK
    can replace the release of the same run without a downgrade.
*   Release signing itself is unchanged. The release keystore and secret names
    are the same.

Verified on run `42491881254`: debug and release APKs share package
`com.dannx.walls`, version code `132` and certificate SHA-256
`1B:C0:A3:3A:E6:91:09:0A:A8:1B:94:F3:50:02:4B:45:53:27:DE:81:19:0F:13:8C:76:BC:45:89:F9:A0:B2:2C`.

### 4. No change to `.github/dependabot.yml`

The file already separates the `gradle` and `github-actions` ecosystems with
separate limits and commit prefixes. Cadence is not a build-time lever.

### 5. `README.md` / `README.es.md`

No change: artifact names and the rolling release remain the same.

## Verification plan

*   **Correctness gate**: on `main` and tags, `publish` must have
    `needs: [checks, build]`. A deliberately failing test or lint error must
    leave the rolling/tag release untouched. Validate with a throwaway commit
    on a branch or a manual dispatch, not by breaking `main`.
*   **Build**: `debug` must stay green on push/PR; `checks` and `build` must be
    green on `main`; `publish` must produce the rolling release.
*   **Tag path**: a tag publishes a versioned release, so it is created only
    when a release is intended. The mechanism was verified once with a
    throwaway tag `v0.0.0-ci-test` (run `42509692612`): `debug` was skipped,
    `checks` and `build` passed, and `publish` created the release with
    `DannX.apk`. The release and the tag were deleted afterwards, and the
    rolling release was marked latest again.
*   **Timing**: record per-step durations for at least three consecutive
    `main` runs. Report medians; do not compare a cold-cache run with a warm one.
    Target: full path median ≤ 210 s, debug median ≤ 90 s.
*   **Cache health**: inspect Gradle output for `FROM-CACHE` / `UP-TO-DATE` and
    confirm the release build still succeeds when the build cache is cold. Scope
    cold-cache checks to cache-affecting changes, not every PR.
*   **Secrets**: search logs for the actual keystore base64/password values, not
    for secret names. `SUPABASE_ANON_KEY` is public-by-design in the APK;
    keystore material must never appear.
*   **No app changes**: `git diff` for the CI commits must not touch `app/src`.

## Alternatives considered

### A. Enable the Gradle configuration cache without persistence

Rejected. One invocation per job means the cache is written but never read in
the same run. A second run is a fresh VM. The measured configuration phase is
~15 s, so even a perfect cache would not close the gap, while a hand-rolled
`.gradle/configuration-cache` cache adds risk.

### B. Cache `.gradle/configuration-cache` explicitly

Rejected for now. It would require a key over build scripts and Gradle/AGP
versions, would store absolute paths, and would still not help release builds
because `VERSION_CODE` changes every run. Revisit only if a future measurement
shows configuration time growing again.

### C. Keep one full-path job

This was the baseline. Measurement shows lint/tests and R8 are largely
sequential on the critical path; splitting shortens wall time without dropping
checks. If the measured split is slower than the single job, revert the split
and keep this document's baseline numbers.

### D. Run lint/tests before the release build in the same job

Would keep one runner but add the checks to the critical path; strictly worse
for G2.

### E. Remote shared build cache

Would be the standard answer to ephemeral runners, but requires a third-party
service or paid infrastructure, violating NG3.

### F. Stable `VERSION_CODE` for rolling builds

Would let R8 reuse cached output on unchanged code, but would make the rolling
APK non-updatable and change release semantics. Rejected under NG7.

## Open questions

*   **Q1 (answered)**: Yes. Six post-change runs give a 168 s median end-to-end
    against the 276 s baseline, so the split stays. Remaining variance comes
    from R8 and runner speed, not from the split.
*   **Q2**: Does `actions/download-artifact@v7` add measurable time to
    `publish`? It should be a few seconds; measure before optimizing further.
*   **Q3**: Is a scheduled cold-cache check worth the runner minutes, or is a
    manual dispatch sufficient? Start with manual dispatch on cache-affecting
    changes.

## Actualización: debug en PRs y Gradle 9.9 (2026-11-08)

*   El job `debug` corre **solo en pull requests**. En `main` ya no se genera un
    APK de prueba por push: `checks`, `build` y la release rolling cubren ese
    camino, y se ahorra un runner por cada push. G1 sigue midiendo el modo
    rápido, pero ahora sobre PRs.
*   Gradle pasó de 9.4.1 a 9.9.0. La primera corrida llenó la caché del wrapper
    (`checks` 183 s, `build` 174 s); con la caché tibia, `checks` volvió a 117 s
    y el camino completo quedó en ~3 min, dentro de G2.
*   `download-artifact` pasó a v8 y `setup-java` a v6.2.0, fijados a SHA.
