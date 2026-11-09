# Privacy Policy — DannX

English | [Español](PRIVACY.es.md)

Last updated: 2026-11-09

DannX is an open-source wallpaper app for Android, distributed through GitHub
Releases. This policy explains what the app keeps on your device, what it sends
over the network, and the choices you have. It applies to the app itself, not to
websites or services you open from it.

## Summary

- You can use DannX without creating an account or providing personal
  information.
- There are no analytics, advertising, tracking or crash-reporting services.
- Your favourites, collections, preferences and offline cache stay in the app's
  private storage.
- Network access is used only to load the public wallpaper catalogue and images,
  and to check for and download app updates.
- DannX does not request location, contacts, camera, microphone or health data,
  and does not use advertising identifiers.

## Data kept on your device

DannX stores the following in its private app storage:

- Favourites and the collections you create.
- Your recent searches (up to eight), so you can repeat them.
- Preferences: theme, language, lite mode and whether the editor opens before
  applying a wallpaper.
- A limited offline copy of the catalogue and its collections, so the app can
  show content without a connection.
- A record of the wallpapers you saved, so the app can offer to open the
  existing copy instead of downloading it again.
- The result of the last update check and, if you accept an update, the
  downloaded installer file in temporary storage.
- Temporary copies of images you choose to share, kept in the app cache.

Images you save are written to `Pictures/DannX` in your device's gallery. They
remain there until you delete them.

If Android backup is enabled on your device, some app data — such as favourites,
collections and preferences — may be included in the backup managed by Google.
Cached images and temporary files are not included. You can control this option
in Android Settings.

To erase app data, go to Android Settings → Apps → DannX → Storage → Clear
data. The catalogue cache can also be cleared from DannX Settings; doing so does
not delete images you saved to your gallery.

## Data sent over the network

DannX uses HTTPS for all requests.

- **Wallpaper catalogue.** The app downloads the public catalogue, collection
  information and images from a backend hosted on Supabase.
- **Updates.** When you open Settings or ask the app to check for updates, it
  queries the public GitHub API for a newer version, at most once per day. The
  download starts only when you request it, and the file is verified against its
  published SHA-256 checksum before Android's installer is opened.
- **Sharing.** When you share an image, it is handed to the app you select
  through Android's share sheet. How that app uses the image is governed by its
  own privacy policy.

DannX does not send usage statistics, device identifiers or advertising
identifiers. As with any network request, the services above receive your IP
address and the basic technical information needed to respond.

## Permissions

- `INTERNET` and `ACCESS_NETWORK_STATE`: load the catalogue and images, and
  detect when the device is offline.
- `SET_WALLPAPER`: apply a wallpaper to the home screen, the lock screen or both.
- `REQUEST_INSTALL_PACKAGES`: open Android's installer when you choose to
  install an update. Android always asks for your confirmation.

Saving an image uses Android's media library and does not grant DannX access to
the photos already on your device.

## Third parties

- **Supabase** hosts the catalogue and the image storage. Data it processes is
  subject to Supabase's privacy policy.
- **GitHub** hosts the source code, the releases and the update metadata.
  Opening the repository, the releases page or this policy from the app opens
  GitHub in your browser.
- **Apps you choose** when sharing or opening a saved image receive only the file
  you select.

DannX does not sell, rent or share personal data with advertisers or data
brokers.

## Security

All network traffic uses HTTPS, and updates are checked against a published
SHA-256 checksum before installation. No method of transmission or storage is
completely secure, but DannX is designed to collect as little data as possible.

## Children

DannX is a general-purpose wallpaper app and is not directed to children under
13. The app does not knowingly collect personal information from children.

## Retention and deletion

App data remains on your device until you clear it or uninstall DannX. Temporary
files are cleared periodically and when you clear the app cache. Uninstalling the
app removes its private data; images saved to your gallery are not removed and
must be deleted with your gallery app.

## Changes

When this policy changes, the updated version will be published in this
repository with a new "Last updated" date. Material changes will be mentioned in
the release notes.

## Contact

For privacy questions, open an issue at
<https://github.com/Lxchoooo/dannx/issues>. For sensitive reports, use the
private channel described in [SECURITY.md](SECURITY.md).
