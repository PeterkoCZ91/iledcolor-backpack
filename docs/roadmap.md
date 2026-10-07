# Roadmap

GifPack already covers the main path — find or make a GIF, fit it to 64 × 64 and get it
onto the backpack with a confirmed result. The remaining work is mostly **verification on
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
| First-run / API-key guidance and screenshots | Done: About and Settings → API keys screenshots are in the README and user guide. |
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

## Protocol features not yet used

- **Clock sync decision.** The tested backpack does not advertise time support, and the
  app now skips the command there. Confirm the behaviour on a device that does.
- **Backpack password** (`funCode` bit `0x0040`). Query, verify and set are known in outline.
  Must be designed carefully — a mistake can lock the user out of their own device.
- **Effects** for uploaded programmes (an effect byte exists next to speed and brightness).

## Library and UX ideas

Not known bugs or blockers:

- An overview of edited copies in the collection (search, sort, rename and a count / size summary are done).
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

- Join GIFs into one programme (multi-select in the collection, order, pause, preview, size warning): a 33-frame / 95 KB result was saved and confirmed by the backpack on the second phone. Panel output not yet checked by eye.
- Collection: search (case- and accent-insensitive), remembered sort order, rename of own GIFs, count / size summary.
- Privacy notice, optional release signing from environment variables and a tag-triggered release workflow that publishes nothing without the owner's secrets.
- Scrolling-text banner, verified on the panel.
- Library items whose file is broken show the reason and offer *Remove*.
- Screenshots in the README; the repository was published with a clean history and CI.
- First connection without a remembered backpack finds the first one advertising nearby.
- "Already on backpack" status verified; the home tile shows the backpack's connection state and name.
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
