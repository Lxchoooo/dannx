# DannX

English | [Español](README.es.md)

[![Android CI](https://github.com/Lxchoooo/dannx/actions/workflows/android.yml/badge.svg)](https://github.com/Lxchoooo/dannx/actions/workflows/android.yml)
![Android 10+](https://img.shields.io/badge/Android-10%2B-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-2.2.20-7F52FF?logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-BOM%202026.09.00-4285F4?logo=jetpackcompose&logoColor=white)
![Material 3](https://img.shields.io/badge/Material%203-1.5.0--rc01-6750A4?logo=materialdesign&logoColor=white)
![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)

A wallpaper app for Android built with Kotlin and Jetpack Compose. Explore a
curated feed, organise your favourites into collections, and apply or download
any image in a couple of taps. The backend is optional: without it, the app runs
with a built-in demo feed.

## 📸 Screenshots

| Explore | Detail |
| --- | --- |
| ![Explore](docs/screenshots/explore.png) | ![Detail](docs/screenshots/detail.png) |

| Collections | Favorites |
| --- | --- |
| ![Collections](docs/screenshots/collections.png) | ![Favorites](docs/screenshots/favorites.png) |

> The screenshots live in [`docs/screenshots/`](docs/screenshots/). See the
> note there if the images are not available yet.

## ✨ Features

- Feed with featured wallpapers, search, categories and free-form tags.
- Collections you create inside the app, public or private.
- Detail view with pinch zoom, double-tap zoom and pan.
- Download to the gallery (`Pictures/DannX`) through the system MediaStore.
- Apply as home screen, lock screen, or both — cropped automatically.
- Share any wallpaper with other apps.
- Favourites saved on the device.
- Adaptive monochrome icon that follows the system theme (Android 13+),
  splash screen and launcher shortcuts.
- Works offline: the feed you already loaded stays available.
- Dark and light themes, with dynamic colour on Android 12+.
- Welcome screens shown once on first launch.

## ⬇️ Download and install

1. Get the APK from the
   [Releases](https://github.com/Lxchoooo/dannx/releases) page (each tagged
   version has a `DannX.apk` asset), or from
   [Actions](https://github.com/Lxchoooo/dannx/actions) — download the
   `DannX-release` artifact of the latest successful run.
2. Open the APK on your phone and allow installation from **unknown sources**
   when Android asks for it.
3. Updates install on top of the existing app: your favourites, collections and
   settings are kept.

Requirements: **Android 10 (API 29) or newer**. Release builds are signed with a
private key stored as a GitHub secret; the key is never committed to the
repository.

## ☁️ Backend (optional, Supabase)

DannX works out of the box with a demo feed. To publish your own wallpapers you
connect a free [Supabase](https://supabase.com) project:

1. Create a project and run [`supabase/setup.sql`](supabase/setup.sql) in the
   SQL Editor. Replace the admin UUID in the file with your own user's UID.
2. Copy the **Project URL** and the **anon public** key from
   *Project Settings → API*.
3. Add them as repository secrets with the exact names the build expects:
   `SUPABASE_URL` and `SUPABASE_ANON_KEY`.
4. Push to `main`: the CI builds a new APK with those values baked in.

Notes:

- Without these two values the app shows the built-in demo feed.
- The **anon** key is public by design; the database rules (RLS) are what
  protect the data. The `service_role` key is **never** used in the app or in
  the repository.
- Step-by-step guide with screenshots of the dashboard:
  [`CONECTAR-SUPABASE.md`](CONECTAR-SUPABASE.md).

## 🛠 Development

Requirements: **JDK 17** and the Android SDK (the project compiles against
SDK 37, targets SDK 36 and has a minimum of SDK 29).

```bash
./gradlew assembleDebug        # debug APK
./gradlew lintDebug            # lint report
./gradlew testDebugUnitTest    # unit tests
./gradlew assembleRelease      # signed release APK (needs the keystore)
```

Project layout:

```
app/src/main/java/com/dannx/walls/
├── data/      # API client, cache, preferences, demo feed, retry policy
├── ui/        # screens, components, theme, window size classes
└── util/      # wallpaper files (gallery, apply) and geometry helpers
supabase/      # setup.sql for the backend
.github/       # CI workflows
docs/          # screenshots and documentation assets
```

## 🗺 Roadmap

The following are **planned** and not available yet:

- Web admin panel for managing wallpapers.
- A base profile to improve performance on low-end devices.
- Signed release distributed outside GitHub.
- In-app updater.

## 🐞 Contributing

Found a bug or have an idea? Open an
[issue](https://github.com/Lxchoooo/dannx/issues) with the details. Pull
requests are welcome.

## 📄 License

Released under the [MIT License](LICENSE). You can use, copy, modify and
distribute the code, keeping the copyright notice.

Wallpapers included in the demo feed are original works and are covered by
their own authorship and licensing terms. They are not part of the code license.

## 🎨 Credits

Wallpapers included in the demo feed are original works and are covered by
their own authorship and licensing terms. They are not part of the code license.
