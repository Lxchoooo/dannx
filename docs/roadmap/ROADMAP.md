# Roadmap

English | [Español](ROADMAP.es.md)

DannX is a small, privacy-first wallpaper app. This is a priority list, not a
promise of dates. Items move when they are tested and when there is time to
maintain them.

## Status legend

- **Done**: implemented in the repository.
- **Next**: the current focus.
- **Planned**: accepted, but not started.
- **Exploring**: needs a design decision first.

## 1.8.0

- [x] **Lock screen preview** on the detail screen, with the time, the date and
      generic shortcuts, respecting Lite mode.
- [x] **Related wallpapers** by category and tags, capped at five and with
      navigation that replaces the current entry.
- [x] **Macrobenchmark module** with startup and scroll measurements, plus a
      first reference measurement.
- [x] Fixes for feed pagination, the download User-Agent and the Storage bucket
      in the migrations.
- [ ] Tag and publish `v1.8.0`.

## 1.7.0

- [x] **Tag filters on Home**, with a single spelling for tags written in
      different case and a selection that survives navigation.
- [x] Stability and maintenance on the server and in the app.
- [x] Tag and publish `v1.7.0`.

## 1.6.0

- [x] **In-app updater** that checks the latest release, downloads the APK and
      opens the Android installer.
- [x] **Feed pagination and incremental cache**: 30 wallpapers per page,
      server-side search and filters, and a 400-wallpaper offline cache.
- [x] **Surprise me** button for a random wallpaper from the current filter.
- [x] **Accessibility pass**: TalkBack labels and states, larger touch targets
      and better large-font behaviour.
- [x] Release notes read from an English file under `docs/releases/`.
- [x] Green CI run for the release branch.
- [x] Tag and publish `v1.6.0`.

## 1.5.0

- [x] Full English translation and progressive blur in the editor.
- [x] About screen, privacy link in Settings and public project links.
- [x] Feed cache capped at 400 wallpapers and 100 collections.
- [x] CodeQL, dependency review, wrapper validation, checksums and build
      provenance attestations.
- [x] Atomic bulk actions with Supabase RPCs.
- [x] Trash with restore, permanent delete and automatic 30-day purge.
- [x] Home preview in the Studio content editor.
- [x] Tag and publish `v1.5.0`.

## Next

- [ ] **Baseline profile and Macrobenchmark** to improve startup and scrolling
      on low-end devices.
  - [x] `:benchmark` module with startup and feed-scroll benchmarks, and a
        manual CI job that runs them on an emulator.
  - [x] First measurement: 757 ms median cold startup on the API 34 emulator,
        kept as the reference row in
        [`benchmarking.md`](../designs/benchmarking.md).
  - [x] Generate the profile against a non-minified variant and replace the
        handwritten `app/src/main/baseline-prof.txt`: cold startup went from
        973 ms to 729 ms on the same emulator
        ([`benchmarking.md`](../designs/benchmarking.md)).

## Product

- [ ] **Web admin panel** for managing wallpapers without using the app.
- [ ] **Signed release distributed outside GitHub**, with checksum and
      attestation verification.
- [ ] **Widgets and automatic rotation**: wallpaper of the day or change every
      few hours.
- [x] **More languages**, starting with Portuguese and French.

## Quality and CI

- [x] R8 mapping published as a CI artifact.
- [x] Instrumented tests for applying a wallpaper and for the favourites
      selection.
- [ ] Instrumented tests for downloading and for the editor.
- [x] Screenshot tests with Roborazzi: onboarding, About, decoy and states in
      the 4 languages and light/dark; `verifyRoborazziDebug` runs in CI and the
      goldens are regenerated with the `Record screenshots` workflow.
- [x] `actionlint` checks for workflows and `shellcheck` for scripts.
- [x] `zizmor` checks for GitHub Actions.
- [x] Release notes generated from a versioned English file.

## Backend and security

- [x] RLS tests that prove anonymous users cannot write.
- [x] Storage policies and cleanup of orphaned images from the trash.
- [x] Rate limiting: no write limiter (only the admin can write) and
      `statement_timeout` plus `pg_stat_statements` measurement
      ([design and decision](../designs/rate-limiting.md)).
- [x] Supabase setup guide updated
      ([`supabase/README.md`](../supabase/README.md)).
- [x] Secret scanning, push protection and private vulnerability reporting
      enabled in the GitHub settings.
- [x] Restrict the allowed GitHub Actions to GitHub-owned and verified
      creators, plus the two third parties in use.

## Not planned

- Analytics, advertising or tracking SDKs.
- Public Supabase registration.
- Putting the service key in the app or repository.

## How to help

Open an [issue](https://github.com/Lxchoooo/dannx/issues) with the idea or bug.
Read [CONTRIBUTING.md](../../CONTRIBUTING.md) before sending a pull request, and report
security issues through [SECURITY.md](../../SECURITY.md).
