# Privacy — technical data flows

This page is the technical counterpart of the plain-language [privacy notice](../PRIVACY.md).
It lists every network request, every place the app stores data and every permission, as
found in the source code. Re-check it whenever networking, storage or the manifest changes.

**Summary:** no analytics, crash reporting, advertising or tracking SDKs; no backend of the
project's own; no user identifiers added to requests. The only network traffic is to the GIF
services (Giphy, Klipy), the media URLs they return, and a video URL the user types.

## Network requests

All HTTP traffic goes through one shared OkHttp client (`core/network` `NetworkModule`).
`network_security_config.xml` forbids cleartext (HTTP) traffic; release builds trust only
system CAs, debug builds additionally trust user-installed CAs.

| Trigger | Host / endpoint | Code | Data sent | Response use / storage |
| --- | --- | --- | --- | --- |
| Giphy search (GIFs) | `api.giphy.com/v1/gifs/search` | `GiphyApi.searchGifs`, `GiphyRepositoryImpl` | Query parameters: `api_key`, `q` (search text), `limit`, `offset`, `rating`, `lang` | Results cached in Room (`cached_gifs`) |
| Giphy search (stickers) | `api.giphy.com/v1/stickers/search` | `GiphyApi.searchStickers` | Same as above | Result list |
| Giphy trending GIFs / stickers | `api.giphy.com/v1/{gifs,stickers}/trending` | `GiphyApi.getTrending*` | `api_key`, `limit`, `offset`, `rating` | Result list |
| Giphy categories | `api.giphy.com/v1/gifs/categories` | `GiphyApi.getCategories` | `api_key` | Category list |
| Pixel-art mode | Giphy search (above) | `LospecRepositoryImpl` | Search text prefixed with `pixel art` | Despite the class name, no request goes to Lospec; `LospecApi` is defined but never called |
| Klipy search / featured | `api.klipy.com/v2/search`, `/v2/featured` | `KlipyApi`, `KlipyRepositoryImpl` | `key`, `q` (search only), `limit`, `pos`, `contentfilter`, `media_filter` | Results cached in Room (`cached_gifs`, keyed by `klipy_<query>`) |
| Thumbnails and previews | Media URLs from the Giphy / Klipy result | Coil `ImageLoader` (`BatohApplication`) | Plain GET of the URL | Coil memory / disk cache in the app's cache directory |
| Download to collection | Original GIF URL from the result | `DownloadWorker` (WorkManager) | Plain GET of the URL | Validated (`SafeGifDecoder`, ≤ 20 MiB), saved to `Pictures/GifPack` |
| Video → GIF | URL typed by the user | `VideoToGifConverter.downloadToFile` | Plain GET of the URL | Temporary MP4 in the cache dir (deleted after conversion), result GIF saved to the collection |

Every request also exposes what HTTP always exposes: the phone's IP address, TLS metadata
and OkHttp's default `User-Agent` (`okhttp/<version>`). No cookies, advertising IDs, device
IDs or account data are added.

**Debug builds only:** an `HttpLoggingInterceptor` at level `BODY` writes full requests to
logcat — including the API key in the query string. It is disabled in release builds
(`BuildConfig.DEBUG`). Logcat is readable only by the system and by `adb`.

## API keys

| Source | Where it lives | Notes |
| --- | --- | --- |
| Build time (`GIPHY_API_KEY`, `KLIPY_API_KEY` from `local.properties`, Gradle property or env) | `BuildConfig` of `core/data`, inside the APK | Extractable from the APK. CI and release builds have none — do not set these variables in the release workflow. |
| Settings → API keys | SharedPreferences `batoh_ui_preferences` (`giphy_api_key`, `klipy_api_key`), plain text in app-private storage | Takes precedence over the build-time key. Included in Android backup (see below). |

Keys are sent only to their own service, as a query parameter over HTTPS.

## Local storage

| Store | Contents | Location |
| --- | --- | --- |
| MediaStore `Pictures/GifPack` | Downloaded, imported, edited, generated GIFs | Shared storage — visible to the gallery and apps with media access; survives uninstall; excluded from app backup |
| SharedPreferences `batoh_ui_preferences` | Theme, grid columns, user interests, API keys typed in Settings | App-private |
| SharedPreferences `backpack_upload_history` | Last 10 uploads: title, time, outcome, failure reason code | App-private |
| SharedPreferences `backpack_connection` | Last device address; per address: advertised name and raw advertisement bytes | App-private |
| Room database | Search history, cached Giphy / Klipy results (with the query), category pins / last use | App-private |
| WorkManager database | Pending download jobs (URL, title) | App-private |
| Per-app locale | Chosen UI language (AndroidX `autoStoreLocales`) | App-private |
| Cache directory | Coil image cache, temporary MP4 during conversion | App-private, clearable |
| In memory only | BLE diagnostics log (may include the backpack's address and name); copied to the clipboard only on *Copy* | — |

**Backup:** `allowBackup="true"`; `backup_rules.xml` and `data_extraction_rules.xml` include
`root`, `database` and `sharedpref`, exclude `external`, and **exclude
`batoh_ui_preferences.xml`** — the file holding API keys typed in Settings (together with UI
preferences such as theme and interests, which are therefore not restored either). Upload history
and the remembered backpack are still backed up.

**Logcat:** names and Bluetooth addresses of *other* nearby devices seen during a scan are
logged only in debuggable builds. Release builds still log the backpack's own connection events
and advertisement (`Log.d` / `Log.i`); logcat is not accessible to other apps on modern Android.

## Bluetooth data

BLE traffic goes only between the phone and the backpack: GIF payloads chosen by the user,
display commands (brightness, screen, rotation when supported, clock when supported),
status and capability queries. See the [BLE protocol](ble-protocol.md). Nothing from the
backpack is sent to the internet. Uploaded programmes stay on the backpack until cleared.

## Outgoing shares

*Share* in the detail and library screens sends the selected GIF to an app the user picks via
the system chooser (`ACTION_SEND`, `FileProvider` with a temporary read grant). Incoming
shares (`ACTION_SEND` with `image/gif`) are copied into the collection.

## Permissions

| Permission | Max SDK | Declared in | Why |
| --- | --- | --- | --- |
| `INTERNET` | — | `app`, `core/network` | GIF search, downloads, video URL |
| `ACCESS_NETWORK_STATE` | — | `app` | Download jobs wait for a connection (WorkManager `NetworkType.CONNECTED`) |
| `BLUETOOTH`, `BLUETOOTH_ADMIN` | 30 | `feature/backpack` | BLE scan / connect on Android ≤ 11 |
| `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION` | 30 | `feature/backpack` | Required by Android ≤ 11 for BLE scans; location is never read |
| `BLUETOOTH_SCAN` (`neverForLocation`) | — | `feature/backpack` | Finding the backpack on Android 12+ |
| `BLUETOOTH_CONNECT` | — | `feature/backpack` | Connecting and writing to the backpack on Android 12+ |
| `READ_MEDIA_IMAGES` | — | `app`, `core/storage` | Listing GIFs in `Pictures/GifPack` on Android 13+ |
| `READ_EXTERNAL_STORAGE` | 32 | `app`, `core/storage` | Listing the collection on Android ≤ 12 |
| `WRITE_EXTERNAL_STORAGE` | 29 | `app`, `core/storage` | Saving to `Pictures/GifPack` on Android ≤ 10 |

No foreground service, no background location, no contacts, camera or microphone.

## Third-party libraries with network or data access

OkHttp / Retrofit / Moshi (HTTP and JSON), Coil (image loading and cache), WorkManager
(download jobs), Room (local database). None of them sends telemetry. The `usb-serial`
dependency in `core/data` makes no network requests. There is no Firebase, Google Play
Services, Crashlytics, Sentry or similar dependency (checked in `gradle/libs.versions.toml`
and all module build files).

## Store data-safety draft

For a Play Store "Data safety" form, based on the flows above (owner to confirm):

- **Data collected by the developer:** none — no backend.
- **Data shared with third parties:** search text and IP address are sent to Giphy / Klipy
  when the user searches; files are requested from their CDNs or a user-entered URL.
  Purpose: app functionality. Not used for advertising by the app.
- **Encryption in transit:** yes (HTTPS only, cleartext disabled).
- **Deletion:** all app data is local; clearing data or uninstalling removes it, except GIFs
  in `Pictures/GifPack`.
