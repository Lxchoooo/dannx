# DannX

English | [Español](README.es.md)

[![Android CI](https://github.com/Lxchoooo/dannx/actions/workflows/android.yml/badge.svg)](https://github.com/Lxchoooo/dannx/actions/workflows/android.yml)
[![CodeQL](https://github.com/Lxchoooo/dannx/actions/workflows/codeql.yml/badge.svg)](https://github.com/Lxchoooo/dannx/actions/workflows/codeql.yml)
![Android 10+](https://img.shields.io/badge/Android-10%2B-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-2.4.21-7F52FF?logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-BOM%202026.09.00-4285F4?logo=jetpackcompose&logoColor=white)
![Material 3](https://img.shields.io/badge/Material%203-1.5.0--rc01-6750A4?logo=materialdesign&logoColor=white)
![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)

A wallpaper app for Android built with Kotlin and Jetpack Compose. Explore a
curated feed, organise your favourites into collections, and apply or download
any image in a couple of taps. It ships with a built-in demo feed, so it works
right after installing.

## 📸 Screenshots

Screenshots for the current build live in
[`docs/screenshots/`](docs/screenshots/). The 1.4.0 set is being refreshed; see
the note there for how to take them.

## ✨ Features

- Feed with featured wallpapers, search, categories and free-form tags.
- Collections you create inside the app, public or private.
- Detail view with pinch zoom, double-tap zoom and pan.
- Built-in editor with crop, colour adjustments, filters and real progressive
  blur.
- Download to the gallery (`Pictures/DannX`) through the system MediaStore.
- Apply as home screen, lock screen, or both — cropped automatically.
- Share any wallpaper with other apps.
- Favourites saved on the device.
- Spanish and English interface.
- Adaptive monochrome icon that follows the system theme (Android 13+),
  splash screen and launcher shortcuts.
- Works offline: the feed you already loaded stays available.
- Dark and light themes, with dynamic colour on Android 12+.
- Settings screen: theme (system, light, dark), lite mode, cache reset, an
  About section with the privacy policy and a shortcut to the latest release.
- Detail buttons take the colour of the wallpaper you are looking at.
- Welcome screens shown once on first launch.

## ⬇️ Download and install

1. Get the APK from the
   [Releases](https://github.com/Lxchoooo/dannx/releases) page: the
   **DannX · última versión** release always has the newest signed build, and
   it is rebuilt automatically on every merge to `main`. If you need a specific
   run, the `DannX-release-<number>` artifact is also available in
   [Actions](https://github.com/Lxchoooo/dannx/actions).
2. Open the APK on your phone and allow installation from **unknown sources**
   when Android asks for it.
3. Updates install on top of the existing app: your favourites, collections and
   settings are kept.
4. Debug artifacts that CI builds with the repository's keystore secret use the
   same signing key as releases, so they also install on top of the published
   app. Use the latest run's artifact; an older debug build cannot replace a
   newer release.

Requirements: **Android 10 (API 29) or newer**. Release builds are signed with a
private key stored as a GitHub secret; the key is never committed to the
repository.

## 🛠 Development

Requirements: **JDK 17** and the Android SDK (the project compiles against
SDK 37, targets SDK 36 and has a minimum of SDK 29).

```bash
./gradlew assembleDebug        # debug APK
./gradlew lintDebug            # lint report
./gradlew testDebugUnitTest    # unit tests
./gradlew assembleRelease      # signed release APK (needs the keystore)
```

`assembleDebug` uses the release keystore when `ANDROID_KEYSTORE_PATH` is set;
CI does this so debug artifacts install over the published release. Without the
keystore, Gradle falls back to the standard debug key.

Project layout:

```
app/src/main/java/com/dannx/walls/
├── data/      # API client, cache, preferences, demo feed, retry policy
├── ui/        # screens, components, theme, window size classes
└── util/      # wallpaper files (gallery, apply) and geometry helpers
.github/       # CI workflows
docs/          # screenshots and documentation assets
```

See [CONTRIBUTING.md](CONTRIBUTING.md) for the full development setup, code
style and pull request checklist.

## 🗺 Roadmap

The following are **planned** and not available yet:

- Web admin panel for managing wallpapers.
- A base profile to improve performance on low-end devices.
- Signed release distributed outside GitHub.
- A full in-app updater (for now, Settings opens the releases page).

See [ROADMAP.md](ROADMAP.md) for the full priority list and what is already
done.

## 🐞 Contributing

Found a bug or have an idea? Open an
[issue](https://github.com/Lxchoooo/dannx/issues) with the details. Pull
requests are welcome.

Read [CONTRIBUTING.md](CONTRIBUTING.md) before sending changes. By participating
you agree to the [Code of Conduct](CODE_OF_CONDUCT.md).

## 🔒 Privacy

DannX does not include analytics, advertising or tracking SDKs. Favourites,
collections, settings and the offline cache stay on your device. See
[PRIVACY.md](PRIVACY.md) for the details.

## 🛡 Security

Please do not open a public issue for vulnerabilities. Follow
[SECURITY.md](SECURITY.md) to report them privately.

Every release ships `DannX.apk.sha256` and a build provenance attestation. After
downloading, verify the checksum with `sha256sum -c DannX.apk.sha256` or the
attestation with `gh attestation verify DannX.apk --repo Lxchoooo/dannx`.

## 📄 License

Released under the [MIT License](LICENSE). You can use, copy, modify and
distribute the code, keeping the copyright notice.

Wallpapers included in the demo feed are original works and are covered by
their own authorship and licensing terms. They are not part of the code license.

## 🎨 Credits

Wallpapers included in the demo feed are original works and are covered by
their own authorship and licensing terms. They are not part of the code license.
