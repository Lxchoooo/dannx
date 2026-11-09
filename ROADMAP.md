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

## 1.5.0 (in progress)

- [x] Full English translation and progressive blur in the editor.
- [x] About screen, privacy link in Settings and public project links.
- [x] Feed cache capped at 400 wallpapers and 100 collections.
- [x] CodeQL, dependency review, wrapper validation, checksums and build
      provenance attestations.
- [x] Atomic bulk actions with Supabase RPCs.
- [x] Trash with restore, permanent delete and automatic 30-day purge.
- [x] Home preview in the Studio content editor.
- [ ] Green CI run for the release branch.
- [ ] Screenshots for the README.
- [ ] Tag and publish `v1.5.0`.

## Next

- [ ] **Baseline profile and Macrobenchmark** to improve startup and scrolling
      on low-end devices.
- [ ] **In-app updater** that checks the latest release, compares versions and
      opens the download. Settings currently opens the releases page.
- [ ] **Feed pagination and incremental cache** so the app does not download and
      keep every wallpaper at once.
- [ ] **Accessibility pass**: TalkBack, contrast, large font sizes and touch
      targets.

## Product

- [ ] **Web admin panel** for managing wallpapers without using the app.
- [ ] **Signed release distributed outside GitHub**, with checksum and
      attestation verification.
- [ ] **Widgets and automatic rotation**: wallpaper of the day or change every
      few hours.
- [ ] **More languages**, starting with Portuguese and French.

## Quality and CI

- [x] R8 mapping published as a CI artifact.
- [ ] Instrumented tests for applying, downloading, favourites and the editor.
- [ ] Screenshot tests (Roborazzi or Paparazzi) to catch visual changes.
- [ ] `actionlint` and `zizmor` checks for GitHub Actions.
- [ ] Release notes generated from the changelog or commits.

## Backend and security

- [ ] RLS tests that prove anonymous users cannot write.
- [x] Storage policies and cleanup of orphaned images from the trash.
- [ ] Rate limiting for writes.
- [ ] Updated Supabase setup guide in `docs/`.
- [ ] Secret scanning, push protection and private vulnerability reporting
      enabled in the GitHub settings.

## Not planned

- Analytics, advertising or tracking SDKs.
- Public Supabase registration.
- Putting the service key in the app or repository.

## How to help

Open an [issue](https://github.com/Lxchoooo/dannx/issues) with the idea or bug.
Read [CONTRIBUTING.md](CONTRIBUTING.md) before sending a pull request, and report
security issues through [SECURITY.md](SECURITY.md).
