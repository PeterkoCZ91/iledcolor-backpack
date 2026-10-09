# Changelog

All notable changes to this project are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/). There are no tagged releases
yet; debug builds are identified by their `versionCode` (currently 63).

## [Unreleased]

### Added
- **Downloadable APK:** pre-release `v63` (the UI refresh, see *Changed*) follows `v62` (localized error messages) and the first pre-release, `v61`; all are on the [releases page](https://github.com/PeterkoCZ91/iledcolor-backpack/releases): a debug build from CI without API keys (checked by searching it for the real keys), with its SHA-256 in the notes. The README and docs link to it.
- CI uploads a debug APK without API keys as a downloadable artifact for every run.
- The home *Backpack* tile shows the connection state and the device name.
- Upload failures carry a typed reason (not connected, connection lost, header / chunk / end
  rejected or timed out, insufficient space, MTU too small, invalid GIF, cancelled…) with a
  "what to do" hint, shown on the upload card and stored in the history.
- Accessibility pass: content descriptions, section headings, 48 dp touch targets, semantics for
  toggles, sliders and colour chips, announced error states with a recovery action.

- Privacy notice (`PRIVACY.md`) and a technical data-flow page (`docs/privacy.md`) covering
  every network request, local store and permission.
- Optional release signing: `assembleRelease` is signed when the `GIFPACK_KEYSTORE_PATH`,
  `GIFPACK_KEYSTORE_PASSWORD`, `GIFPACK_KEY_ALIAS` and `GIFPACK_KEY_PASSWORD` environment
  variables are set, unsigned otherwise. A tag-triggered `Release` workflow (`v*`) runs the
  unit tests and attaches a signed APK without API keys to a GitHub Release — only when the
  signing secrets are configured; otherwise it skips with a notice. Owner setup in
  `docs/releasing.md`.

- Unit tests for the domain module, NeuQuant, `GifCachePolicy`, `ConvertError`, `MtuNegotiation` and `PendingRenameStore` (see test run for counts).
- About-screen screenshot (`docs/images/about.png`) and Settings → API keys screenshot (`docs/images/api-keys.png`).
- **Send as a sequence:** on the join screen, a second mode sends every selected GIF as its own programme (`0x03` playlist headers and a closing `0x08`), so the backpack plays them in turn. Verified on the backpack with two programmes.
- **Join GIFs:** long-press a GIF in the collection to select several, order them, set the pause
  between them, Fit / Crop and an optional frame delay, preview the result, then save it to the
  collection or send it to the backpack. Sizes above 96 frames / 453 KB (the largest upload
  confirmed on hardware) show a warning; above 600 frames / 20 MiB the result is blocked. The
  previous long-press menu (share, rename, delete) moved to the ⋮ button on each tile.
- Read-only **Backpack info** diagnostic action (state query `0x10`, built-in count `0x0D`,
  decoded advertisement and the already received RCSP answer, printed raw). It sends no write command.
- Product categories are stored in the database, so the list also shows offline after a restart.
- Request logging in debug builds masks API keys and logs request lines only.
- Experimental *Playlist test* and read-only *Password status* diagnostics. The playlist test confirmed that several programmes (`0x03` headers + `0x08`) alternate on the panel; the password query answered "no password set".
- Experimental *Effect test* diagnostic (buttons 0, 1, 2, 3, 5, 6, 7) that uploads a labelled test GIF with a chosen effect byte. On the tested backpack the byte has no visible effect on GIF items; results in `docs/device-capabilities.md`.

### Changed
- Diagnostics: the four action buttons are laid out in two rows so their labels fit on narrow screens.
- UI consistency: the collection has one name ("Moje sbírka" / "My Collection") and one star icon in the
  bottom bar, the Text banner and GIF editor use the same back arrow as the other screens, the Giphy / Klipy
  source chips show a check mark when selected, and the Text banner preview is smaller so the colour
  controls are visible without scrolling.
- Home: a backpack status card (connected / not connected, device name) replaces the backpack tile, and a
  "Recently in collection" row opens the GIF detail. Backpack: *Clear backpack contents* moved into a collapsed
  *Advanced* section and *Refresh display state* is an icon. Collection: the hint text is behind an info icon and
  the import action is shortened to "Import". Categories: a missing preview no longer happens when the top Giphy hit is not square (previews now pick the first of ten candidates that passes the filter), and the heart has a round scrim so it stays visible on light previews.
- The app is published as **GifPack** (repository `iledcolor-backpack`); application id
  `io.github.peterkocz91.gifpack` and deep-link scheme `gifpack://` (Kotlin packages are unchanged).
- Without a remembered backpack, *Connect* now scans for the first backpack advertising nearby
  instead of falling back to a built-in device address.

### Fixed
- Error messages no longer fall back to English (or raw exception text) in the Czech UI:
  search, categories, downloads, the GIF editor, the text banner and backpack commands show
  localized texts (cs + en); search and categories tell a missing API key, no connection and a
  failing service apart. Technical details go to the log and the diagnostics log instead.
- After a shared GIF failed to import, the next activity recreation (e.g. switching the app
  language) jumped to *My Collection* again: the failure reused the already handled navigation
  event. The share message is also kept as a string resource, so it follows a language switch.
- Review fixes: the import queue no longer stalls on a failed item; MediaStore pending/publish is checked; a GATT race was removed; `CancellationException` is no longer swallowed in `fetchGifs`; the MTU falls back to 23; the name limit is counted in UTF-8 bytes; a rename survives process death; import after a killed process is idempotent; the legacy permission dialog no longer loops; Video → GIF errors are typed and localized.
- GIF cache: the key includes the filter (composite key), ordering is kept, entries expire after 7 days and categories are served from memory when offline.
- The backpack screen no longer crashes on a fresh install (an early return inside the device
  card's `Column` corrupted the Compose slot table when no advertisement was cached yet).

### Security
- API keys typed in Settings are excluded from Android backup and device transfer; the unused
  `READ_MEDIA_VIDEO` permission was removed; nearby devices' scan results are logged only in
  debuggable builds.
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
