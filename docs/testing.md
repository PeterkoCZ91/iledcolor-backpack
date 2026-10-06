# Testing

Three layers, from cheapest to most invasive:

1. **JVM unit tests** per Gradle module — no phone, no backpack.
2. **Python tests** for the smoke harness and the translations — no phone.
3. **ADB smoke harness** — drives the real app on a phone; some flags write to a real
   backpack.

## Unit tests

Run from `BatohManager/` with the JVM settings from [building](building.md):

```bash
./gradlew -Dorg.gradle.java.home=<jdk17-path> \
  '-Dorg.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8 -XX:TieredStopAtLevel=1' \
  --max-workers=2 \
  :app:testDebugUnitTest \
  :core:conversion:testDebugUnitTest :core:data:testDebugUnitTest :core:storage:testDebugUnitTest \
  :feature:backpack:testDebugUnitTest :feature:library:testDebugUnitTest :feature:search:testDebugUnitTest
```

| Module | Test classes | What they cover |
| --- | --- | --- |
| `app` | `IncomingImportQueueTest` | Share intent replay on rotation, FIFO of further shares, no replay of a finished import after restore. |
| `core:conversion` | `GifEditorProcessorTest`, `LzwEncoderTest`, `TextBannerLayoutTest`, `HttpAwaitTest` | Rotation / flip / fit / crop pixel mapping, LZW code-width boundaries (pixel regressions), banner frame plan and seamless loop, cancellable HTTP. |
| `core:data` | `BackpackProtocolTest`, `BackpackAdvertisementTest`, `BuiltInProgramTest`, `PendingCommandResponseTest`, `UploadHistoryTest` | Frame building and checksums against captured frames, payload layout and CRC-32C, advertisement parsing, built-in count and type-5 payload, response matching, history encoding. |
| `core:storage` | `GifFileInspectorTest`, `GifImportReaderTest`, `CancellableStreamCopyTest` | Damaged-file classification, bounded / cancellable import reads, exact block copy and cancellation mid-write. |
| `feature:backpack` | `BackpackUploadProtocolTest`, `EditorPlaybackOptionsTest`, `SingleOperationOwnerTest` | Cmd 06 status classification, playback byte offsets and defaults, one-operation-at-a-time ownership. |
| `feature:library` | `BackpackPreviewScalerTest`, `PreviewDiagnosisTest` | 64 × 64 sampling identical to the converter, preview failure diagnosis. |
| `feature:search` | `SearchDownloadsTest`, `SearchRequestOwnerTest` | Download state mapping, stale paging and request ownership. |

`core:common`, `core:domain`, `core:network`, `core:ui`, `feature:home`, `feature:detail`
and `feature:convert` have no unit tests yet. There are no instrumented (`androidTest`)
tests; on-device behaviour is covered by the smoke harness below. Android lint runs with
`:app:lintDebug`.

## Python tests

The smoke harness and the translation check use only the Python 3 standard library. From
the repository root:

```bash
python3 -m unittest discover -s tools -p 'test_*.py' -v
```

| File | Covers |
| --- | --- |
| `tools/test_apk_smoke.py` | Tap-target resolution (enabled, app-owned clickable parent), detection of progress, panel state, command and import errors in Czech and English UI, reading the selected app language, cleanup that deletes and verifies only registered fixture URIs, `--fixtures` required for data-changing flags, failure reports that do not persist exception contents. |
| `tools/test_android_locales.py` | Every default (Czech) string resource has an English translation; the locale config lists Czech and English. |

## ADB smoke harness

`tools/apk_smoke.py` drives the installed app over `adb` and UIAutomator: each step reads
a fresh UI dump, checks the package, finds a control by its text or description and taps
its real coordinates, with bounded waits. It never uninstalls the app, never clears its
data and **never confirms Clear backpack contents**. The full guide (in Czech) is
[`tools/APK_SMOKE.md`](../tools/APK_SMOKE.md).

Prerequisites: Python 3, `adb` on `PATH`, one unlocked phone authorised for USB debugging,
Bluetooth permissions already granted to the app (the harness does not accept system
dialogs). Do not use the phone while it runs.

```bash
python3 tools/apk_smoke.py                         # safe default run
python3 tools/apk_smoke.py --apk <path-to-apk>     # update-install first (adb install -r)
python3 tools/apk_smoke.py --serial <device-serial>
```

Reports go to `artifacts/apk-smoke/<timestamp>.json` (git-ignored). Exit code 0 means all
automatic checks passed. Reports contain no ADB serial, raw logcat or UI XML.

### What each flag does

| Flag | Phone storage | Backpack | What it checks |
| --- | --- | --- | --- |
| *(none)* | no changes | **not touched** | Home, version against the APK metadata, settings, library, backpack controls, collapsed diagnostics, opening and cancelling the library picker; no new crashes since the run started. |
| `--apk <file>` | updates the app, keeps data | not touched | Same, after `adb install -r`. |
| `--hardware` | no changes | **connects**, reads state, writes nothing | Clock sync / status query without error, brightness and screen state shown; opens the clear-contents dialog and presses **Cancel**. |
| `--fixtures` | adds two uniquely named GIFs to `Pictures/GifPack`, removes **only** those exact URIs afterwards | not touched | Valid GIF decodes in detail; malformed GIF shows an error without a crash. Needs Android 10+ and full image access. |
| `--locale-sweep` | restores the original language | not touched | Main screens in English and Czech; best with `--fixtures` so detail and editor are reached. |
| `--import` (needs `--fixtures`) | creates and then removes its own imported copies | not touched | Cold and warm `ACTION_SEND` with a valid and a malformed GIF; exactly one copy for the valid one, none for the malformed. |
| `--upload` (needs `--fixtures`) | fixture cleanup as above | **WRITES** a small 64 × 64 test animation | Upload reaches a confirmed success that survives leaving and reopening the screen. |
| `--cancel-upload` (needs `--fixtures`) | fixture cleanup as above | **WRITES** a 96-frame (~450 KB) test animation on retry | Cancel mid-transfer shows "cancelled" (no false success); retry succeeds and survives navigation. |

`--upload` and `--cancel-upload` leave their test programme on the backpack — the harness
never deletes anything there. The automatic run proves the protocol completed and the UI
reported it, not that the panel shows the right image; check the panel by eye.

## Manual checks that remain

- Visual output on the panel: colours, playback speed, edited GIFs, the text banner and the
  experimental speed / brightness bytes.
- Import after the URI grant is revoked and after process death.
- Error states of each screen, TalkBack and large font sizes.
- "Already on backpack" and "not enough space" answers on real hardware.
