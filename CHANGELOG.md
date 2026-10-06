# Changelog

All notable changes to this project are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/). There are no tagged releases
yet; debug builds are identified by their `versionCode` (currently 51).

## [Unreleased]

### Changed
- The app is published as **GifPack** (repository `iledcolor-backpack`); application id
  `io.github.peterkocz91.gifpack` and deep-link scheme `gifpack://` (Kotlin packages are unchanged).
- Without a remembered backpack, *Connect* now scans for the first backpack advertising nearby
  instead of falling back to a built-in device address.

### Fixed
- The backpack screen no longer crashes on a fresh install (an early return inside the device
  card's `Column` corrupted the Compose slot table when no advertisement was cached yet).

### Security
- The `gifpack://backpack` deep link is no longer `BROWSABLE`, and its `auto_test` upload hook
  works only in debuggable builds.
- API keys are read from `local.properties` / environment, never from tracked files; a Giphy key
  can now be entered in Settings next to the Klipy key.
- The bundled test image is an own generated colour-bar animation.

### Added
- Public documentation: README with feature maturity and tested hardware, a docs index,
  architecture, building, testing, user guide, troubleshooting and roadmap pages,
  CONTRIBUTING, SECURITY and an MIT licence.

## [Initial feature set] — debug builds up to versionCode 51

Summary of what the app does at the point the repository was first documented.

### Added
- **Search**: Giphy GIFs and stickers, Klipy, a pixel-art mode (square Giphy pixel-art
  search), curated categories with pinned, monthly trending and recent lists, content-rating
  and aspect-ratio filters, retry on network errors.
- **Downloads**: WorkManager download to the collection with visible pending / running /
  done / error state, retry, offline handling and validation before saving.
- **Collection**: `Pictures/GifPack` library; import through the Android picker and by
  sharing a GIF to the app (`ACTION_SEND`, cold and warm start, FIFO, no double import on
  rotation); delete; 64 × 64 panel preview identical to the upload conversion.
- **Detail**: animated preview, pixel-exact 64 × 64 preview, save, share, convert, send and
  edit actions.
- **Editor**: rotation 90° / 180° / 270°, horizontal and vertical flip, fit or crop to
  square, animated 64 × 64 preview, save as a copy or send.
- **Experimental programme speed and brightness** in the editor (bytes in the upload
  payload, 0–255, default 100); effect on the panel not yet verified.
- **Scrolling-text banner**: up to 60 characters, 9 colours, 3 sizes, 3 speeds, bold;
  seamless loop capped at 300 frames; save or send. Not yet verified on the panel.
- **Video → GIF** from an MP4 URL (up to 100 MB, up to 30 frames at 64 × 64), cancellable.
- **Backpack upload** over BLE: programme payload with CRC-32C file ID, start command, data
  chunks sized to the negotiated MTU with per-chunk acknowledgement, end frame; progress,
  cancel and retry; distinct handling of "already on backpack", "not enough space" and
  "rejected". Verified with a small GIF and a 453 KB / 96-frame GIF.
- **Backpack controls**: device card from the advertisement (firmware, panel size, colours,
  features), brightness 1–10 and screen on/off (verified), status refresh, clear backpack
  contents with confirmation, upload history of the last 10 outcomes, diagnostics log and
  test image.
- **Capability gating**: clock sync only with time support, rotation / mirror only with
  rotation support, built-in programmes only when the device reports at least one.
- **Safe GIF decoder** with limits of 20 MiB, 600 frames, 4 M canvas pixels and 64 M decoded
  pixels, cancellation checks and malformed-input handling.
- **Languages**: system default, Czech or English, with English translations for every UI
  string.
- **Tests**: JVM unit tests in `app`, `core:conversion`, `core:data`, `core:storage`,
  `feature:backpack`, `feature:library` and `feature:search`; Python tests and an ADB smoke
  harness in `tools/`.

### Changed
- The upload follows the sequence observed from the owner's own device and app traffic
  (start → chunks → end); earlier experimental slot buttons, custom binary uploads and
  extra commands were removed.

### Fixed
- LZW code-width boundaries in the GIF encoder (pixel regression tests).
- Cancellation while writing an imported GIF now removes the partial MediaStore entry and
  propagates instead of being reported as a normal failure.
- Every unexpected disconnect closes the GATT client, so failed connections no longer leak
  Android's Bluetooth client slots.
