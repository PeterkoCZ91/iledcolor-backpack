# Roadmap

GifPack already covers the main path — find or make a GIF, fit it to the panel (64 × 64
backpack, or an iPixel Color panel) and get it onto the panel with a confirmed result. The remaining work is mostly **verification on
real hardware** and a few features that the protocol allows but the app does not use yet.
Items are capability-driven: a feature the backpack does not advertise stays hidden rather
than guessed.

Status words follow the [feature-maturity table](README.md#feature-maturity).

## Repository visibility

The repository was private while the work below was finished and is public again from
7 October 2026. Checked before reopening: the diff was scanned and the pushed history was swept by a hook for keys,
personal paths and device identifiers; the Actions logs contain only CI-runner paths;
`local.properties`, personal APKs, captures and local notes are excluded by `.gitignore`;
the CI artifact is built without API keys.

| Task | State |
| --- | --- |
| Video → GIF on Android | Instrumentation test and the https URL flow passed on phones; still marked experimental until the owner confirms it on the panel. |
| First-run / API-key guidance and screenshots | Done: screenshots of every tab, including More → API keys with About, are in the README and user guide. |
| Preflight of the working tree and history | Done (see above). |

## Next steps

| Order | Work | Status and definition of done |
| --- | --- | --- |
| 1 | **Import robustness on Android.** Import after the URI grant is revoked, and after the process is killed mid-import. | Implemented and unit-tested: revoked/missing sources map to clear messages, partial MediaStore items are removed (with a pending/publish check), a failed item no longer blocks the import queue, the share queue is restored from `SavedStateHandle` after process death (an unreadable URI asks the user to share again) and a repeated import after a killed process is idempotent. Done when both cases are checked on a device. |
| 2 | **First-connection resilience.** A transient GATT 133 was seen on a first connection; a manual retry succeeded. | One bounded automatic retry (status 133 / 62, only while connecting, 800 ms, cancelled by *Disconnect*) is implemented and unit-tested. Done when an occurrence is observed being retried on a device. |
| 3 | **Verify remaining backpack answers and visuals.** The "not enough space" status; physical image of edited GIFs. | Clock, status query, brightness, screen on/off and the "already on backpack" status (3) are verified. Rotation is hidden because the tested firmware does not advertise it (`funCode 0x0044`). Done when the statuses are observed and the panel output is checked by eye. |
| 4 | **Verify the new features on the panel.** Programme speed / brightness bytes and built-in programmes on a device that reports them. | The scrolling-text banner is verified (upload confirmed and it scrolls on the panel). Speed / brightness remain experimental; the tested unit reports 0 built-in programmes. |
| 5 | **Error states and accessibility.** Each screen's error state, TalkBack, large fonts and touch targets. | Code pass done on all screens (descriptions, headings, 48 dp targets, toggle/slider semantics, announced errors with a recovery action). A manual TalkBack and large-font pass on a phone is still to do. |
| 6 | **Collection name safety.** Case-insensitive name collisions, rollback of a legacy-path rename and completing the save when `importGif` is cancelled. | Implemented and unit-tested (name rules, rename rollback, non-cancellable commit). Done when checked on a device, including a pre-Android 10 phone for the legacy path. |
| 7 | **Measure capacity safely.** Find the practical upload limit step by step, watching the panel, without using "clear all" as cleanup. | Largest confirmed upload: 453 KB / 96 frames. |
| 8 | **Cancel while connecting to several panels.** | Cancelling *Send to all my panels* during connecting must leave a panel that was not connected before disconnected (otherwise restore the previous one) and write the history correctly. Not yet checked on hardware. |
| 9 | **First write after a fresh iPixel connection.** The first quick send failed once at packet 1 of 2 right after connecting; the retry succeeded and a repeat run of 8 rounds (10 transfers, fresh and reused connections) all completed. | Watch for a repeat; if it recurs add one bounded automatic retry like the one for GATT 133. |
| 10 | **Static images in quick send.** Only GIFs can be picked today. | JPG / PNG to a one-frame GIF, if the existing converter can do it without new dependencies. |

## Backlog from the October 2026 review

Collected after the five-tab rework from four independent read-only reviews (user journeys,
protocol features, release readiness, reach and hardware). Claims were checked against the
repository; evidence is given as file references. Effort: **S** about a day, **M** a few days,
**L** more. Suggested order: **A**, then B1, B2, B7 and the open hardware checks, then F1 and F2.

### Ground rules for any follow-up work

- A panel is used only after the user picked it in the app. Never scan-and-send to everything
  in range, never flash firmware or OTA, never set a panel password without a recovery design.
- Never send the commands that erase a panel: backpack clear-all (`0x02`), iPixel slot delete and
  ROM wipe (see [BLE protocol](ble-protocol.md) §10.3 and §13.5). iPixel uploads go to slot 0 only.
- Every single-GIF send goes through the one send decision (`SendTargetPlan`) and is converted to
  the chosen panel's own size; do not add a second path.
- Something counts as verified only after a check on hardware. Use `tools/panel_send_check.py`
  (counts completed transfers from the app log) and `tools/apk_smoke.py`; the label table in
  `apk_smoke.py` is compared with the app's strings by a unit test.
- Public APKs come only from CI and contain no API keys; check a build with a key scan before
  publishing and never publish a personal build.
- Repository text is English; Czech is a secondary UI language.

### A. Fewer send paths (each S)

| # | Work | Evidence and notes |
| --- | --- | --- |
| A1 | **One send action in Detail.** Merge *Send to panel* and *Send to all my panels* into *Send to panel…* that opens the existing *Where to send?* sheet (*All my panels* stays inside it). | `DetailScreen.kt` ~l.260–264 has two send buttons plus *Edit*; `SendTargetSheet.kt`. |
| A2 | **Same for Scrolling text:** *Send* and *Send to all my panels* become one action. | `TextBannerScreen.kt` ~l.243–269. |
| A3 | **After Video → GIF offer *Send to panel*** (and *Edit*); today the result screen only offers *To collection* and *Convert another*. | `ConvertScreen.kt` ~l.333–364; needs a callback in `BatohApp.kt`. |
| A4 | **Shared GIF from another app goes straight to the send step**, or to Detail with one main action; keep the import queue. The share target accepts only `image/gif`. | `BatohApp.kt` incoming-GIF effect, `IncomingImportController.kt`, manifest. |
| A5 | **JPG / PNG in *From my phone*** as a one-frame GIF (item 10 above). | Check whether the converter can do it without new dependencies. |

### B. Reliable sending from every screen (each M unless noted)

| # | Work | Evidence and notes |
| --- | --- | --- |
| B1 | **Shared send preflight:** Bluetooth on, permission and the enable prompt before every send and before the chooser. Only the Panel screen enables Bluetooth today; the text banner only asks for permission and nothing in Search or Detail checks it. | `BackpackScreen.kt` ~l.196, 376–379; `TextBannerScreen.kt` ~l.100–118. |
| B2 | **Remember the last target.** With several panels offer *Send to <name>* as the main action and *Another panel…* second; show the target name and range state in Detail and Search. | `SendTargetSheet.kt`, my-panels store. |
| B3 | **First-run guide.** There is no onboarding in the code. A Home card *Connect your first panel*: permission, Bluetooth, scan, pick, saved to *My panels*. | `HomeScreen.kt`, `BackpackScreen.kt` / view model. |
| B4 | **Failure everywhere:** a message with *Try again* and *Choose another panel*, and an offer of another panel when the panel is out of range. Today the detail is shown only on the Panel screen. | `BackpackTransferManager.kt`, `BatohApp.kt`. |
| B5 | **Text banner:** choose the target panel while composing (preview in the right aspect ratio) and let the user enter a size for an unknown type instead of only showing an error. | `TextBanner*.kt`, `TextBannerRenderer.kt`. |
| B6 | **Panel tab is one long scroll** (nearby, my panels, display, history, diagnostics); consider sections. | `BackpackScreen.kt`. |
| B7 | **The upload card shows "Done — image is on the panel" twice** (S). | `BackpackScreen.kt` upload card. |
| B8 | **Open hardware checks:** cancelling *Send to all my panels* while connecting (item 8) and the one failed first write after a fresh iPixel connection (item 9). | See the table above. |

### C. Features the protocols allow (verify on hardware first)

| # | Work | Certainty, effort |
| --- | --- | --- |
| C1 | **Playlist limits.** *Send as sequence* exists for the backpack; the number of programmes and the capacity above 453 KB are unknown, and the "not enough space" status has not been seen. | Protocol verified, limits unknown. S, hardware. |
| C2 | **iPixel clock:** quick access from Home and clock as default content. | Verified on hardware ([BLE protocol](ble-protocol.md) §13.4). S. |
| C3 | **Brightness presets and a night mode** (iPixel 0–100 %, backpack 1–10); automatic dimming by time is phone-side logic. | Commands verified. S–M. |
| C4 | **Saved panel profiles** (brightness, rotation, clock, last content). iPixel has no state read, so restoring means sending the saved values again. | Settings verified. M. |
| C5 | **Text banner direction and speed** in the renderer (no protocol change); native text effects only after tests (the effect byte was ignored for GIF items). | Renderer S; native M–L. |
| C6 | **Faster upload** with several chunks in flight (community code uses 32). Window 4 aborted on a repeated ACK earlier, so measure and keep a stop-and-wait fallback. | M, hardware. |
| C7 | Built-in programmes only on a unit that reports a count above 0; the password only as a read-only notice. | Low value, high risk for the password. |
| C8 | A pixel drawing editor is UI only and has no protocol reason. | L, low priority. |

### D. Faster ways to the panel

| # | Work | Evidence and notes |
| --- | --- | --- |
| D1 | **Launcher shortcuts, a Quick Settings tile and a widget** *Send to panel*. The manifest has only the share target and the `gifpack://backpack` link; no `shortcuts.xml`, tile service or widget exists. | S–M. |
| D2 | **Share target for video and `image/*`** (feeds Video → GIF and A5). | S. |
| D3 | **Intent API for Tasker / MacroDroid** with an explicit saved panel, and scheduled sends. Reliability depends on the phone being in BLE range. | M. |
| D4 | **Home Assistant / MQTT:** point to the existing community integration instead of building a bridge. | Low priority. |

### E. More hardware

- **Verify the remaining iPixel sizes.** The code table lists 20 device types `0x80`–`0x93` (for
  example 64 × 64, 32 × 16, 96 × 16, 128 × 32, up to 448 × 32); only `0x81` (32 × 32) and `0x83`
  (64 × 16) are marked verified (`IPixelProtocol.kt`). Needs testers with the units, little code.
- **Older iledcolor units** (a 16 × 32 panel, mono panels with a column-wise 1 bpp format, a
  different frame generation): see the research notes; needs units, M.

### F. Quality and release (verified against the repository)

| # | Work | Evidence |
| --- | --- | --- |
| F1 | **Signed release:** create the keystore, back it up, add the five secrets and tag the first signed release. The workflow exists. | [releasing](releasing.md); needs the owner. S. |
| F2 | **CI test list is incomplete.** `ci.yml` and `release.yml` run a module list that lacks `:core:domain` (2 test files), `:core:network` (1), `:feature:home` (2) and `:feature:convert` (1). `feature:detail`, `core:common` and `core:ui` have no tests. | `ci.yml` l.26–30, `release.yml` l.47–51. S. |
| F3 | **Dependabot and dependency review.** No `dependabot.yml`. Versions: AGP 8.2.0, Kotlin 1.9.22, Compose BOM 2024.02.02, Hilt 2.50, Room 2.6.1, media3 1.3.0, compileSdk and targetSdk 34 (Play will want a newer target). | `gradle/libs.versions.toml`. S for the bot, M for upgrades. |
| F4 | **Release shrinking.** `isMinifyEnabled = false` and the referenced `proguard-rules.pro` does not exist. Enable R8 and resource shrinking, check Moshi / Hilt / Room on a device, and build `assembleRelease` in CI for pull requests. | `app/build.gradle.kts` l.52–55. M. |
| F5 | **Manifest housekeeping:** remove `requestLegacyExternalStorage`; `jvmTarget` 1.8 to 17. Backup rules already exclude the API-key preferences. | `AndroidManifest.xml`, `data_extraction_rules.xml`. S. |
| F6 | **UI tests.** `androidTest` holds only `VideoToGifInstrumentedTest`; UI flows are covered by the ADB smoke script alone. Add Compose tests for the key screens and an optional emulator job. | M. |
| F7 | **BLE state machine tests** (fake GATT: disconnect mid-transfer, permission revoked, `SecurityException`). `BluetoothLeManager.kt` (~1250 lines), `BackpackTransferManager.kt` (~1100) and `BackpackScreen.kt` (~880) are large and worth splitting. | M–L. |
| F8 | **Accessibility:** about 31 `contentDescription = null` to review and a manual TalkBack pass. | S–M. |
| F9 | **Store metadata:** rationale for `READ_MEDIA_IMAGES` and `BLUETOOTH_SCAN` (`neverForLocation`) in [privacy](privacy.md), the data-safety form, F-Droid metadata. | S. |
| F10 | **Performance** (startup, GIF decode, large collections) is not measured; a macrobenchmark and baseline profile would show it. | L. |

## Protocol features not yet used

- **Clock sync decision.** The tested backpack does not advertise time support, and the
  app now skips the command there. Confirm the behaviour on a device that does.
- **Backpack password** (`funCode` bit `0x0040`). Query, verify and set are known in outline.
  Must be designed carefully — a mistake can lock the user out of their own device.
- **Effects** for uploaded programmes: tested on the panel and found to be ignored for GIF items (codes 0, 1, 2, 6; see [device capabilities](device-capabilities.md#73-programme-effect-byte-on-a-gif-item)), so there is nothing to add to the editor.
- **Several programmes in turn.** Confirmed on the panel with the diagnostic *Playlist test*: two programmes sent with `0x03` headers and a closing `0x08` alternate (see [BLE protocol §10.13](ble-protocol.md)). A user-facing *Send as playlist* action (pick several GIFs in the collection, send each as its own programme) is the next step, as an alternative to joining the frames into one GIF.

## Library and UX ideas

Not known bugs or blockers:

- An overview of edited copies in the collection (search, sort, rename and a count / size summary are done).
- Search no longer needs the trending request while the category list is shown on an empty query; skip it to save traffic.
- Default names of saved GIFs still start with `batoh_`; a neutral `gif_` prefix would suit all panel types.
- A manual TalkBack pass over the new tabs and the More screen (part of item 5).
- Category filtering, if pinned / trending / recent prove insufficient in daily use.
- Continue uploads while the phone is locked — needs a foreground service with a visible
  notification; only if users need it.

## Before a public release

Not required for local development or debug testing.

**Prepared:**

- **Privacy notes.** [PRIVACY.md](../PRIVACY.md) is a plain-language notice usable as a
  store privacy policy; [privacy](privacy.md) lists every request (Giphy, Klipy, media URLs,
  user video URLs), local store, permission and a data-safety draft. Audit result: no
  analytics, crash reporting or own backend.
- **Release signing.** The release build is signed only when the `GIFPACK_KEYSTORE_*` /
  `GIFPACK_KEY_*` environment variables are set and stays unsigned otherwise; the
  `Release` workflow builds, tests and publishes a signed, keyless APK for `v*` tags only when
  the repository secrets exist. See [releasing](releasing.md).

**Owner decisions and remaining work:**

- Generate the release keystore, back it up and add the five repository secrets
  ([releasing](releasing.md#one-time-setup-repository-owner)); then tag the first release.
- Choose the distribution channel (GitHub Releases, F-Droid, Google Play) and fill in its
  data-safety form from the draft; confirm the privacy notice's contact route.
- Target-API review and the channel's other requirements; decide on code shrinking.

## Done recently

- **Pre-release v66** (CI debug build without API keys, key-scan checked): iPixel Color panels, the five-tab UI, one *Send a GIF* button with *Where to send?*, a send button on search results, simpler duplicate cleanup and reviewed Czech and English texts.
- **Navigation rebuilt into five tabs (Home, Search, Panel, Collection, More):** Home is a dashboard (panel status, search bar, quick actions, recent GIFs); categories live inside Search when the query is empty; settings, API keys and About are inline on More instead of dialogs. All screens were reviewed for copy, plurals (Czech one/few/many/other), 48 dp targets, large fonts and system navigation-bar insets; generic texts say "panel" and show the panel's real size.
- **Quick send:** one **Send a GIF** button on the Panel tab (*From my phone* / *From my collection*), a **Send to panel** button on Search results, and a *Where to send?* sheet that appears only when there is not exactly one candidate panel; the GIF is always converted to the chosen panel's own size. Checked on hardware: backpack 64 × 64, iPixel Color 32 × 32 and 64 × 16, *Send to all my panels* (2 of 2), brightness and the scrolling-text banner. `tools/panel_send_check.py` repeats such sends and counts completed transfers from the app log.
- **Duplicate deletion simplified:** one *Find duplicates* action and one confirmation that keeps the first copy of each group.
- **Smoke harness:** the labels it taps are kept in one table that a unit test compares with the app's string resources; the locale test also checks plurals, placeholders and house style.
- **Localized error messages (v62):** search, categories, downloads, the GIF editor, the text banner and backpack commands no longer fall back to English or raw exception text; search and categories tell a missing API key, no connection and a failing service apart. A failed share no longer makes the next language switch jump to the collection. Smoke harness with `--fixtures --import --locale-sweep` passes on the test phone; the offline search message was checked by hand.
- **Downloadable APK:** pre-release `v66` (earlier `v65`, `v64`, `v63`, `v62`, `v61`) on the releases page, a CI debug build without API keys (verified keyless); README, docs and changelog link to it. Still no signed release: that needs the owner's keystore and the five repository secrets.
- **Send as a sequence:** several GIFs from the collection, each sent as its own programme; the backpack plays them in turn (two programmes, 181 KB, checked on the panel). Maximum number of programmes and total capacity still unknown.
- Join GIFs into one programme (multi-select in the collection, order, pause, preview, size warning): a 33-frame / 95 KB result was saved and confirmed by the backpack on the second phone. Panel output not yet checked by eye.
- Collection: search (case- and accent-insensitive), remembered sort order, rename of own GIFs, count / size summary.
- Privacy notice, optional release signing from environment variables and a tag-triggered release workflow that publishes nothing without the owner's secrets.
- Scrolling-text banner, verified on the panel.
- Library items whose file is broken show the reason and offer *Remove*.
- Screenshots in the README; the repository was published with a clean history and CI.
- First connection without a remembered backpack finds the first one advertising nearby.
- "Already on backpack" status verified; the home status card shows the backpack's connection state and name.
- Upload failures show a typed reason with a hint, in the upload card and in the history.
- Clean install, first connection without a stored address and a test-image upload confirmed by the backpack, checked on a second phone (2026-10-07).
- Video → GIF instrumentation test passed on a device; the URL (https) → download → preview → save-to-collection flow was verified by hand on a second phone (still experimental until the owner confirms).
- Review fixes: import queue no longer stalls, MediaStore pending/publish check, GATT race, `CancellationException` handled in `fetchGifs`, MTU falls back to 23, name limit counted in UTF-8 bytes, rename survives process death, idempotent import after process kill, legacy permission dialog no longer loops, typed localized Video → GIF errors.
- GIF cache: key includes the filter (composite key), ordering kept, 7-day TTL; the category list is stored in the database (migration 7 → 8) and still shows offline after a restart (checked on a phone in airplane mode).
- Read-only *Backpack info* diagnostic action (state `0x10`, built-in count `0x0D`, decoded advertisement): the state answer is a 16-byte frame whose `p8` equals the firmware version; see [device capabilities](device-capabilities.md#72-raw-answers-read-with-the-backpack-info-diagnostic-action).
- The smoke harness passes end to end on a phone again (home, import, picker, both languages); the repository text, comments and docs are English, with Czech kept as a secondary UI language.
- New unit tests for the domain module, NeuQuant, `GifCachePolicy`, `ConvertError`, `MtuNegotiation` and `PendingRenameStore` (see test run for counts).

## What is still unknown about the backpack

- Total storage capacity and the largest safe programme size. The app's 20 MiB / 600-frame
  limits are decoder limits, not device capacity.
- The status query reports display settings only — not free memory.
- Behaviour of other models and firmware versions; only one unit has been tested.
