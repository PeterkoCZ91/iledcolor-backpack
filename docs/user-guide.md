# User guide

This guide follows the app screen by screen. Labels are quoted as they appear in the English
UI; the Czech UI has the same layout.

Everything the backpack plays is a **64 × 64** animation. The app converts other GIF sizes
for you; what you see in the 64 × 64 previews is what is sent.

## Home and Settings

The home screen has tiles for **Search GIFs**, **Categories**, **My Collection**,
**Video → GIF**, **Scrolling text** and **Backpack**.

**Settings** (gear icon) holds:

| Setting | Effect |
| --- | --- |
| Display mode | System, light or dark theme. |
| App language | **System default**, **Čeština** (Czech) or **English**. On Android 13+ the choice also shows in the system's per-app language settings. It persists across restarts. |
| Grid density | Number of columns in result grids. |
| My interests | Tags used to suggest GIFs in search. |
| API keys | Optional personal **Giphy** and **Klipy** keys. Empty uses the key built into the app, if any. See [API keys](../README.md#api-keys) and the [screenshot](images/api-keys.png). |

## Search and categories

1. Open **Search GIFs**, type a query and browse the grid. Results load page by page.
2. **Filters**: source (**Giphy**, **Klipy**, **Lospec**), content type (GIF or sticker),
   content rating, and aspect ratio. **Square** is the default because it fits the panel
   without cropping. The **Lospec** source is a pixel-art mode: a Giphy search for square
   pixel-art results, so it needs the Giphy key.
3. **Categories** lists curated categories plus your pinned, trending-this-month and
   recently used ones. Pin a category to keep it at the top. If loading
   fails, use **Retry**.
4. Open a result to reach its **detail** screen, or download it directly. Each download
   shows pending, running, done or error, and failed downloads can be retried.

Online search needs the network and a valid key; without one the search shows an error,
while the rest of the app keeps working.

## Detail

The detail screen shows the animated GIF and a pixel-exact **64 × 64 panel preview**
(nearest neighbour, centre crop, transparent pixels as black). Actions:

- **Save to my collection** — downloads the original into the collection.
- **Share** — hands the GIF to another app.
- **Backpack 64×64** — converts and saves a 64 × 64 copy.
- **Send to backpack** — opens the backpack screen and uploads.
- **Edit for backpack** — opens the editor.

A damaged file shows "Could not display the GIF" instead of crashing.

## My collection

Your collection is the `Pictures/GifPack` folder on the phone, so its GIFs also appear in
the system gallery.

- Tap a GIF to open it, the **pencil** to edit it, or the **arrow** to send it to the
  backpack. The **⋮** button on a tile opens **Share**, **Rename**, **Edit for backpack** and
  **Delete**.
- **Long press** a GIF to start selecting several; tap more tiles to add or remove them, then
  **Join (N)**. On the next screen put the GIFs in order and choose one of two modes:
  - **Join into one GIF**: the frames are chained into a single GIF (with an optional pause
    and Fit / Crop). Preview, frame count and size estimate are shown.
  - **Send as a sequence**: every GIF becomes its own programme and the backpack plays them in
    turn. The screen shows the number of programmes and the total size. Only 453 KB in total
    has been confirmed on hardware; above that, and above 8 programmes, a warning is shown
    because the backpack's real limit is unknown.
- The **bin** icon on a tile deletes that GIF after one confirmation.
- **Find duplicates** (next to *As on backpack*) groups GIFs with identical content. Each group
  keeps one file; tick the extra copies you want to remove and confirm. Android may ask once more
  for permission to delete files the app did not create.
- **Import GIF** opens the Android picker. The app saves its own copy, so the original can
  live anywhere (including cloud providers) and later be moved or deleted.
- **Share to the app**: in a gallery or file manager choose *Share → GifPack* on a
  GIF. The app imports a copy and opens its detail. Several shares in a row are imported in
  order; rotating the phone does not import twice.
- A GIF that cannot be previewed is marked as such. Malformed files are rejected at import.

Import limits: 20 MiB per file and 600 frames. These protect the phone; they say nothing
about how much the backpack can hold.

## Editor

Open with **Edit for backpack**. The preview at the top is the real 64 × 64 result, animated,
with frame count, duration and loop mode.

| Control | Effect |
| --- | --- |
| Rotation | 0°, 90°, 180° or 270°. |
| Flip horizontally / vertically | Mirror left-right or top-bottom. |
| Display fit | **Crop to square** fills the panel and cuts the edges; **Fit entire image** keeps everything and adds black bars. |
| **Backpack playback (experimental)** | **Playback speed** and **Programme brightness**, 0–255 each, default 100; **Restore defaults**. |
| **Save a copy** | Saves the edited GIF to the collection. The original is never changed. |
| **Send to backpack** | Saves the copy (if not yet saved) and uploads it. |

Preparation can be cancelled; the original stays as it was.

**About the experimental sliders.** Speed and brightness are written into the programme
that is uploaded, next to the GIF. The defaults (100 / 100) match what the manufacturer's
app sends. Whether other values visibly change playback speed or brightness on the panel has
**not been verified yet**. They do not re-render the GIF and do not change the panel-wide
brightness set on the backpack screen. Sending with non-default values needs the Bluetooth
permission granted first, and is refused while another upload or setting is in progress.

## Scrolling text

**Scrolling text** turns a short message into a looping 64 × 64 GIF — a *new feature that
has not yet been verified on the panel*.

1. Type up to 60 characters.
2. Choose **Text color** and **Background color** (white, black, red, orange, yellow,
   green, cyan, blue, magenta). The app warns when both are the same.
3. Choose **Font size** (small, medium, large), **Speed** (slow, normal, fast) and **Bold font**.
4. The preview re-renders as you type and shows frames, duration and size. The text enters
   on the right and leaves on the left, and the loop is seamless. Long texts scroll in larger
   steps so one loop stays within 300 frames.
5. **Save to library** or **Send to backpack** (upload progress is shown in place;
   **Open backpack** jumps to the full backpack screen).

## Video → GIF

Paste a link to an **MP4** file and tap **Convert & Save**. The app downloads it (up to
100 MB), samples up to 30 frames, centre-crops them to 64 × 64 and saves the GIF to the
collection. Progress is shown for both phases and the download can be cancelled. Use only
videos you have the right to use. This flow is implemented but has not yet been verified in
the public GitHub build, so treat it as experimental for now.

## Backpack

### Connecting

1. On first use, allow **Bluetooth** (Android 12+: *Nearby devices*; Android 8–11:
   *Location*, required by Android for Bluetooth scanning). If you denied it, use
   **Open permission settings**.
   If Bluetooth is switched off, the screen says so and **Turn on Bluetooth** switches it on.
2. Switch the backpack on. Tap **Find device** and pick your backpack from the list.
3. Next time, **Connect backpack** reconnects to the last device directly.
4. After connecting, the app performs the session handshake, reads the panel state and asks
   how many built-in programmes exist. If the backpack advertises clock support, the clock
   is set; the tested backpack does not, so the step is skipped.

**Disconnect backpack** closes the connection (not available during an upload).

### Device card

The card at the top shows what the backpack reports about itself in its Bluetooth
advertisement: name, **firmware** version, **panel** size, **colours** and **features**
(for example GIF, brightness, password). It fills in once the backpack has been seen in a
scan; until then it says the details load when the backpack is found. See
[device capabilities](device-capabilities.md).

### Uploading

- **Choose a GIF from your library**, or start from the collection, detail, editor or
  banner screens.
- The upload card shows the GIF, then *Preparing*, *Connecting*, a percentage while
  sending and *Finishing*. Leaving the screen does not stop the upload.
- **Success** is shown only when the backpack confirmed the stored programme. If it already
  holds exactly this programme, the app reports "This GIF is already on the backpack" and
  sends nothing.
- **Cancel upload** stops the transfer and drops the connection (there is no verified abort
  command). **Retry** / **Upload again** starts a clean upload.
- If the backpack reports too little space or rejects the upload, the card shows an error;
  see [troubleshooting](troubleshooting.md#upload-rejected-or-failed).

### Display

Shown once connected:

| Control | Effect |
| --- | --- |
| **Refresh display status** | Re-reads screen state and brightness from the backpack. |
| Brightness | 1 (dimmest) to 10 (brightest), applied when you release the slider. *Unknown* until the state has been read. Verified on hardware. |
| Screen switch | Turns the panel on or off. Verified on hardware. |
| Rotation / Mirror | **Shown only if the backpack advertises rotation support.** The tested backpack does not, so it is hidden; rotate GIFs in the editor instead. |
| Built-in programmes | **Shown only if the backpack reports at least one** stored in its firmware. Pick a number with − / + and **Play**; nothing is stored. The tested backpack reports 0, so this section is hidden and unverified. |

After every change the app re-reads the state, so the controls show what the backpack
actually applied.

### Clear backpack contents — destructive

**Clear backpack contents** deletes **all** images and animations stored on the backpack.
GIFs on your phone are not affected, but nothing on the backpack can be recovered except by
uploading again. The app asks for confirmation; there is no undo. Only use it when you
really want an empty backpack — it is not a cleanup step for testing.

### Upload history

The last 10 uploads with time and outcome:

| Mark | Meaning |
| --- | --- |
| ✓ Confirmed by backpack | The backpack acknowledged the stored programme. |
| ✓ Already on backpack (confirmed) | The backpack already had this exact programme. |
| ✕ Failed — not confirmed by backpack | The upload did not complete; the backpack may or may not hold it. |
| – Cancelled | You cancelled it. |

History lives only on the phone and can be removed with **Clear history**.

### Diagnostics

**Show diagnostics** reveals the connection status, an **Upload test image** button (a small
generated test pattern) and the BLE log, which can be copied. The log includes your
backpack's name and Bluetooth address; remove them before sharing it.
