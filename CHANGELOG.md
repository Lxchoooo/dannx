# Changelog

What changed in DannX, newest first. An entry is added when a block of work is
closed and when a version is published. Maintainers keep the technical detail in
the repository history.

## Unreleased

### Stability

- Maintenance and stability improvements.
- Repository checks now cover the workflows and the scripts.

## v1.7.0

### Tags

- A tag written in capitals or lowercase now counts as the same tag: it is shown
  with a single spelling and the Home filters find it either way.
- Home lets you pick one or several tags, and the selection is kept when you
  move between screens.
- Tags with spaces, commas or symbols work like any other tag.

### Stability

- Stability and maintenance improvements.

## v1.6.0

### Updater and feed

- Settings now checks the latest GitHub release, downloads the APK into the
  internal cache and opens the system installer. If unknown apps are not
  allowed, the matching setting is opened.
- The feed asks for 30 wallpapers per page and loads the next one at the end,
  without duplicates.
- Search, category and tag are resolved on the server. Search covers title,
  category and author; tags match exactly.
- The cache is still capped at 400 wallpapers and now merges after each page. It
  is only written when no filters are active.
- A “Surprise me” chip opens a random wallpaper from the current result and
  avoids repeating the last one.
- Accessibility pass: roles and states on cards, favourite descriptions, touch
  targets and large-text scaling.
- Release notes are read from an English file per version.
- New tests for version comparison, updater, pagination, filters and cache.

## v1.5.0

### Studio: trash and bulk actions

- Bulk actions in the Studio are applied as a single operation: feature,
  archive, move to a category, delete and restore. If one fails, no half-done
  batch is left behind.
- New trash: deleting a wallpaper no longer removes the image. It can be
  restored, deleted permanently or emptied from the Studio.
- Deleted wallpapers are purged automatically after 30 days, and the scheduled
  cleanup removes the leftover files.
- Bulk actions fall back to sequential calls when the server has not been
  updated yet. The trash does require the new version.
- Content adds a preview of the cover with the current sentence, notice and
  featured wallpapers.
- New tests for moving to the trash, restoring, emptying and keeping the local
  state after archiving or moving in bulk.

### Translation and editor

- The whole interface moves to string resources and is translated to English; no
  hardcoded text is left on the screens.
- Strings can be resolved from the ViewModels and from non-Compose layers.
- The editor adds real progressive blur: three preset levels, blending between
  adjacent levels and a final render on a reduced copy.
- The blur is computed once per level; moving the control does not blur the
  image again.
- New tests for the blur: uniformity, variance, alpha and blending.

### Documentation and community

- Contributing guide with setup, style, tests and a pull request checklist.
- Code of conduct based on Contributor Covenant 2.1.
- Security policy with the private channel and the project practices.
- Roadmap with priorities, status and things that will not be done.
- Privacy policy, linked from the README and from Settings.
- The README stops showing screenshots that did not exist; they were updated
  with the 1.5.0 build.
- Issue templates updated: 1.5.0, Editor and Privacy sections, and a fixed
  contributing link.

### Stability and internal maintenance

- Hardened the backend, the signing and the distribution of the app.
- Release builds are signed with a key kept outside the repository, and tags
  publish that APK instead of a debug build.
- Version numbers come from Gradle properties; CI uses the run number and the
  tag.
- The pipeline is split into verification (lint and tests on every pull request)
  and packaging (signed APKs on `main` and tags).
- Collections are private by default, on the server and in the app when the
  field is missing.
- Release APKs no longer include debug logs.

### Improvements

- Settings adds About (version, links and third-party licences) and a direct
  link to the privacy policy.
- On Android 13 or later, Settings includes Language and opens the per-app
  language picker, so the language can change without touching the phone.
- About adds “Share DannX” with the repository link.
- The feed search shows a button to clear the text when there is an active
  query.
- Search keeps the last 8 queries and offers them as chips when the field is
  empty, with an action to clear them.
- The Studio adds a summary with counters and shortcuts to published, archived
  and featured wallpapers.
- My wallpapers lets you search, filter by category or featured and sort by
  date, title or category.
- Each wallpaper can be featured or unfeatured without opening the editor, and
  the Studio can be refreshed from the header.
- Categories can be reordered with arrows and the new order is saved.
- The Studio adds Content to edit the cover sentence and the global notice. The
  texts are saved on the server and read without a session.
- My wallpapers lets you select several wallpapers and feature, unfeature,
  archive, restore, move or delete them in bulk.
- Editing a wallpaper allows changing the author as well as the title, category,
  tags and featured state.
- The Studio adds Featured to reorder the carousel with arrows; the order is
  saved and respected by Home.
- The wallpaper screen adds side buttons to move to the previous or next one
  without going back to the feed.
- The feed cache is capped at 400 wallpapers and 100 collections so it cannot
  grow forever.
- CI publishes the R8 mapping for 30 days so obfuscated crashes can be read.
- Release notes are generated from the published version section, with a
  fallback text when it is empty.
- New cache tests (cap and corrupt file) and string resolver tests.
- Gradle updated to 9.9.0; the outdated version warning in CI is gone.
- The debug job runs only on pull requests: on `main` the rolling build is
  enough, so one runner per push is saved.
- Settings includes “Check for updates”, which opens the latest release.
- Dependabot groups minor updates for AndroidX, Kotlin and the build tools.
- The editor marks its state and filters as immutable to reduce recompositions.
- Local builds move to version 1.5.0.
- Updated `core-splashscreen` 1.2.0, `kotlinx-coroutines-test` 1.11.0 and the
  `setup-java` 6.2.0 and `download-artifact` 8.0.2 actions.
- Lint warnings cleaned up: KTX extensions in preferences, URIs and bitmaps,
  window sizes in Compose and an unnecessary `super` call.
- The detail screen shows a “this can take a moment” hint while applying a
  wallpaper.

## v1.3.0

### Experience

- Updated libraries: Compose BOM 2026.09.00 (Compose 1.11), Material 3
  1.5.0-rc01 for the official Expressive kit, core 1.19.1, activity 1.13.0,
  lifecycle 2.11.0, navigation 2.10.2 and graphics-shapes 1.1.0.
- The whole theme uses expressive motion, and the onboarding shows brand shapes
  as a decorative background.
- The welcome onboarding gets its first pass: three full-screen pages with a
  horizontal pager, display typography and accent shapes, in the Material 3
  Expressive style. It is shown once and can be skipped.
- The Studio tab no longer sits in the bottom bar; it opens as a standalone
  screen.
- The feed adapts to the screen size: 2 columns on phones, 3 on small tablets
  and 4 on large tablets or split screen; the hero is limited so it does not
  cover the rest.
- Accessibility: favourite and remove buttons are at least 48 dp, TalkBack reads
  each card as one sentence, back arrows are mirrored in right-to-left
  languages, and images show a loading indicator and a notice when they fail.
- Removed unused code.

### Fixes

- Tag operations, category renaming, deleting with reassignment and reordering
  collections use a single operation on the server when it is available, and
  keep the previous path otherwise so compatibility is not broken.
- Debug APKs from CI are signed with the same key as release when the secret is
  available, and use the run's version number so they install on top without
  uninstalling.

### Stability

- When the app comes back or refresh is tapped, the previous feed stays visible:
  it is no longer replaced by the loading skeleton. Only a thin progress bar
  appears while it refreshes in the background.
- If collections fail but the feed responds, a small notice is shown without
  hiding the content.
- A 403 has its own message instead of being confused with an expired session.
  The server error message no longer mixes English and Spanish.
- Parsing errors are no longer retried: they are deterministic and retrying only
  delayed the notice.
- The feed cache is written atomically (temporary file and replacement), so a
  close in the middle of a write does not corrupt it.
- The detail image is requested at screen size instead of full resolution, to
  use less memory and avoid out-of-memory closes.

### Robustness

**Errors and network**

- Typed network errors: no connection, server down, expired session or
  unexpected failure, each with its own message.
- Retries with exponential backoff only on transient failures; never on 4xx
  errors or an expired session.
- Timeouts set to 15 s for connection, 45 s for reading and 120 s for writing.
- On a 401 the token is renewed once and the request is retried; if it fails
  again, the session closes cleanly with a notice.
- Image uploads stream from the URI in blocks instead of loading the whole file
  into memory.

**Screen states**

- A sealed loading state (loading, content, empty, error) replaces loose
  booleans.
- Every screen shows the four states: a light skeleton, content, an empty state
  with useful text and an error with a Retry button.
- New strings for those states.

**Offline and cache**

- The last feed and collections are stored on disk.
- Without a network the saved content is shown with a discreet “Offline”
  notice.
- Download, apply and share are disabled offline; favourites and local
  collections keep working.
- Coming back to the app refreshes once and only when there is a network.

**Navigation and state**

- Single-top navigation on the detail and collection screens: double taps no
  longer stack screens.
- Filters, search and the selected image survive rotation and the system killing
  the app.
- Concurrent refreshes are cancelled: requests and states do not stack.

**Tests**

- The ViewModel no longer depends on Android and is tested on the JVM with
  fakes.
- New tests: cancelled refresh, transient error with retry, 401 with refresh,
  offline cache, download and duplicates, and the retry policy.

### Performance

- Action state (download, apply, share) lives in its own flow: progress no
  longer recomposes the whole app.
- Models and UI state are marked immutable.
- The hero carousel reads its state only in the draw phase.
- Image requests are remembered; hero and covers use explicit sizes.
- Gradients are remembered, redundant clipping removed and the bottom bar no
  longer animates on tab changes.
- Base profile with the profile installer and manual rules.
- FPS overlay: one update per second.
- Compose compiler reports are published on every CI run; they showed that the
  main ViewModel was unstable and invalidated the whole app.
- The main ViewModel is marked stable and thumbnails are prefetched once per
  feed load.
- RGB_565 for thumbnails stays as a documented option, off by default: it saves
  memory and decoding but adds banding to gradients.

### Core features

**Apply wallpaper**

- Bottom sheet with Home screen, Lock screen and Both.
- Fixed the out-of-memory crash: the bitmap is measured, a sample size is
  calculated and only the needed region is decoded.
- Center crop to the screen ratio: the image is scaled to the exact size,
  without stretching or bands.
- Everything on a background thread, with progress, a cancel button and clear
  messages.

**Download**

- Streamed straight into the gallery: the image is never fully loaded into
  memory.
- Visible progress with a percentage and a cancel action that removes the
  pending row.
- Unique name and real extension detected from the file signature, not only from
  the content type.
- Duplicate detection: if the file is still on the phone, the existing copy can
  be opened or downloaded again.
- Final snackbar with an Open action.

**Zoom**

- Pinch, drag and double tap (1x ↔ 2.5x) with animation.
- Scale between 1x and 5x, with drag limits based on the scaled size.
- Zoom and pan survive rotation.

**Share**

- The image is shared from the cache with a temporary read permission.
- Sharing no longer leaves a copy in the gallery.
- Old shared files are cleaned up.

### Audit

- Full review of the project: code, resources, build, CI and the server setup.
- Prioritised risk report (critical, important, minor) with the open points at
  the time.

### Earlier performance work

- Uniform 2-column 9:16 grids (instead of a staggered grid) in Explore,
  Favourites and collection detail.
- Deterministic thumbnail pipeline: fixed size in cards and prefetch, prefetch
  limited to 12 with low priority and a short delayed start.
- Limited decoding parallelism and a short crossfade only on the hero.
- New adaptive icon with a monochrome layer, splash screen and shortcuts.
- Stable debug signing so updates install on top without uninstalling.
- Professional README and development texts moved out of the app.

### Verification

- CI builds debug and release (R8 with minify and resource shrinking) and runs
  unit tests on every push to `main`.
- New tests for crop geometry and sampling, and for zoom limits.
