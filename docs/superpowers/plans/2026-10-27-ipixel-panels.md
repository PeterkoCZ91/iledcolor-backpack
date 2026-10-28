# iPixel Color panel support — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** GifPack recognises and drives iPixel Color panels (`LED_BLE_*`, any table size) next to the iledcolor backpack: image, GIF, scrolling text and prepared texts, always rendered at the connected panel's resolution.

**Architecture:** A pure-JVM `ipixel` protocol package in `core/data` (advertisement, size table, frames, ACK parsing) plus a `PanelFamily`/`PanelSpec` model. `BluetoothLeManager` learns a second GATT profile (service `0x00FA`) and exposes the connected panel's spec; `BackpackTransferManager` branches per family, with the iPixel window/ACK loop in a separate testable `IPixelUploader`. `core/conversion` gains width × height parameters with 64 × 64 defaults so every backpack path stays byte-identical.

**Tech Stack:** Kotlin, Jetpack Compose, Hilt, Android BLE (no new libraries), JUnit4 JVM tests.

**Spec:** `docs/superpowers/specs/2026-10-27-ipixel-panels-design.md` — read it first; byte layouts and verified answers are there.

## Global Constraints

- Backpack behaviour unchanged: every existing unit test passes unmodified; default parameters keep 64 × 64 output byte-identical.
- iPixel uploads go to **slot 0** only. No delete/clear/slot-save command is ever sent to an iPixel panel.
- GifPack connects to an iPixel panel only after the user picked it in GifPack's list (`picked_<address>` flag). First use and Send-while-disconnected never auto-pick an iPixel panel.
- No third-party code copied; `pypixelcolor` is only the byte-layout reference.
- Repo is public and English; every new user-visible string in `res/values/strings.xml` **and** `res/values-cs/strings.xml`.
- No new dependencies. Protocol code JVM-testable (no `android.*` in `ipixel/*` except where noted).
- Build/test from `BatohManager/` (JDK 17):
  `./gradlew -Dorg.gradle.java.home=<jdk17-path> :app:assembleDebug :app:lintDebug :app:testDebugUnitTest :core:conversion:testDebugUnitTest :core:data:testDebugUnitTest :core:storage:testDebugUnitTest :feature:backpack:testDebugUnitTest :feature:library:testDebugUnitTest :feature:search:testDebugUnitTest`
  (find the JDK path in `docs/building.md`; a one-off Gradle SIGSEGV in C2 is known — rerun once).
- Never print or commit `gradle.properties` (API keys). Do not push. Work on local branch `ipixel-panels` off `main`; local commits per task, English messages, no AI attribution lines (owner's global rule).
- ADB/hardware only in Task 8. Use the test phone; never delete anything on phone or panels.
- Code style: match surrounding code (comment density, naming); `.codegraph/` exists — use `codegraph explore` before grepping.

## Review Focus

1. **Another family member's panel grabbed by accident** — with only an iPixel panel advertising and no prior pick, Connect/Send must not connect to it (Task 3 test `connectTarget_neverAutoPicksUnpickedIPixel`).
2. **Square GIF on 64 × 16** — must letterbox (fit), not crop away 75 % (Task 2 test `defaultScaleMode_fitsWhenAspectDiffersTwofold`).
3. **Unknown iPixel type byte** — panel is listed but Send is disabled, no upload attempted (Task 1 `sizeFor_unknownTypeIsNull`, Task 5 gating).
4. **Connection drops mid-window on iPixel** — upload ends as `ConnectionLost`, not a hang; no ACK in 10 s → `ChunkTimeout` failure (Task 4 tests).
5. **Long text on a 448-px-wide panel** — banner stays within 300 frames and the 20 MiB GIF cap (Task 2 test `plan_widePanelStaysWithinFrameCap`).

---

### Task 1: iPixel protocol and panel model (pure JVM)

**Files:**
- Create: `BatohManager/core/data/src/main/java/com/batoh/core/data/bluetooth/PanelSpec.kt`
- Create: `BatohManager/core/data/src/main/java/com/batoh/core/data/bluetooth/ipixel/IPixelAdvertisement.kt`
- Create: `BatohManager/core/data/src/main/java/com/batoh/core/data/bluetooth/ipixel/IPixelProtocol.kt`
- Test: `BatohManager/core/data/src/test/java/com/batoh/core/data/bluetooth/ipixel/IPixelProtocolTest.kt`

**Interfaces — Produces:**
- `enum class PanelFamily { Backpack, IPixel }`
- `data class PanelSpec(val family: PanelFamily, val width: Int, val height: Int, val verified: Boolean)`; `companion object { val BACKPACK_DEFAULT = PanelSpec(PanelFamily.Backpack, 64, 64, true) }`
- `data class IPixelAdvertisement(val typeByte: Int)` with `val size: Pair<Int, Int>?` (= `IPixelProtocol.sizeFor(typeByte)`); `companion fun parse(name: String?, scanRecord: ByteArray): IPixelAdvertisement?` — walks AD structures, needs AD type `0xFF` with company bytes `54 52` (0x5254 little-endian) and ≥ 7 value bytes; `typeByte` = last value byte. Returns null otherwise (name not required, but log-friendly name prefix `LED_BLE_` is accepted as an additional hint only).
- `object IPixelProtocol`:
  - `val SERVICE_UUID = 000000fa-0000-1000-8000-00805f9b34fb`, `WRITE_UUID = 0000fa02-…`, `NOTIFY_UUID = 0000fa03-…` (`java.util.UUID`)
  - `const val WINDOW_BYTES = 12 * 1024`
  - `val VERIFIED_TYPES = setOf(0x81, 0x83)`
  - `fun sizeFor(typeByte: Int): Pair<Int, Int>?` — table from the spec (0x80..0x93), null otherwise
  - `fun deviceInfoCommand(hour: Int, minute: Int, second: Int): ByteArray` = `08 00 01 80 hh mm ss 00`
  - `fun parseDeviceInfo(response: ByteArray): Int?` — type byte = `response[4]` when `size >= 5 && response[3] == 0x80.toByte()`; else null
  - `fun uploadMessages(file: ByteArray, isGif: Boolean): List<ByteArray>` — one message per 12 KiB window, layout in spec
  - `sealed interface UploadAck { object WindowAccepted; object Done; data class Rejected(val code: Int) }`; `fun parseUploadAck(n: ByteArray): UploadAck?` — only `05 00 kk 00 cc` (size ≥ 5, `n[0]==0x05`): cc 0/1 → WindowAccepted, 3 → Done, other → Rejected(cc); anything else → null

- [ ] **Step 1: Write failing tests** (`IPixelProtocolTest`), exact bytes from hardware:
  - `parse_reads32x32Panel`: record `… 0A FF 54 52 00 72 00 07 02 00 81` (prepend a flags AD `02 01 06`) → `typeByte == 0x81`, `size == 32 to 32`
  - `parse_reads64x16Panel`: value `00 72 00 07 01 00 83` → `64 to 16`
  - `parse_ignoresBackpackRecord`: the synthetic TBD record from `BackpackAdvertisementTest` → null
  - `sizeFor_unknownTypeIsNull`: `sizeFor(0x7F) == null`, `sizeFor(0x94) == null`, `sizeFor(0x93) == 448 to 32`
  - `deviceInfoCommand_layout`: `(17, 27, 49)` → `08 00 01 80 11 1B 31 00`
  - `parseDeviceInfo_hardwareAnswers`: `0C 00 01 80 81 1B 31 00 00 01 00 01` → 0x81; `0B 00 01 80 83 20 03 00 00 01 00` → 0x83; 3-byte array → null
  - `uploadMessages_singlePng`: 169-byte file → 1 message; bytes 0..1 = LE(169+13+2), byte 2..4 = `02 00 00`, size/crc LE, tail `00 00`, then file
  - `uploadMessages_gifWindows`: 30 000-byte GIF → 3 messages; options `00`,`02`,`02`; kind `03`; tail `02 00`; every message carries the whole-file size and CRC; payload lengths 12288, 12288, 5424
  - `parseUploadAck_codes`: `05 00 03 00 01` → WindowAccepted, `05 00 02 00 03` → Done, `05 00 03 00 05` → Rejected(5), `0C 00 01 80 81` → null
- [ ] **Step 2:** `./gradlew … :core:data:testDebugUnitTest --tests '*IPixelProtocolTest*'` → FAIL (unresolved references)
- [ ] **Step 3:** Implement the three files per Interfaces (CRC via `java.util.zip.CRC32`).
- [ ] **Step 4:** Same command → PASS; full `:core:data:testDebugUnitTest` → PASS
- [ ] **Step 5:** Commit `Add iPixel Color protocol model, advertisement parser and frame builder`

### Task 2: Panel-size-aware conversion (`core/conversion`)

**Files:**
- Modify: `core/conversion/.../GifEditorProcessor.kt` (`transform`, `transformPixels`)
- Modify: `core/conversion/.../TextBannerRenderer.kt` (`TextBannerLayout`, `TextBannerPlan`, `TextBannerRenderer.render`)
- Modify: `core/conversion/.../TestPatternGif.kt` (`render`)
- Test: existing `GifEditorProcessorTest`, `TextBannerLayoutTest` (or equivalents — locate with codegraph) + new cases

**Interfaces — Produces:**
- `GifEditorProcessor.transform(bytes, options = GifEditOptions(), width: Int = 64, height: Int = 64, checkCancellation = {})` — **keep `checkCancellation` last; update callers that pass it positionally.** Output canvas and frames `width × height`; 128-colour palette as today.
- `GifEditorProcessor.defaultScaleMode(srcWidth: Int, srcHeight: Int, width: Int, height: Int): GifScaleMode` — `Fit` when source and panel aspect ratios differ by factor ≥ 2, else `CenterCrop`.
- Crop/fit for non-square targets: scale = `max(w/rw, h/rh)` for crop, `min(...)` for fit, centred; sample nearest at pixel centres (same formula family as today; 64 × 64 output must stay byte-identical).
- `TextBannerLayout.plan(textWidthPx, requestedStepPx, delayMs, panelWidth: Int = SIZE, maxFrames = MAX_FRAMES)`; `baseline(ascent, descent, panelHeight: Int = SIZE)`; `TextBannerPlan` gets `val panelWidth: Int = TextBannerLayout.SIZE` used by `offsetX`.
- `TextBannerRenderer.render(options, width: Int = 64, height: Int = 64, checkCancellation = {})`; text size = `options.size.textSizePx * height / 64f`.
- `TestPatternGif.render(width: Int = 64, height: Int = 64)`.

- [ ] **Step 1: Failing tests:**
  - `transform_defaultIsByteIdentical`: fixture GIF → `transform(bytes)` equals bytes produced before the change (capture a golden output in the test from the current implementation **before** editing; store as resource)
  - `transform_outputs64x16`: any GIF, `width=64,height=16` → `SafeGifDecoder.validate(result).width==64 && height==16`
  - `transformPixels_fitLetterboxesSquareInto64x16`: 4×4 all-red source, Fit, 64×16 → columns 0..23 and 40..63 black, 24..39 red
  - `defaultScaleMode_fitsWhenAspectDiffersTwofold`: `(64,64,64,16)` → Fit; `(64,48,64,64)` → CenterCrop; `(320,100,96,32)` → CenterCrop
  - `plan_usesPanelWidth`: `plan(100, 2, 80, panelWidth = 32)` → frameCount 66; offsetX(0) == 32
  - `plan_widePanelStaysWithinFrameCap`: `plan(60*40, 1, 80, panelWidth = 448)` → frameCount ≤ 300
- [ ] **Step 2:** Run `:core:conversion:testDebugUnitTest` → new tests FAIL, golden test PASS on old code
- [ ] **Step 3:** Implement per Interfaces; update all callers in the repo to compile (named args where positional `checkCancellation` was used).
- [ ] **Step 4:** Full unit-test command from Global Constraints → PASS
- [ ] **Step 5:** Commit `Conversion: render GIFs, text banners and test pattern at any panel size`

### Task 3: BLE layer — discover, pick and connect iPixel panels

**Files:**
- Modify: `core/data/.../bluetooth/BluetoothLeManager.kt`
- Create: `core/data/.../bluetooth/ConnectTarget.kt` (pure decision function)
- Create: `core/common/src/main/java/com/batoh/core/common/LastPanelSize.kt`
- Test: `core/data/src/test/.../bluetooth/ConnectTargetTest.kt`

**Interfaces — Consumes:** Task 1 (`PanelSpec`, `IPixelAdvertisement`, `IPixelProtocol`).
**Produces:**
- `data class NearbyPanel(val address: String, val name: String?, val spec: PanelSpec?, val rssi: Int)` (`spec == null` → unknown iPixel type, list it but not sendable)
- `BluetoothLeManager.nearbyPanels: StateFlow<List<NearbyPanel>>` — filled by `startScan()` from both parsers, deduped by address, newest RSSI
- `BluetoothLeManager.connectedPanel: StateFlow<PanelSpec?>` — non-null once Ready; backpack from `BackpackAdvertisement` width/height (fallback `BACKPACK_DEFAULT`), iPixel from the cached type byte
- `BluetoothLeManager.pickPanel(address: String)` — stores `picked_<address>=true`, `family_<address>`, then connects (user-initiated)
- `BluetoothLeManager.updateIPixelType(address: String, typeByte: Int)` — called by Task 4 after the info reply; overrides scan hint, persists `ipixel_type_<address>`, logs `"iPixel type: scan 0x%02X, info 0x%02X"` when they differ
- `BluetoothLeManager.ipixelNotifications: SharedFlow<ByteArray>` (FA03)
- `suspend fun BluetoothLeManager.writeIPixel(data: ByteArray): Boolean` — splits into `mtu - 3` pieces, write-with-response each, via existing `writeDataSuspend(piece, IPixelProtocol.WRITE_UUID, IPixelProtocol.SERVICE_UUID)`
- `object ConnectTarget { fun choose(lastAddress: String?, lastFamily: PanelFamily?, lastPicked: Boolean, advertising: List<NearbyPanel>): String? }` — rules: last address is backpack → as today; last address is iPixel → only if `lastPicked`; no last address → first advertising **backpack** only; never an iPixel without pick
- `object LastPanelSize { fun read(context: Context): Pair<Int, Int>; fun write(context: Context, width: Int, height: Int) }` — SharedPreferences `panel_size`, default `64 to 64`; written whenever `connectedPanel` becomes non-null

GATT: in `onServicesDiscovered`, if service `0x00FA` exists → family IPixel: enable FA03 notifications, then `requestMtuIfPermitted` (same watchdog), Ready status string unchanged (`"Ready (MTU=…)"`). Backpack branch untouched. `connectToTarget()` uses `ConnectTarget.choose`; its capability scan must also accept the matching iPixel address.

- [ ] **Step 1: Failing tests** (`ConnectTargetTest`): `connectTarget_neverAutoPicksUnpickedIPixel` (no last, only iPixel advertising → null), `connectTarget_reconnectsPickedIPixel`, `connectTarget_unpickedLastIPixelIsIgnored` (last iPixel, picked=false, backpack advertising → null), `connectTarget_firstUsePrefersBackpack` (iPixel + backpack advertising → backpack address), `connectTarget_lastBackpackAsToday`
- [ ] **Step 2:** `:core:data:testDebugUnitTest --tests '*ConnectTargetTest*'` → FAIL
- [ ] **Step 3:** Implement `ConnectTarget`, `LastPanelSize`, then the manager changes
- [ ] **Step 4:** Full unit tests + `:app:assembleDebug` → PASS
- [ ] **Step 5:** Commit `BLE: discover iPixel panels, connect only after the user picks one`

### Task 4: Family-aware transfers (`feature/backpack`)

**Files:**
- Create: `feature/backpack/.../IPixelUploader.kt`
- Modify: `feature/backpack/.../BackpackTransferManager.kt` (init block, `UploadRequest`, `startUpload`, new `uploadGifBytes`)
- Modify: `TextBannerViewModel.kt`, `GifEditorViewModel.kt` (call `uploadGifBytes`)
- Test: `feature/backpack/src/test/.../IPixelUploaderTest.kt`, `PanelUploadPrepTest.kt`

**Interfaces — Consumes:** Tasks 1–3.
**Produces:**
- `class IPixelUploader(private val write: suspend (ByteArray) -> Boolean, private val acks: Flow<ByteArray>, private val ackTimeoutMs: Long = 10_000)` with `suspend fun upload(file: ByteArray, isGif: Boolean, onProgress: (Float) -> Unit)`; throws `UploadFailureException` with existing members of `UploadFailure` (`core/data/.../UploadHistory.kt`), using window index/total as `index`/`total`: no ACK in time → `ChunkTimeout(window, windows)`; false write → `ChunkWriteFailed(window, windows)`; `UploadAck.Rejected(code)` → `ChunkRejected(window, windows, code)`; last window acknowledged but no `Done` within the timeout → `EndTimeout`. Success requires `Done` after the last window (an earlier `Done` also ends successfully). No new failure members.
- `BackpackTransferManager.uploadGifBytes(gif: ByteArray, name: String?, playback: EditorPlaybackOptions? = null)` — backpack: `playback?.buildPayload(gif) ?: BackpackPayload.fromGif(gif)` (exactly today's bytes); iPixel: ignore `playback`.
- `internal suspend fun prepareForPanel(gif: ByteArray, spec: PanelSpec): ByteArray` — backpack: unchanged `prepareProgramme` path; iPixel: validate with `SafeGifDecoder`, if size ≠ panel → `GifEditorProcessor.transform(gif, GifEditOptions(scaleMode = defaultScaleMode(...)), spec.width, spec.height)`.
- Init on Ready: backpack → today's sequence; iPixel → only `deviceInfoCommand` (wait ≤ 2 s on `ipixelNotifications` for `parseDeviceInfo != null`, then `updateIPixelType`); no auth, time, built-ins or state query. A missing answer is logged, not fatal.
- `sendTestImage()` on iPixel renders `TestPatternGif.render(w, h)`; `sendEffectTest`, `sendPlaylistTest`, `startStagedSequence`, `playBuiltIn`, settings commands → no-op on iPixel (UI hides them; this is the safety net).
- Cancelling an iPixel upload disconnects (no abort command), like the backpack.

- [ ] **Step 1: Failing tests:** `IPixelUploaderTest` — `upload_singleWindowDone` (fake acks `05 00 03 00 03` → progress reaches 1f, writes == `uploadMessages` concatenation), `upload_multiWindow` (acks `…01`,`…01`,`…03` for 3 windows), `upload_timeoutIsChunkTimeout` (no ack, `ackTimeoutMs=50` → `ChunkTimeout(0, 1)`), `upload_rejectedCode` (`…05` → `ChunkRejected(0, 1, 5)`), `upload_writeFailure` (→ `ChunkWriteFailed(0, 1)`); `PanelUploadPrepTest` — `prepare_backpackBytesUnchanged` (64×64 fixture → identical to `BackpackPayload.fromGif`), `prepare_ipixelConverts64x64To32x32`
- [ ] **Step 2:** `:feature:backpack:testDebugUnitTest` → new tests FAIL
- [ ] **Step 3:** Implement; switch the two view models to `uploadGifBytes`
- [ ] **Step 4:** Full unit tests → PASS
- [ ] **Step 5:** Commit `Transfers: upload images, GIFs and text to iPixel panels`

### Task 5: Panel screen — picker, device card, gating

**Files:**
- Modify: `feature/backpack/.../BackpackScreen.kt`, its view model, `GifEditorScreen.kt`, `GifChainScreen.kt`
- Modify: `HomeBackpackStatus.kt` in the home module
- Modify: `feature/backpack/src/main/res/values/strings.xml`, `values-cs/strings.xml` (and `feature/home` if its tile text changes)

**Interfaces — Consumes:** `nearbyPanels`, `connectedPanel`, `pickPanel` (via `BackpackTransferManager` pass-throughs `val nearbyPanels`, `val connectedPanel`, `fun pickPanel(address)`).

Behaviour:
- Screen title "Panel" / "Panel" (cs "Panel"). Section "Nearby panels": row per `NearbyPanel` = name, family ("Backpack" / "iPixel Color"), size `W × H` or "Unknown type", "untested size" when `!spec.verified`, signal; tap → `pickPanel`. Shown when disconnected or via a "Switch panel" button when connected.
- Device card for iPixel: family, size, verified/untested, name; backpack card unchanged.
- Hidden when `connectedPanel?.family == IPixel`: built-ins, rotation/mirror, brightness, screen on/off, clear programmes, password status, effect test, playlist test, editor speed/light controls, GifChain "Send as sequence" (the chain's "Save" stays). Send disabled when the connected iPixel has `spec == null`.
- Home tile: shows family + size of the connected panel (e.g. "iPixel Color · 64 × 16").

- [ ] **Step 1:** Add strings (en + cs) for: nearby panels, switch panel, family names, unknown type, untested size.
- [ ] **Step 2:** Implement UI; keep backpack layout identical when a backpack is connected.
- [ ] **Step 3:** `:app:assembleDebug :app:lintDebug` + full unit tests → PASS (lint: no missing translations)
- [ ] **Step 4:** Commit `Panel screen: list nearby panels, iPixel device card, hide backpack-only controls`

### Task 6: Previews, editor and banner follow the panel size

**Files:**
- Modify: `feature/library/.../BackpackPreview.kt` (`BackpackPreviewScaler.scaleToPanel(pixels, w, h, crop, panelWidth: Int = 64, panelHeight: Int = 64)`, `BackpackPreviewTransformation(crop, panelWidth, panelHeight)` with cache key including the size)
- Modify: library and detail screens that build the transformation (read `LastPanelSize.read(context)`)
- Modify: `GifEditorViewModel.kt` (transform at `connectedPanel` size, else `LastPanelSize`), `TextBannerViewModel.kt` (render at that size; saved banner keeps that size)
- Modify: the duplicated preview sampler in `feature/detail` (keep the "identical by tests" rule from `docs/architecture.md`)
- Test: existing preview-sampler tests + `scaleToPanel_64x16Fit`

- [ ] **Step 1:** Failing test `scaleToPanel_64x16Fit` (square red 4×4, crop=false, 64×16 → centre 16 columns red) in both duplicated sampler test classes
- [ ] **Step 2:** Run → FAIL
- [ ] **Step 3:** Implement; default arguments keep 64 × 64 behaviour
- [ ] **Step 4:** Full unit tests + assemble → PASS
- [ ] **Step 5:** Commit `Previews, editor and text banner use the connected panel's size`

### Task 7: Prepared texts

**Files:**
- Create: `feature/backpack/.../PreparedTexts.kt` (codec, like `UploadHistory`)
- Modify: `TextBannerViewModel.kt`, `TextBannerScreen.kt`, strings en + cs
- Test: `feature/backpack/src/test/.../PreparedTextsTest.kt`

**Interfaces — Produces:**
- `data class PreparedText(val id: Long, val options: TextBannerOptions)`
- `object PreparedTexts { const val MAX = 30; fun encode(list: List<PreparedText>): String; fun decode(raw: String?): List<PreparedText>; fun add(list, item): List<PreparedText> /* newest first, drops oldest over MAX, ignores blank text, de-dupes identical options */; fun remove(list, id): List<PreparedText> }` — stored in SharedPreferences `prepared_texts`, key `items`; tolerant decode (bad entries skipped).
- ViewModel: `preparedTexts: StateFlow<List<PreparedText>>`, `savePrepared()`, `deletePrepared(id)`, `sendPrepared(id)` (renders at the connected panel size, then `uploadGifBytes`).
- UI: "Save as prepared text" button; list below the editor, each row = text, colours swatch, Send, Delete (with undo snackbar, no confirm dialog). Works for any family.

- [ ] **Step 1:** Failing tests: `roundTrip`, `add_newestFirstAndCapped`, `add_ignoresBlank`, `add_dedupes`, `decode_skipsCorruptEntries`, `remove_byId`
- [ ] **Step 2:** Run → FAIL
- [ ] **Step 3:** Implement codec, view model, UI, strings
- [ ] **Step 4:** Full unit tests + assemble + lint → PASS
- [ ] **Step 5:** Commit `Text banner: prepared texts sent with one tap to the connected panel`

### Task 8: Docs, version, hardware verification

**Files:**
- Modify: `docs/ble-protocol.md` (new iPixel section with [HW]/[DER] tags), `docs/device-capabilities.md`, `docs/architecture.md` (new classes), `docs/user-guide.md`, `CHANGELOG.md`, `README.md` (supported panels), `app/build.gradle.kts` versionCode 66

- [ ] **Step 1:** Write docs; bump versionCode to 66.
- [ ] **Step 2:** Full build/lint/test command → PASS.
- [ ] **Step 3 (hardware, the test phone via ADB; owner present):** install debug APK as update (data kept). With both iPixel panels on: list shows both with 32 × 32 / 64 × 16 and family; nothing connects before a pick. Pick 32 × 32 → send library GIF, test image, banner "AHOJ", one prepared text → each ends "Confirmed". Same on 64 × 16, including a square GIF (letterboxed) and a > 12 KiB GIF. Owner confirms the panels visually.
- [ ] **Step 4 (backpack regression, owner switches the backpack on):** connect, upload a library GIF and a text banner, brightness round-trip 7→6→7 — all as before.
- [ ] **Step 5:** Record results in the project notes (outside the repo); commit `Docs and version 66 for iPixel Color panel support`.

### Task 9: iPixel panel settings (owner request after hardware test)

Owner: "the panels can do the same — brightness etc." Commands verified from a PC on the 64 × 16 panel on 2026-10-27, every one answered OK (`… 01` as last byte):

| Setting | Command | Answer |
|---|---|---|
| Brightness 0–100 | `05 00 04 80 LL` | `05 00 04 80 01` |
| Rotation 0–3 (×90°) | `05 00 06 80 OO` | `05 00 06 80 01` |
| Display off / on | `05 00 07 01 00` / `05 00 07 01 01` | `05 00 07 01 01` |
| Time (also the info query) | `08 00 01 80 hh mm ss 00` (binary values) | info answer |
| Clock face | `0B 00 06 01 style(0–8) 24h(0/1) date(0/1) yy mm dd dow(Mon=1)` | `05 00 06 01 01` |

Never send `04 00 03 80` (wipes the panel's ROM including settings) or `07 00 02 01 01 00 n` (deletes a slot).

**Files:**
- Modify: `core/data/.../bluetooth/ipixel/IPixelProtocol.kt` (+ `IPixelProtocolTest`)
- Modify: `feature/backpack/.../BackpackTransferManager.kt`, `BackpackScreen.kt` (+ view model), strings `values/` (cs) and `values-en/`

**Interfaces — Produces:**
- `IPixelProtocol.brightnessCommand(level: Int): ByteArray` (require 0..100), `rotationCommand(quarterTurns: Int)` (require 0..3), `screenCommand(on: Boolean)`, `clockCommand(style: Int, use24h: Boolean, showDate: Boolean, date: java.time.LocalDate): ByteArray` (require style 0..8; yy = year % 100, dow = `date.dayOfWeek.value`), `fun isSettingAck(n: ByteArray, commandId: Int): Boolean` (true for `05 00 id xx 01`).
- `BackpackTransferManager`: `ipixelBrightness(level)`, `ipixelRotation(quarterTurns)`, `ipixelScreen(on)`, `ipixelClock(style, use24h, showDate)` — run under the same single-operation rule as backpack settings, only when the connected family is IPixel; wait ≤ 2 s for `isSettingAck`; failure → existing `_commandError` with a localized text. The connect-time info query already sends the current time (must use binary values).
- UI (iPixel only, shown where the backpack's display section is): brightness slider 0–100 (send on release), display on/off switch, rotation 0°/90°/180°/270° chips, "Clock" section with style 0–8 picker, 24h and show-date switches and a "Show clock" button. State is not readable from the panel: show the last value set in this session, default slider 100, rotation 0, display on.

- [ ] **Step 1: Failing tests** in `IPixelProtocolTest`: `brightnessCommand_layout` (20 → `05 00 04 80 14`, 101 throws), `rotationCommand_layout` (2 → `05 00 06 80 02`), `screenCommand_layout` (false → `05 00 07 01 00`), `clockCommand_layout` (style 1, 24h, date, 2026-10-27 → `0B 00 06 01 01 01 01 1A 0A 1B 02`), `isSettingAck_hardwareAnswers` (`05 00 04 80 01` for 0x04 true, `05 00 06 01 01` for 0x06 true, `05 00 04 80 00` false), `deviceInfoCommand_usesBinaryTime` (18:51:40 → `08 00 01 80 12 33 28 00`).
- [ ] **Step 2:** run → FAIL. **Step 3:** implement protocol, manager, UI, strings. **Step 4:** full build/lint/unit tests → PASS. **Step 5:** commit `iPixel panels: brightness, rotation, display and clock settings`.

### Task 10: Send to all my panels (owner request)

Owner wants one tap to send the same GIF / text to all of **their own** panels. "My panels" = panels the user has picked in GifPack (`picked_<address>` already stored by `pickPanel`), plus a way to remove a panel from that set. Never includes panels that were only seen in a scan. (Sending to every panel nearby was explicitly rejected: it would overwrite strangers' panels.)

**Behaviour:**
- Panel screen: "My panels" list (name, family, size, in range or not) with "Remove from my panels" per row (clears `picked_`, does not touch the panel).
- New action "Send to all my panels" next to Send in: library picker send, GIF detail/editor send, text banner Send and each prepared text row.
- Runs sequentially over my panels currently advertising (quick scan ≤ 5 s): connect → upload the content rendered/converted for that panel's family and size (same paths as a single send; banner/prepared text re-rendered per size) → disconnect → next. One failing panel does not stop the rest.
- Progress: "Panel 2 / 3: <name> …"; summary at the end: per panel ✓ / ✗ with reason; each panel's result goes to `UploadHistory` as a normal entry. Cancel stops after the current panel's transfer is cancelled.
- After the run, reconnect to the panel that was connected before (if any).
- Strings in `values/` (cs) and `values-en/`. Czech suggestions: "Moje panely", "Odebrat z mých panelů", "Poslat na všechny moje panely", "Panel %1$d / %2$d: %3$s", "Hotovo na %1$d z %2$d panelů".

**Files (builder decides exact split):** `BluetoothLeManager` (read picked set, `unpickPanel(address)`), new `feature/backpack/.../MultiPanelSend.kt` (pure planner: which panels, order, per-panel result aggregation — JVM-tested), `BackpackTransferManager` (sequential runner reusing the single-panel connect/upload code), UI in `BackpackScreen`, `BackpackGifPicker`, `GifEditorScreen`, detail send, `TextBannerScreen`.

- [ ] **Step 1: Failing tests** for the pure planner: `plan_onlyPickedAndAdvertising` (picked+advertising included; picked but absent → "not in range"; advertising but not picked → never included), `plan_keepsPreviousPanelFirstOrLast` (decide and pin), `summary_countsSuccessAndFailure`, `unpick_removesFromSet`.
- [ ] **Step 2:** run → FAIL. **Step 3:** implement. **Step 4:** full build/lint/unit tests → PASS. **Step 5:** commit `Send to all my panels`.
