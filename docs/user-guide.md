# User guide

This guide follows the app screen by screen. Labels are quoted as they appear in the English
UI; the Czech UI has the same layout.

Everything is shown and sent at the **connected panel's size**: 64 × 64 for the backpack, or
the size of the iPixel Color panel you picked (for example 32 × 32 or 64 × 16). Without a
connection the size of the last panel is used (64 × 64 if there was none). The app converts
other GIF sizes for you; what you see in the previews is what is sent.

## Navigation

A bottom bar has five tabs: **Home**, **Search**, **Panel**, **Collection** and **More**.

- **Home** is a dashboard: a panel status card (connection state and device name), a search
  bar, quick actions for **Scrolling text** and **Video → GIF**, and your recent GIFs with
  **Show all** to open the collection.
- **Search** finds GIFs online; with an empty query it shows the categories.
- **Panel** connects to and controls your panels (see [below](#panel-tab)).
- **Collection** holds your saved GIFs.
- **More** has the tools (**Video → GIF**, **Scrolling text**), the settings and **About**.

### More: settings and About

The settings are inline on the **More** tab:

| Setting | Effect |
| --- | --- |
| Display mode | System, light or dark theme. |
| App language | **System default**, **Čeština** (Czech) or **English**. On Android 13+ the choice also shows in the system's per-app language settings. It persists across restarts. |
| Grid density | Number of columns in result grids. |
| My interests | Tags used to suggest GIFs in search. |
| API keys | Optional personal **Giphy** and **Klipy** keys. Empty uses the key built into the app, if any. See [API keys](../README.md#api-keys) and the [screenshot](images/api-keys.png). |

**About** at the bottom of the tab shows the app description and the version.

## Search and categories

1. Open the **Search** tab (or the search bar on Home), type a query and browse the grid. Results load page by page.
2. **Filters**: source (**Giphy**, **Klipy**, **Lospec**), content type (GIF or sticker),
   content rating, and aspect ratio. **Square** is the default because it fits the panel
   without cropping. The **Lospec** source is a pixel-art mode: a Giphy search for square
   pixel-art results, so it needs the Giphy key.
   Every result tile has a **Send to panel** button: it saves the GIF to your collection and then
   starts the same send flow as the Panel tab (including the *Where to send?* sheet).
3. With an empty query the Search tab lists the **categories**: curated ones plus your pinned, trending-this-month and
   recently used ones. Pin a category to keep it at the top. If loading
   fails, use **Retry**.
4. Open a result to reach its **detail** screen, or download it directly. Each download
   shows pending, running, done or error, and failed downloads can be retried.

Online search needs the network and a valid key; without one the search shows an error,
while the rest of the app keeps working.

## Detail

The detail screen shows the animated GIF and a pixel-exact **panel preview** (at the connected panel's size)
(nearest neighbour, centre crop, transparent pixels as black). Actions:

- **Save to my collection** — downloads the original into the collection.
- **Share** — hands the GIF to another app.
- **Save for panel** — converts and saves a 64 × 64 copy in the collection (always 64 × 64; it is adapted to the panel's own size when you send it).
- **Send to panel** — uploads to a panel. If there is not exactly one candidate panel, the app asks **Where to send?**; the GIF is always converted to the chosen panel's own size.
- **Edit for panel** — opens the editor.

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
- **Find duplicates** finds GIFs with identical content and asks **Delete them and keep one of
  each?** One file per group always stays. Android may ask once more for permission to delete
  files the app did not create.
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
brightness set on the Panel tab. Sending with non-default values needs the Bluetooth
permission granted first, and is refused while another upload or setting is in progress.

## Scrolling text

**Scrolling text** (Home or More) turns a short message into a looping GIF at the connected panel's size (64 × 64 for the backpack) — a *new feature that
has not yet been verified on the panel*.

1. Type up to 60 characters.
2. Choose **Text color** and **Background color** (white, black, red, orange, yellow,
   green, cyan, blue, magenta). The app warns when both are the same.
3. Choose **Font size** (small, medium, large), **Speed** (slow, normal, fast) and **Bold font**.
4. The preview re-renders as you type and shows frames, duration and size. The text enters
   on the right and leaves on the left, and the loop is seamless. Long texts scroll in larger
   steps so one loop stays within 300 frames.
5. **Save to library** or **Send to backpack** (upload progress is shown in place;
   **Open backpack** jumps to the Panel tab). A banner saved to the library has the
   size of the panel it was rendered for.

### Prepared texts

**Save as prepared text** keeps the message with its colours, size, speed and bold setting in a
list (up to 30, newest first; identical entries are not added twice). Under **Prepared texts**,
**Send** renders the text for the panel that is connected right now and uploads it with one
tap, so the same list works on the backpack and on a 64 × 16 panel. **Delete** removes an entry
(with **Undo**). The list lives only on the phone; nothing is stored inside the panel.

## Video → GIF

Open it from Home or More, paste a link to an **MP4** file and tap **Convert & Save**. The app downloads it (up to
100 MB), samples up to 30 frames, centre-crops them to 64 × 64 and saves the GIF to the
collection. Progress is shown for both phases and the download can be cancelled. Use only
videos you have the right to use. This flow is implemented but has not yet been verified in
the public GitHub build, so treat it as experimental for now.

## Panel tab

The **Panel** tab works with the iledcolor backpack and with iPixel Color
(`LED_BLE_XXXXXXXX`) panels. Besides the connection and display controls it offers **Send a GIF**
(from your phone or from your collection), **My panels**, the upload history
and **Advanced**. The sections below describe the backpack; the iPixel differences are at the
end.

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

- **Send a GIF** on the Panel tab opens *Send a GIF from…* with **From my phone** (system file picker)
  and **From my collection** (your saved GIFs). When there is not exactly one candidate panel, a
  *Where to send?* sheet follows; with two or more of your panels it has an **All my panels** row.
  You can also start from the collection, detail, editor or
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

### iPixel Color panels

- **Nearby panels** lists every supported panel the app sees: family, size, name and signal.
  Sizes other than 32 × 32 and 64 × 16 are marked *untested size*; a panel of an unknown type is
  listed but nothing can be sent to it.
- **Pick the panel in this list.** An iPixel panel is connected **only after you pick it**; the
  app never connects to one on its own, on first use or when you tap Send while disconnected
  (so it cannot take over a panel that belongs to someone else). After the first pick it is
  reconnected as the last-used panel. **Switch panel** lets you change.
- The device card shows family, size and whether that size was tested on hardware.
- Uploads (images, GIFs, the test image, scrolling text and prepared texts) go only to the
  panel's **display slot**: nothing the vendor app stored on the panel is deleted or replaced,
  and the app has no delete command for these panels.
- **Display** for an iPixel panel has its own controls: brightness 0–100 %, display on/off,
  rotation (0°, 90°, 180°, 270°) and a **Clock** section (face 0–8, 24-hour format, show date,
  **Show clock**). Each setting is sent when you change it and the panel confirms it; if it does
  not, an error is shown and the setting in the app stays as it was.
- Backpack-only controls are hidden: built-in programmes, clear contents, programme speed /
  brightness / effect in the editor, sending as a sequence and the effect and playlist tests.
  Joining GIFs into one GIF still works.
- Large GIFs are sent in several windows; a 40 KB GIF takes roughly 10–16 seconds.

### My panels and sending to all of them

- **My panels** lists every panel you picked in *Nearby panels* (backpack or iPixel), with
  whether it is in range. **Remove from my panels** takes it off the list; if it was the
  last-used panel, the app forgets that too and goes back to finding the backpack on its own.
- **Send to all my panels** (the **All my panels** row of the *Where to send?* sheet, in Detail, in the Editor and for the scrolling text
  and each prepared text) sends the same content to every panel in *My panels* that is in range,
  one after another. Each panel gets it converted for its own family and size; a text banner is
  rendered separately for each size. Only panels you picked yourself are ever contacted — a
  panel that is merely nearby, for example someone else's, never receives anything.
- A quick scan comes first; panels that are not in range are listed as such and skipped. The
  progress shows which panel is being sent, and the summary shows the result per panel. A
  failing panel does not stop the run; **Cancel upload** stops the current panel's transfer and
  skips the remaining panels.
- The panel that was connected before is sent to last and stays connected; otherwise it is
  reconnected at the end. Each panel's result also appears in the upload history.

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
