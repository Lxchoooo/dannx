# Releasing

Short checklist for publishing a new version. Everything runs on GitHub
Actions; nothing has to be built on the phone.

## 1. Prepare the notes

- Add the version section to `CHANGELOG.md`, in English.
- Write `docs/releases/v<version>.md` with the text people will read in the
  release and in the in-app updater. Keep it short and user-facing.

## 2. Bump the default version

`app/build.gradle.kts` uses `VERSION_NAME` when CI provides it (tag builds) and
a fallback otherwise. Set the fallback to the version being prepared, so builds
from `main` report the same number as the release.

## 3. Validate on `main`

Push the changes and wait for the Android CI run to go green (lint, unit tests,
instrumented tests and the release build).

## 4. Publish

```sh
git tag -a vX.Y.Z -m "DannX X.Y.Z"
git push origin vX.Y.Z
```

The tag run repeats all the checks and then publishes `DannX.apk` and
`DannX.apk.sha256` with the notes from step 1.

## 5. Check the result

- The release is not a draft or a pre-release.
- Both assets are present and the checksum matches the APK.
- The APK reports the new version and installs on top of the previous one.

## After the release

Move the fallback version in `app/build.gradle.kts` to the next one, so builds
from `main` never offer an update they already include.
