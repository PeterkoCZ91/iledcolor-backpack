# Architecture

GifPack is a single-activity Android app written in Kotlin with Jetpack Compose,
Hilt for dependency injection, Room for small local tables, WorkManager for downloads,
Retrofit/OkHttp for the GIF services and Coil for image loading. It is split into Gradle
modules so that the GIF pipeline and the BLE protocol can be unit-tested on the JVM without
a phone or a backpack.

## Modules

```text
BatohManager/
├── app                 MainActivity, navigation graph, share-to-app import queue
├── core/
│   ├── common          Result type, UI preference keys (theme, language, grid, API keys)
│   ├── domain          Models (Gif, GifFilter, categories) and use cases, no Android UI
│   ├── data            Giphy/Klipy repositories + the BLE layer (bluetooth/*)
│   ├── network         Retrofit/OkHttp clients
│   ├── storage         Room database, MediaStore library, import reader, DownloadWorker
│   ├── conversion      Safe GIF decoder, editor processor, GIF/LZW encoders, text banner, video → GIF, panel preview sampler
│   └── ui              Theme, shared components, navigation routes
└── feature/
    ├── home            Home dashboard and the More tab (tools, theme, language, grid, interests, API keys, About)
    ├── search          Search, filters, categories, download state
    ├── library         My collection, import, delete, panel preview
    ├── detail          Detail screen, panel preview, save / share / send / edit
    ├── convert         Video URL → GIF
    └── backpack        Panel screen, editor, text banner, prepared texts, BackpackTransferManager
```

Feature modules depend on `core/*`, never on each other. The "as on the panel" preview sampler
is shared the same way: it lives once in `core/conversion` (`PanelPreviewSampler`, the same
sampling as the upload converter), and both `feature/library` and `feature/detail` use it, so
`feature/library` now depends on `core:conversion`.

The app supports two panel families, the iledcolor backpack and iPixel Color panels. A
`PanelSpec` (family, width, height, verified flag) describes the connected panel; everything
that renders or uploads takes its size from it instead of assuming 64 × 64.

## Data flow: GIF → payload → BLE upload

```mermaid
flowchart LR
    SRC[Search download / import / share / editor / text banner / video] --> LIB[(Pictures/GifPack)]
    LIB --> URI[content:// URI]
    URI --> READ[Bounded read<br/>≤ 20 MiB, cancellable]
    READ --> VAL[SafeGifDecoder.validate]
    VAL -->|not panel size| CONV[GifEditorProcessor.transform<br/>crop or fit, NeuQuant, LZW]
    VAL -->|panel size| PAY
    CONV --> PAY[Backpack: BackpackPayload.fromGif<br/>24 B header + 22 B item + GIF<br/>iPixel: the GIF itself]
    PAY --> TM[BackpackTransferManager]
    TM --> BLE[BluetoothLeManager]
    BLE -->|Cmd 06 / data chunks / end| DEV[Backpack]
    BLE -->|12 KiB windows to slot 0| IPX[iPixel panel]
    DEV -->|ACK notifications| BLE
    IPX -->|window ACKs| BLE
```

1. **Source.** Every route ends with a GIF stored in the MediaStore collection
   `Pictures/GifPack`: downloads (`DownloadWorker`), picker imports and shared GIFs
   (`GifImportReader`, `IncomingImportQueue`), edited copies and text banners
   (`SaveGifBytesUseCase`).
2. **Read and validate.** `BackpackTransferManager.startUpload` reads the URI in 8 KiB
   blocks with a 20 MiB cap and cancellation checks, then `SafeGifDecoder.validate` checks
   the structure, frame count and pixel budgets before any decoding work is trusted.
3. **Convert.** A GIF that is not the panel's size (64 × 64 for the backpack, the reported size
   for an iPixel panel) goes through `GifEditorProcessor.transform` with default options
   (centre crop, or fit with black bars when the shapes differ strongly; nearest neighbour,
   transparency → black), is quantised with `NeuQuant` and re-encoded with `LzwEncoder`. The
   editor uses the same processor with the user's rotation, flips and fit/crop.
   `PanelUploadPrep` chooses the path per family.
4. **Payload.** `BackpackPayload.fromGif` wraps the unmodified 64 × 64 GIF in a programme:
   a 24-byte header whose file ID is a CRC-32C of the body, then one 22-byte item (rectangle,
   type 6 = GIF, effect, speed, light). The editor's experimental speed / brightness values go
   into this item via `EditorPlaybackOptions.buildPayload`. Built-in programmes use a type-5
   item instead (`BackpackPayload.builtIn`).
5. **Upload.** `BackpackTransferManager.doUpload` performs the session handshake once per
   connection, sends the start command (Cmd 06) with the file ID and size, and branches on
   its status: send data, already present, not enough space or rejected. Data goes out in
   MTU-sized chunks, each waiting for its own acknowledgement, followed by an end frame that
   the backpack must confirm. The exact byte layouts are in the
   [BLE protocol](ble-protocol.md) reference. An iPixel panel gets the converted GIF as 12 KiB
   windows into slot 0 (`IPixelUploader`), each acknowledged by the panel; there is no programme
   payload, and no delete or slot command exists in this path.
6. **Result.** The upload state (`Preparing → Connecting → Sending → Finishing →
   Success | Error | Cancelled`) drives the UI, and the outcome is appended to
   `UploadHistory` (`Confirmed`, `AlreadyPresent`, `Failed`, `Cancelled`).

## Key classes

### BLE layer (`core/data/.../bluetooth`)

| Class | Responsibility |
| --- | --- |
| `BluetoothLeManager` | Singleton GATT client: scan, connect to the last device, service discovery, notifications, MTU request (512), serialised writes, command/response matching, BLE diagnostics log. Caches each device's advertisement and name so direct reconnects still know its capabilities. |
| `BackpackFrame` | Builds and validates frames (`0x54`, command, length, payload, 16-bit checksum), data chunks and the end frame; `chunkLength(mtu)`. |
| `BackpackPayload` | Programme payload (header, item, CRC-32C file ID), start frame, GIF size check, built-in programme payload. |
| `BackpackCommands` | Settings commands — clear programmes, brightness, screen, rotation, time, status query, built-in count — and parsers for their answers. |
| `BackpackAdvertisement` | Parses the scan record: panel size, colour type, firmware version, `funCode` feature bits. See [device capabilities](device-capabilities.md). |
| `PanelSpec`, `PanelFamily` | Family (`Backpack`, `IPixel`), width, height and whether the size was verified on hardware. |
| `ConnectTarget`, `NearbyPanel` | Decides which panel an automatic connect may open: a known backpack as before, an iPixel panel only if the user picked it in GifPack. `NearbyPanel` is one entry of the scan list. |
| `IPixelAdvertisement` | Parses the iPixel manufacturer data (company `0x5254`); the last value byte is the device type. See [device capabilities](device-capabilities.md#9-ipixel-color-advertisement). |
| `IPixelProtocol` | GATT UUIDs (service 00FA), device type → size table, info command, upload window messages (CRC-32, kind, slot 0), the write piece size `clamp(mtu − 3, 20, 512)`, the brightness / rotation / display / clock setting commands and the ACK parsers. JVM-testable. |
| `PickedPanels`, `MyPanel` | "My panels": exactly the addresses whose `picked_<address>` flag is set, read from the stored preferences; also which keys "remove from my panels" clears (the flag, and the last-panel address if it is that panel). JVM-testable. |
| `PendingCommandResponse` | Matches a notification to the command that is waiting for it; JVM-testable. |
| `JieLiAuth` | Session handshake on the auth characteristic pair, performed once per connection before commands. |
| `UploadHistory` | Encodes/decodes the last 10 upload outcomes for SharedPreferences. |

### Backpack feature (`feature/backpack`)

| Class | Responsibility |
| --- | --- |
| `BackpackTransferManager` | Singleton owner of every panel transaction. Navigation never cancels a transfer. After each backpack connection it runs the handshake, optional clock sync, built-in count and status query; after an iPixel connection only the info query. It sends the iPixel settings (brightness, rotation, display, clock face; each waits for its `05 00 id xx 01` answer) and runs "send to all my panels". It exposes upload state, panel state, advertisement, history and errors as flows. |
| `MultiPanelSend` | Pure planning of a "send to all my panels" run: only picked panels that advertise or are connected, strongest signal first and the previously connected panel last; the summary, and whether the run's end must close a leftover link or pending GATT retry. The run itself (in `BackpackTransferManager`) connects to each panel through `BluetoothLeManager.connectToPicked`, which refuses unpicked addresses, and afterwards restores the last panel's address and size. |
| `SingleOperationOwner` | Guarantees one upload and one settings operation at a time; a second request is ignored instead of interleaving frames. |
| `IPixelUploader` | Writes the upload windows to an iPixel panel one at a time (each cut into ≤ 512-byte writes), waiting for each `05 00 kk 00 cc` acknowledgement; failures are typed like the backpack's. Settings do not go through it. |
| `PanelUploadPrep` | Per-family preparation of a GIF: the backpack programme payload, or the GIF re-rendered at the iPixel panel's size. |
| `PreparedTexts`, `PreparedText` | The saved list of scrolling texts (up to 30, plain text in SharedPreferences, newest first); rendered for the connected panel only when sent. |
| `BackpackUploadProtocol` | `classifyUploadStart`: maps the Cmd 06 status byte to `SendChunks`, `AlreadyPresent`, `InsufficientSpace` or `Rejected`. |
| `EditorPlaybackOptions` | Experimental speed / light bytes (0–255, default 100) and the payload they produce. |
| `BackpackScreen`, `GifEditorScreen`, `TextBannerScreen` | Compose UIs and their view models. |

### GIF pipeline (`core/conversion`)

| Class | Responsibility |
| --- | --- |
| `SafeGifDecoder` | Bounded, cancellable GIF parser and LZW decoder (20 MiB, 600 frames, 4 M canvas pixels, 64 M decoded pixels); never enters the platform's native GIF decoder. |
| `GifEditorProcessor` | Rotation, flips, fit/crop to the panel size (64 × 64 by default), palette quantisation and GIF re-encoding. |
| `PanelPreviewSampler` | The "as on the panel" preview: the same sampling as the upload converter, shared by the library and detail previews. |
| `GifEncoder`, `LzwEncoder`, `NeuQuant` | GIF writer, LZW compressor and colour quantiser. |
| `TextBannerRenderer` | Renders scrolling text with `Canvas` into a seamless looping GIF at any panel size (font height follows the panel height); `TextBannerLayout` holds the pure frame-plan math (max 300 frames, 60 characters). |
| `VideoToGifConverter` | Downloads an MP4 (≤ 100 MB), samples up to 30 frames and encodes a 64 × 64 GIF; also the default converter for non-64 × 64 GIFs sent to the backpack. |

### Storage and import (`core/storage`, `app`)

| Class | Responsibility |
| --- | --- |
| `LocalMediaRepositoryImpl`, `MediaStoreHelper` | The `Pictures/GifPack` collection: list, save, delete, import copies. |
| `GifImportReader`, `CancellableStreamCopy` | Bounded, cancellable reads of a picker or shared URI; a cancelled write removes the partial MediaStore row. |
| `GifFileInspector` | Classifies files the library cannot preview (`GifFileProblem`). |
| `DownloadWorker` | WorkManager download of an online GIF with validation before saving. |
| `BatohDatabase` | Room: search history, cached search results, category preferences. |
| `IncomingImportQueue` | FIFO of shared GIFs that survives rotation and does not replay a finished import after restore. |

## Concurrency and failure containment

- All BLE writes are serialised; a command waits up to 2 s for its answer, a data chunk up
  to 2 s for its acknowledgement, and connecting for an upload up to 20 s.
- An unanswered settings command closes the connection, so a late answer cannot be taken as
  the acknowledgement of a retry.
- Any exception during an upload disconnects while the transaction is still owned; the next
  attempt starts from a fresh session.
- Cancelling during data transfer disconnects, because no verified abort command exists.
- Losing the connection cancels a running data transfer and clears the cached panel state.
- Capability-gated features (clock sync, rotation, built-in programmes) default to hidden or
  skipped when the device does not report them.

## Persistent state

| Store | Contents |
| --- | --- |
| MediaStore `Pictures/GifPack` | The GIF collection (shared with the system gallery). |
| Room `BatohDatabase` | Search history, cached results, pinned / recent categories. |
| SharedPreferences `batoh_ui_preferences` | Theme, grid density, interests, optional API keys (plain text, app-private). |
| SharedPreferences `backpack_connection` | Last connected address, cached advertisement and name per address; for iPixel panels also the device type and the "picked in GifPack" flag. |
| SharedPreferences `panel_size` | Width and height of the last connected panel (`LastPanelSize`, 64 × 64 if none). |
| SharedPreferences (prepared texts) | The saved scrolling texts and their options (`PreparedTexts`). |
| SharedPreferences `backpack_upload_history` | Last 10 upload outcomes. |
