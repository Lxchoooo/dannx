# Privacy Policy — DannX

Last updated: 2026-11-08

DannX is an open-source wallpaper app for Android. This policy explains what the
app stores, what it sends over the network, and what it does not collect.

## Summary

- No accounts are required to browse wallpapers, download them, or use the demo
  feed.
- No analytics, advertising, tracking or crash-reporting SDKs are included.
- Favourites, collections, settings and the offline cache stay on your device.
- The only network service is the Supabase project that hosts the wallpaper
  catalogue. The administrator sign-in for the private Studio screen uses
  Supabase Auth.
- The app does not collect location, contacts, camera, microphone or health data.

## Data stored on your device

DannX keeps the following data in private app storage:

- Favourites.
- Collections you create while using the demo feed.
- Settings: theme, lite mode and whether the editor opens before applying a
  wallpaper.
- The last feed and collections, cached for offline use.
- The list of wallpapers you downloaded, so the app can offer the existing copy.
- If you sign in to the private Studio: your administrator email, user id and
  Supabase session tokens. These live in a separate preferences file that is
  excluded from Android cloud backup and device-to-device transfer.

You can delete this data at any time from Android Settings → Apps → DannX →
Storage → Clear data. The cache can also be cleared from DannX Settings.

## Data sent over the network

- The app downloads the public wallpaper catalogue, images and collection data
  from Supabase over HTTPS.
- If you sign in to the private Studio, your email and password are sent to
  Supabase Auth. The password is not stored by the app; only the returned session
  tokens are kept on the device.
- Changes made in the private Studio (wallpapers, categories, tags and
  collections) are sent to Supabase so the catalogue can be updated.
- Downloaded images go directly from Supabase Storage to your device. DannX does
  not route them through any other server.
- No usage statistics, device identifiers or advertising identifiers are sent.

## Permissions

- `INTERNET` and `ACCESS_NETWORK_STATE`: load wallpapers and detect whether the
  device is offline.
- `SET_WALLPAPER`: apply a wallpaper to the home screen, lock screen or both.
- MediaStore access is used only to save an image to `Pictures/DannX` and to open
  a file you already downloaded.

## Third parties

- **Supabase** hosts the wallpaper catalogue, authentication and image storage.
  Its privacy policy applies to data processed by that service.
- **GitHub** hosts the source code and the release APKs. Opening the repository,
  the releases page or the privacy policy from the app opens GitHub in your
  browser.
- Downloaded images are loaded with Coil/OkHttp directly from the Supabase
  storage URL.

DannX does not sell, rent or share personal data with advertisers or data
brokers.

## Children

DannX is a general-purpose wallpaper app and is not directed to children under
13. The app does not knowingly collect personal information from children.

## Changes

When this policy changes, the updated version will be published in this
repository with a new "Last updated" date. Material changes will be mentioned in
the release notes.

## Contact

For privacy questions, open an issue at
<https://github.com/Lxchoooo/dannx/issues>. For sensitive reports, use the
private channel described in [SECURITY.md](SECURITY.md).
