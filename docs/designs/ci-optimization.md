# CI and Repository Optimization for Faster Builds

*   **Author:** Lxchoooo
*   **Status:** Revised after Stage 5 review
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
state, resolves the Stage 5 review, and designs the next change: split the full
path into a parallel correctness gate and a release build, with publication
gated on both.

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

### Full path (`release` job)

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

### First post-change measurement

Source: run `42490677444` on `bf264fe` (2026-11-08), the workflow-only commit
that introduced the split. The app source was unchanged from the previous run,
so the app build cache was warm; treat this as a lower bound, not as the
number for a typical Kotlin change.

| Job | Total | Main step | Notes |
| --- | --- | --- | --- |
| `debug` | 42 s | `assembleDebug` 24 s | Warm app cache; a code change will be slower |
| `checks` | 146 s | `lintDebug testDebugUnitTest` 117 s | Compile 28 s, tests 2 s, lint ~37 s |
| `build` | 159 s | `assembleRelease` 133 s | R8 58 s on this run |
| `publish` | 10 s | download + rolling release | After both gates passed |

End-to-end from the first job start to the end of `publish`: ~169 s, versus
~276 s on the pre-split code-change run. The comparison mixes a code-change
baseline with a warm-cache workflow-only sample, so the true saving for a
one-file Kotlin change still needs two more samples. The structural saving is
real: lint and tests no longer sit on the release critical path, and
publication cannot start until both jobs pass.

## Goals

*   **G1**: Keep the fast path at or below its measured baseline. Do not add
    lint or tests to the `debug` job. Target: debug job median ≤ 90 s and Gradle
    step median ≤ 70 s over three consecutive `main` pushes. The earlier 45 s
    target was not supported by the measured setup floor and is withdrawn.
*   **G2**: Cut the full path from ~276 s to ≤ 210 s (3.5 min) by running lint
    and tests in parallel with `assembleRelease`, while keeping lint and tests
    as hard gates for publication. Do not drop either check. First sample:
    ~169 s on a warm app cache; still needs two code-change samples before it is
    treated as confirmed.
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

Proposed full path:

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
| Parallel `checks` vs `build` | Remove lint and tests from the release critical path; first sample ~169 s total, expected ~200–210 s on a real code change | **Implemented in this change** |
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
*   Keep `debug` as-is (except the runner pin).
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

### 3. No changes to `gradle.properties` or `app/build.gradle.kts`

`org.gradle.caching=true`, `org.gradle.parallel=true` and the daemon setting
stay. No configuration-cache flag is added. No provider rewiring is needed
because the configuration cache is not used. The signing and `VERSION_CODE`
wiring stays byte-for-byte compatible with the current release flow.

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
*   **Tag path**: create a test tag only when a release is intended; confirm
    `publish` downloads the artifact and creates the tag release after both
    jobs.
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

*   **Q1**: Does the split actually save ≥ 50 s on the full path across three
    runs? If not, revert to the single job.
*   **Q2**: Does `actions/download-artifact@v7` add measurable time to
    `publish`? It should be a few seconds; measure before optimizing further.
*   **Q3**: Is a scheduled cold-cache check worth the runner minutes, or is a
    manual dispatch sufficient? Start with manual dispatch on cache-affecting
    changes.
