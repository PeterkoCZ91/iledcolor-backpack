# Privacy notice — GifPack

*Applies to the GifPack Android app (application id `io.github.peterkocz91.gifpack`) as
published in this repository. Last reviewed: October 2026.*

GifPack is an open-source app that sends animated GIFs to an LED backpack over Bluetooth.
It has **no account, no analytics, no advertising and no crash reporting**, and the
project runs **no server of its own**. The developer receives no data from the app.

## What leaves your phone

The app connects to the internet only when you use an online feature:

| When you… | The app contacts | What that service receives |
| --- | --- | --- |
| Search or browse GIFs / stickers from **Giphy** (also the pixel-art mode and categories) | Giphy (`api.giphy.com`) and its image servers | Your search text, the API key, content-rating and paging settings, and — as with any internet request — your IP address and basic device / network information. |
| Search or browse GIFs from **Klipy** | Klipy (`api.klipy.com`) and its image servers | Your search text, the API key, filter and paging settings, your IP address. |
| See thumbnails or **download** a GIF to your collection | The image server named in the search result (Giphy or Klipy) | A request for that file and your IP address. |
| **Convert a video** from a URL you type in | Whatever server that URL points to | A request for that file and your IP address. |

Giphy and Klipy handle this data under their own privacy policies and terms. GifPack does
not add any identifier of you or your device to these requests. If you do not use online
search, downloads or video conversion, the app makes no internet connections.

The API keys belong to whoever built the app or to you (if you typed them in Settings). They
identify the app to the GIF service, not you personally.

## What stays on your phone

- **Your GIF collection.** Downloaded, imported, edited and generated GIFs are saved to the
  shared `Pictures/GifPack` folder, where your gallery and other apps with photo access can
  see them. Uninstalling the app does not delete them.
- **Settings and history** in the app's private storage: theme, grid size, language, your
  interests, API keys you typed in Settings, recent / pinned categories, cached search
  results (including the search text), the last 10 uploads (GIF title, time and result), and
  the remembered backpack (its Bluetooth address, name and advertised capabilities).
- **Temporary files** (thumbnails, video being converted) in the app's cache.

If backup is enabled on your phone, Android may include the app's private storage (upload
history, the remembered backpack) in your device backup. **API keys typed in Settings are
excluded from backups and device transfers**, and so is the `Pictures/GifPack` folder.

## Bluetooth

GifPack uses Bluetooth Low Energy only to find and talk to your backpack. It sends the GIFs
you choose and display commands (brightness, screen on/off, status) directly to the
backpack — nothing goes through the internet. Scanning shows nearby Bluetooth devices
inside the app; on Android 12 and newer the scan is declared as *not used for location*.
On Android 11 and older, Android requires the location permission for any Bluetooth scan;
GifPack does not read your location.

The diagnostics log on the backpack screen is kept in memory only. It is copied to the
clipboard only when you tap *Copy*, and it may contain your backpack's Bluetooth address.

## Permissions

| Permission | Why |
| --- | --- |
| Internet, network state | Online search, downloads and video conversion. |
| Nearby devices (Bluetooth scan / connect); location on Android 11 and older | Finding and connecting to the backpack. |
| Photos and videos (Android 13+), storage (Android 12 and older) | Showing and saving the GIFs in `Pictures/GifPack`. |

## Your choices

- Use the app offline: the library, import, editor, scrolling text and backpack upload work
  without internet.
- Clear the app's data or uninstall it to remove settings, keys, history and cache.
  Delete `Pictures/GifPack` in your gallery or file manager to remove saved GIFs.
- Remove a key in **Settings → API keys** at any time.

## Children

The app is not directed at children. Online searches use the services' content-rating
filters (adjustable in the search filters), but results come from third parties and may not
always be suitable.

## Changes and contact

Changes to this notice are tracked in the repository history. Questions or concerns:
open an issue at https://github.com/PeterkoCZ91/iledcolor-backpack/issues (for security
issues follow [SECURITY.md](SECURITY.md)). A technical description of every data flow is in
[docs/privacy.md](docs/privacy.md).
