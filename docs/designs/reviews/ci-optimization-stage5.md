# Stage 5 — Initial Zoom Out Review

*   **Target:** `docs/designs/ci-optimization.md` (319 lines, full file)
*   **Reviewers:** `egm-zoom-out`, `egm-inventive-catalyst` (fresh sessions)
*   **Standard:** `resources/document_standards/zoom-out-standard.md`
*   **GoldfishContext:** empty (no additional context files; no external links)

---

## Reviewer 1: egm-zoom-out

### Context Sufficiency (Standard §0) — PASS

The document states the objective (cut CI wait time), the constraint (CI-only
builds, phone author), the current measured state (~83 s push / ~3–3.5 min tag),
goals G1–G5, non-goals NG1–NG6, and open questions Q1–Q3. A fresh reader can
determine what and why. Review proceeds.

### Blockers

#### B1 — Configuration cache has no cross-run persistence; the headline lever cannot deliver as designed

*   **Severity:** Blocker
*   **Location:** Lever 1 (lines 98–116), Lever 2 bullet "do **not** hand-roll
    an `actions/cache` key" (lines 126–128), diagram label `config cache: script`
    (line 87), Goal G1 (lines 39–41), Verification Plan (lines 302–306).
*   **Reasoning:** Gradle stores configuration-cache entries in the **project's**
    `.gradle/` directory. `gradle/actions/setup-gradle` persists only Gradle
    User Home (`~/.gradle/caches`), GitHub-hosted runners are ephemeral, and the
    design proposes no cache for `.gradle/` — while explicitly forbidding
    hand-rolled `actions/cache` keys. Consequences: (a) on the push path the job
    runs `./gradlew assembleDebug` exactly once per fresh VM, so there is *zero*
    config-cache reuse across runs; (b) on the tag path the two invocations
    request different task sets, so their entries would not match even within
    the job; (c) the PR B verification test as written is unrunnable — two
    separate workflow runs cannot see each other's config cache. Lever 1 is
    labeled "highest expected gain" and underpins G1 and PR B; without a
    persistence mechanism the central lever yields no benefit.

### Major Findings

#### M1 — Lever 5 misstates the current workflow (factual error affecting the cost model)

*   **Severity:** Major
*   **Location:** Lever 5, first paragraph (lines 157–159).
*   **Reasoning:** The doc says lint+tests and `assembleRelease` run "as one
    Gradle invocation in one job." `.github/workflows/android.yml` (lines 68–69,
    97–101) runs **two separate `./gradlew` invocations** in one job. Two
    invocations means two configuration phases today — which changes both the
    savings available to Lever 1 and the keep-vs-split arithmetic in Lever 5.

#### M2 — Architecture diagram pre-decides the open PR D choice and misrepresents current state

*   **Severity:** Major
*   **Location:** Technical Plan diagram, full-path box (lines 90–95).
*   **Reasoning:** The diagram labels the full path as "job 1 (lint + test)" and
    "job 2 (assembleRelease)", i.e. a two-job split — but the current workflow is
    a single job and PR D explicitly leaves the split undecided. A high-level
    diagram should show the current/committed structure, not presume the outcome
    of a pending measurement-driven decision. It also omits keystore
    restore/signing and report uploads, which matter to G3.

#### M3 — Goals G1/G2 carry no savings budget; the numbers don't reconcile with the levers

*   **Severity:** Major
*   **Location:** Goals G1–G2 (lines 39–43), Open Question Q3 (lines 73–75),
    Lever 3 (lines 130–144), Lever 4 (lines 146–153).
*   **Reasoning:** G1 requires cutting 83 s → ≤45 s, i.e. ~38 s of savings, yet
    the document never estimates what any lever contributes. The "highest
    expected gain" lever is blocked by B1; Lever 3's own table calls
    checkout/upload bumps "minor"; Lever 4's first two items are no-ops. Q3
    itself says the setup/Gradle split is only *suggested*, not known. Numeric
    targets asserted before the PR A baseline risk becoming commitments the plan
    cannot meet without violating NG4. Either attach per-lever estimates or
    restate G1/G2 as "best after PR A measurement."

#### M4 — Implementation sequence does not cover everything the design says will land

*   **Severity:** Major
*   **Location:** "Everything below lands as separate PRs … in the order listed"
    (lines 207–208) vs. Implementation sequence (lines 210–224) vs. Target Files
    (lines 226–288).
*   **Reasoning:** Lever 2's `gradle-home-cache-cleanup` change, file 4
    (`dependabot.yml`), and file 5 (READMEs) are assigned to **no PR letter**.
    PR E is self-described as containing nothing new — a placeholder PR with no
    content, which contradicts G5's "small, individually verifiable" landing
    plan. The mapping from levers → PRs must be complete.

#### M5 — The Dependabot change rests on a premise that is already false

*   **Severity:** Major
*   **Location:** Target file 4 (lines 270–279); related: "current 7-PR burst"
    (line 277), Q2 (lines 70–72).
*   **Reasoning:** `.github/dependabot.yml` **already splits** `github-actions`
    and `gradle` into separate update entries with separate commit prefixes,
    labels, and PR limits (3 + 2 = 5 open max). Dependabot already opens separate
    PRs per ecosystem, so "split the ecosystems" changes nothing, and the
    "7-PR burst mixing speed-relevant action bumps with app-risk library bumps
    in one review pass" rationale cannot describe how the current config behaves.
    The cadence tweak might still be defensible on its own, but the design needs
    a corrected rationale.

#### M6 — Lever 3 invites double-caching between setup-java and setup-gradle

*   **Severity:** Major
*   **Location:** Lever 3 table, `actions/setup-java-6` row (line 137), read with
    Lever 2 (lines 118–128).
*   **Reasoning:** The current workflow does **not** use setup-java's `cache:`
    feature; caching is already handled by `setup-gradle`. Enabling setup-java's
    Gradle cache alongside setup-gradle is a known redundant/conflicting
    combination that gradle/actions documents against. The design lists the row
    as a possible speed win without ever stating the guard. Also speculative: no
    evidence setup restore is a bottleneck.

#### M7 — Configuration-cache risk analysis is incomplete and its central claim is unproven

*   **Severity:** Major
*   **Location:** Lever 1 bullet 1 (lines 109–114); Target file 3 (lines 257–268).
*   **Reasoning:** (a) The claim that undeclared env reads cause invalidation
    "every run" is asserted without evidence — Gradle tracks environment reads
    as inputs keyed by value, and CI secrets are identical between runs. (b) The
    plan overlooks other configuration-time inputs in `app/build.gradle.kts`:
    the `local.properties` file load (lines 11–16) and the
    `file(releaseStorePath).exists()` signing-config conditional (lines 33–43),
    both classic configuration-cache hazards. (c) The declared-input list omits
    `ANDROID_KEYSTORE_PATH` / `…_PASSWORD` / `…_KEY_ALIAS` / `…_KEY_PASSWORD`,
    which the same `configValue()` function reads at configuration time.

#### M8 — Signing-related safety claims do not match the repository's actual wiring

*   **Severity:** Major
*   **Location:** Goal G3 "release signing untouched" (lines 44–46), diagram
    "signs only if keystore present" (line 93), Target file 3 (lines 257–268).
*   **Reasoning:** The workflow's restore step writes `KEYSTORE_FILE` into
    `GITHUB_ENV`, but `app/build.gradle.kts` reads `ANDROID_KEYSTORE_PATH` — and
    `ANDROID_KEYSTORE_*` are never set anywhere in the workflow. On a clean
    runner with no `local.properties`, the signing config is therefore likely
    never created, i.e. "keystore present" is probably already false on every
    release build. The design asserts signing works and is untouched while
    proposing to rewire exactly these reads for the configuration cache.

### Minor Findings

*   **m1** — Dangling reference: Lever 5 says "decided by measurement in Step 5"
    (line 169); no "Step 5" exists (sequence uses PR A–E, levers use Lever 1–5).
*   **m2** — Layer/Lever taxonomy inconsistent: intro defines three *layers* but
    sections are "Lever 1–5" plus one "**Layer 3** — Artifacts"; Layers 1–2 never
    labeled.
*   **m3** — Stale self-reference: target file 6 says create `design_draft.md`
    while the document lives at `docs/designs/ci-optimization.md`; two copies
    guarantee drift.
*   **m4** — Version claims uncited: action majors and library versions asserted
    with no PR numbers/links; `gradle-home-cache-cleanup` input name must be
    re-validated against the v6 bump rather than assumed.
*   **m5** — Verification mechanics imprecise: "`cache: false` for one run" is
    setup-java syntax, not a `setup-gradle` input (equivalent: `cache-disabled`);
    the setup-gradle cache key is automatic, not author-controlled.
*   **m6** — Lever 4's first two bullets are no-ops (describe the workflow
    exactly as it already is); acceptable as standing guards, not as levers with
    expected gains.
*   **m7** — Cold-cache ritual heavier than needed: clearing the cache once per
    PR is an outsized tax for a rare failure mode; scope to cache-touching PRs.
*   **m8** — "The Problem" mislabels release-path work (lint → test → assemble)
    as fixed overhead of the 83 s push path; the two paths' cost structures
    should be separated.

### What Passes (for the record)

*   **Root problem focus:** Aligns with README reality; NG1–NG6 are tight and the
    design never drifts into app-code changes.
*   **Reuse:** Builds on `setup-gradle`'s built-in caching instead of
    `actions/cache`; uses the existing two-mode structure; names standard
    mechanisms. Nothing to reinvent.
*   **Altitude:** Appropriate level for CI configuration; concrete values
    proportionate; no pseudocode or loop-level weeds.
*   **Alternatives section:** Genuine trade-offs with stated rejection reasons.
*   **Measurement-first sequencing (PR A):** Measures before changing — right
    instinct, just needs the persistence fix and budget reconciliation.

### Verdict

**Does not pass yet.** One Blocker (B1), eight Majors, eight Minors. Defects
concentrated in technical accuracy and internal consistency rather than
over-engineering.

---

## Reviewer 2: egm-inventive-catalyst

### Contradiction check (step 0)

Genuine contradictions exist — the document does not output "No false compromise
detected." Core contradictions: (a) fastest push feedback vs. correctness-gate
coverage; (b) G1's wall-clock target vs. an unremovable runner/setup floor;
(c) Lever 3's "merge only if faster" policy vs. measurement confounds introduced
by the bumps themselves; (d) PR D's parallel split vs. G4's blocking guarantee.

### Major Findings

#### M1 — Configuration cache will not survive between CI runs (Lever 1)

*   **Severity:** Major
*   **Affected Scope:** Lever 1 "highest expected gain"; `gradle.properties` PR B.
*   **False Compromise:** The plan assumes enabling
    `org.gradle.configuration-cache=true` yields reuse "on a second run." Every
    GitHub-hosted run is a fresh VM; Gradle stores the configuration cache in the
    **project's `.gradle/configuration-cache`**, while `setup-gradle` persists
    only **Gradle User Home**. Within a single job there is only one Gradle
    invocation, so no intra-run reuse either. The highest-expected-gain lever
    delivers approximately **zero cross-run benefit** as written.
*   **Proposed Resolution:** Either (a) add an explicit `actions/cache` step for
    `.gradle/configuration-cache`, or (b) re-baseline Lever 1's expected gain
    downward and promote Lever 2 (build-cache hits, already persisted) as the
    primary warm-path lever. Verify in PR A which tasks are actually cache-hit vs.
    re-executed.
*   **Secondary Liabilities:** Extra cache save/restore time; config-cache
    entries embed absolute paths (relocation fragility); tens of MB extra
    storage; a new artifact to debug when entries go stale.

#### M2 — Merges are not test-gated; the two-mode split conflates "build verification" with "correctness gate"

*   **Severity:** Major
*   **Affected Scope:** Goals G5; fast-path definition; Verification Plan.
*   **False Compromise:** The push/PR job runs `assembleDebug` only; Lever 4 even
    instructs to "confirm no step creeps back in." G5 equates "green CI before
    merge" with that job, so `testDebugUnitTest`/`lintDebug` first run at **tag
    time**. The author accepts late failure discovery as unavoidable while
    claiming the design works "without weakening checks."
*   **Proposed Resolution:** **Segmentation** — keep `assembleDebug` as the fast
    build signal and add a cheap correctness gate. First measure
    `testDebugUnitTest` in PR A; if warm cost is ~10 s or less, run it on the
    push/PR path (G1 may still be reachable). If not, run tests on the PR path
    only, or add a scheduled full check on `main` so failures surface before
    release day.
*   **Secondary Liabilities:** Added PR/push latency; flaky tests become merge
    blockers; if the author pushes directly to `main` without PRs, a PR-only gate
    never runs.

#### M3 — Measurement design cannot reliably validate G1/G2 or the Lever 3 merge gate

*   **Severity:** Major
*   **Affected Scope:** Goals lines 39–43; Lever 3 lines 142–144; Verification
    lines 307–309.
*   **False Compromise:** Four confounds are treated as nonexistent:
    1.  G1 is wall-clock push-to-green, including GitHub runner acquisition
        variance (frequently 5–30+ s on free runners). With Q3's ~20 s setup
        floor, queue time alone can consume the entire 45 s budget.
    2.  Single-sample before/after comparisons have no variance estimate;
        runner-to-runner and time-of-day noise will exceed many expected deltas.
    3.  Action bumps change cache keys/layouts (especially `gradle/actions`
        majors): the "after" run of PR C will be a cold-cache run, so the "close
        any bump that does not improve time" policy will systematically **close
        beneficial bumps**.
    4.  `ubuntu-latest` image drift confounds any multi-week comparison.
*   **Proposed Resolution:** Define G1/G2 at **step level** (Gradle step
    duration, excluding queue), take **medians of ≥3 runs** per configuration,
    **pin the runner image** (`ubuntu-24.04`), and require a **cache-warm-up
    run** before timing any cache-affecting bump.

#### M4 — No savings budget maps levers to G1/G2 gaps; "highest expected gain" anchored before the baseline exists

*   **Severity:** Major
*   **Affected Scope:** Goals G1 (−38 s) and G2 (−30–60 s); Lever 1 labeling;
    PR A sequencing.
*   **False Compromise:** The document claims the configuration phase is "the
    single largest remaining fixed cost per build" while Q3 admits the phase
    split is unknown until PR A runs. No per-lever expected-savings table, and no
    acknowledgment of the unremovable floor (runner provisioning + checkout +
    Java/Gradle setup ≈ 20 s+). If no combination plausibly closes 38 s, G1 is a
    false goal that will drive wasted iterations.
*   **Proposed Resolution:** Make PR A produce a phase budget (queue / setup /
    config / compile / dex+package / lint / test) with rough expected savings per
    lever; state the floor explicitly; treat G1 as provisional until the budget
    proves feasibility.

#### M5 — PR D's split-job option silently breaks G4 unless publish is gated on both jobs

*   **Severity:** Major
*   **Affected Scope:** Lever 5 "Split into two jobs"; PR D; G4.
*   **False Compromise:** In the split layout, `lint + tests` and
    `assembleRelease` + upload + `gh release create` run in parallel. As
    described, the release publish step lives in the second job with no `needs:`
    on the first — a failing lint or test run would **not block artifact upload
    or release creation**, violating G4. The single-job option gets this for
    free; the split option's safety wiring is never specified.
*   **Proposed Resolution:** If PR D splits, the publish job must declare
    `needs: [checks]` (and both jobs must be required status checks), with the
    release created only after both succeed. State as a hard constraint of the
    split option.

#### M6 — Likely duplicate lint work on the full path; Lever 5's anchor numbers bias against the split

*   **Severity:** Major
*   **Affected Scope:** Full path; G2; Lever 5 decision input.
*   **False Compromise:** The full path runs `lintDebug` **and**
    `assembleRelease` — AGP wires `lintVitalRelease` into `assembleRelease` by
    default, so lint analysis may run **twice per tag build**, unmentioned in the
    design. Conversely, Lever 5's illustrative "lint+tests ≈ 15 s" is unrealistically
    low for Android lint, anchoring the reader toward "splitting saves almost
    nothing" before measurement. If lint is actually 60–90 s, the split is the
    single largest G2 lever.
*   **Proposed Resolution:** In PR A, record durations for `lintDebug`,
    `lintVitalRelease`, and `testDebugUnitTest` separately. If
    `lintVitalRelease` duplicates full lint, consider excluding it (`-x
    lintVitalRelease`) **only** while `lintDebug` remains a hard gate. Re-run the
    Lever 5 decision on measured numbers, removing the illustrative anchors.

#### M7 — `VERSION_CODE`/`VERSION_NAME` derivation is unstated and may defeat both caches by design

*   **Severity:** Major
*   **Affected Scope:** Warm-cache premise of G1/G2; configuration cache; build
    cache; `fetch-depth: 1`.
*   **False Compromise:** The document treats `VERSION_CODE` as an env input to be
    "wired as a provider" but never states **how it is produced**. If it derives
    from `github.run_number` or a `git rev-list` count, then (a) the value
    changes every run, (b) `buildConfigField` inputs change, (c)
    `compileDebugKotlin` inputs change → **build-cache misses on every run**, and
    (d) the configuration cache invalidates every run regardless of provider
    wiring — making Levers 1 and 2 structurally useless for warm builds. A shallow
    clone also breaks commit-count-derived versions.
*   **Proposed Resolution:** Establish in PR A where `VERSION_CODE`/`VERSION_NAME`
    come from; if run-derived, either fix them to a git-tag/manifest source for
    CI validation builds (keeping release derivation intact), or explicitly
    restate G1/G2 expectations knowing per-run invalidation is inherent.

### Nit Findings

*   **N1** — Lever 3 merge policy makes speed the *only* merge criterion for
    infrastructure actions, ignoring security/deprecation value; closed neutral
    bumps accumulate upgrade debt. *Resolution:* merge neutral bumps that carry
    security/deprecation value; reserve "close" for regressions.
*   **N2** — `gradle-home-cache-cleanup: true` adds scan time to every cache-saving
    job (works against G1) and eviction coordination is asserted without
    evidence. *Resolution:* keep it, declare the cost, verify in PR A/C.
*   **N3** — Cold-cache regression guard taxes **every PR** with a full cold
    build; GitHub exposes no cache-delete UI, so "clear the cache" has no real
    procedure. *Resolution:* verify cold-cache health on schedule or only for
    cache-affecting PRs (B, C); document the real purge path (key bump or caches
    API).
*   **N4** — PR E content-free; `dependabot.yml` and README changes unassigned to
    any PR letter; file 6 stale self-reference (`design_draft.md` vs
    `docs/designs/ci-optimization.md`). *Resolution:* delete PR E or give scope;
    assign files; correct path.
*   **N5** — Secrets verification greps secret *names* (false positives — names
    legitimately appear in step listings) and lists `SUPABASE_ANON_KEY` alongside
    keystore material although it is public-by-design in the APK.
    *Resolution:* grep for secret *values* and keystore material; document which
    values are public by design.
*   **N6** — Cache hit-rate and cross-job/tag cache sharing asserted, not
    measured: default `cache-read-only` behavior differs by ref; tag builds may
    restore a different key scope than `main` push builds. If tag builds are
    effectively cold, that explains the 3–3.5 min full path. *Resolution:* record
    cache hit/miss per job type in PR A.
*   **N7** — Lever 1's declared risk set incomplete: build-script code touching
    `project` at execution time, plugins with incompatible build logic, harder
    diagnosis of invalidation causes, absolute-path storage.
    *Resolution:* list explicitly; add configuration-cache report on first
    failure to PR B procedure.

### Missed Opportunities & Stronger Alternatives (not in the document)

*   **O1 — Cancel superseded runs (Nit, potentially Major):** no
    `concurrency: { group, cancel-in-progress: true }` for push builds. Zero-cost
    reduction of time-to-green for the latest commit. *(Note: the repository's
    workflow already has this; the document does not mention it — verify.)*
*   **O2 — Two-tier fast feedback (Nit):** an explicit alternative of
    `compileDebugKotlin`-only (<20 s) as a first signal plus `assembleDebug` as
    the full check was never considered-and-rejected; adding it to Alternatives
    would strengthen the document even if rejected.
*   **O3 — Remote shared build cache (Nit):** Develocity/remote-cache is the
    standard industry answer to ephemeral-runner cache loss (and the real fix for
    M1); presumably excluded by cost/NG3 concerns, but not listed among rejected
    alternatives.
*   **O4 — Measurement-driven prioritization stated but not enforced (folded
    into M4/M6):** PR B (config cache) lands before the phase budget exists;
    re-ordering to "PR A budget → pick top two levers → PR B" would prevent
    investing in a lever whose premise fails.

### What Holds Up

*   Context sufficiency: objective, constraints, and success metrics are clear.
*   Two-mode structure, `paths-ignore`, artifact retention, and rejected
    alternatives A–C are sound; the one-line revert cushioning on Lever 1 is good
    practice.
*   Platform-standard advice (do not hand-roll `actions/cache` keys around
    `setup-gradle`) matches "Leverage Existing."
*   NG1–NG6 are coherent and mutually consistent; NG4 correctly protects
    lint/tests from being traded for speed.

### Bottom Line

Well-sequenced and measurement-aware in intent, but its headline lever (M1)
likely does not function on ephemeral runners as written, its evidence base
(M3/M4/M7) cannot currently prove its own goals, and two safety properties (M2
merge gating, M5 release gating) have gaps the text does not yet see.

---

## Stage 5 Combined Severity Tally

| Severity | Count | IDs |
| --- | --- | --- |
| 🔴 Blocker | 1 | zoom-out B1 (= catalyst M1: config-cache persistence) |
| 🟡 Major | 11 | zoom-out M1–M8, catalyst M2–M7 (deduplicated: catalyst M1 = B1) |
| 🟢 Nit/Minor | 15 | zoom-out m1–m8, catalyst N1–N7, O1–O4 |

*Top theme: the configuration-cache lever (B1/M1) has no persistence mechanism
on ephemeral runners — both reviewers independently found this as the central
defect.*
