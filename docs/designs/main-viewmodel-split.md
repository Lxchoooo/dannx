# MainViewModel Split

*   **Status:** in progress — slice 1 (appearance) merged
*   **Date:** 2026-11-16

---

## Why

`MainViewModel` is the largest class in the project (~3,700 lines, 111
functions). It owns the feed, detail, collections, favorites, wallpaper
actions, studio/admin and appearance concerns at once, which makes every
change riskier than it should be and slows down reviews.

The class also drives the whole UI state, so an abrupt rewrite would risk
visual regressions everywhere. The plan is therefore gradual: extract one
cohesive block at a time into a controller class, keep the public API of
`MainViewModel` identical, and let the existing unit tests
(`MainViewModelTest`) pass without changes. Screenshot tests cover the
visible surface.

## Constraints

*   **Public API unchanged:** screens call `viewModel::setThemeMode` and
    friends; each extracted method stays as a one-line delegate.
*   **State stays in `MainViewModel`:** the controllers receive the
    `MutableStateFlow<UiState>` and update it. No second state holder.
*   **Fakes stay valid:** `MainViewModelTest` builds the ViewModel with fakes;
    the controllers only use constructor dependencies the ViewModel already
    receives, so no test changes are needed.
*   **Process memory stays put:** `rememberDecoyOpened` /
    `decoyOpenedInThisProcess` remain in `MainViewModel` (they are process
    state, not appearance state).

## Clusters

| Cluster | Functions (approx.) | Notes |
| --- | --- | --- |
| Appearance & cache | ~9 | theme, lite mode, editor-on-apply, reset, cache size/clear, onboarding — first slice |
| Wallpaper actions | ~15 | download, share, apply (system/lock), set color |
| Collections | ~15 | local collections CRUD, collection detail |
| Favorites | 2 | toggle and remove favorites |
| Feed | ~15 | refresh, pagination, filters, search, daily |
| Studio / admin | ~30 | sign-in, upload, categories CRUD, wallpaper edit, bulk tags |

## Slices

1.  **Appearance** → `AppearanceController` (merged).
2.  Wallpaper actions → `WallpaperActionsController`.
3.  Collections → `CollectionsController`.
4.  Favorites (small; can ride along with collections or feed).
5.  Feed → `FeedController` (largest and most entangled; last of the data
    slices).
6.  Studio → `StudioController` (largest cluster).

Each slice lands as its own PR with CI green before the next starts.

## Outcome so far

*   `AppearanceController` extracted (~90 lines): appearance store, editor
    on apply, reset, cache size/clear and onboarding completion. MainViewModel
    delegates to it with the same method signatures.

## When to stop

Once every cluster is extracted, `MainViewModel` should be a thin shell of
delegates plus the state definition. If the remaining shell is small enough,
the delegates may move to the screens later — but that is optional and out of
scope for this plan.
