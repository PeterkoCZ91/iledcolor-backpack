# Troubleshooting

Start with **Backpack → Show diagnostics**. The connection status line and the BLE log show
how far a connection or upload got. The log contains your backpack's name and Bluetooth
address — replace them with placeholders such as `AA:BB:CC:DD:EE:FF` before posting it
anywhere.

## Bluetooth permissions

| Symptom | Cause and fix |
| --- | --- |
| "Allow Bluetooth access to connect." stays visible | The app has no Bluetooth permission. Tap **Allow Bluetooth**; if Android no longer shows the dialog (denied twice), tap **Open permission settings** and allow it there. |
| Android 12+ asks for *Nearby devices* | That is the Bluetooth scan/connect permission. The app declares scanning as not used for location. |
| Android 8–11 asks for *Location* | Android requires location permission for Bluetooth LE scanning on these versions. Location services may also need to be switched on for scans to return results. |
| **Find device** lists nothing | Make sure the backpack is on and not connected to another phone or the manufacturer app. Turn Bluetooth off and on. Scans stop on their own; tap **Find device** again. |
| Text banner or editor says Bluetooth is required | Sending from those screens needs the permission already granted — open the Backpack screen once and allow it. |

## Connection problems

### GATT error 133

`status=133` in the log is Android's generic GATT connection error. It was seen once during
testing on the first connection attempt; the next attempt with the same GIF succeeded. There
is no confirmed cause and no automatic retry yet.

What helps:

1. Tap **Retry** (or **Connect backpack**) once more.
2. Close the manufacturer app or anything else that may hold the backpack.
3. Toggle Bluetooth on the phone, or switch the backpack off and on.
4. If it happens regularly, please report it with the Android version and phone model
   (see [SECURITY.md](../SECURITY.md) for what not to include).

The app always closes the GATT client after a disconnect, so repeated failures should not
exhaust Android's connection slots.

### "Batoh se nepodařilo připojit" / connection timeout

An upload waits up to 20 s for the backpack to become ready (connected, services found,
MTU negotiated, session handshake and initial status read). Move closer, make sure the
backpack is on, then **Retry**.

### Connect goes to the wrong device or nothing happens

**Connect backpack** reconnects to the last device that connected successfully. If you have
a new backpack, or the app has never connected, use **Find device** and pick it from the
list instead. A successful connection becomes the new default.

### Device card says details are not known

The panel size, firmware and features exist only in the backpack's advertisement, not in
any command answer. The card fills in after the backpack has been seen in a scan. When
connecting directly, the app runs a short (≤ 3 s) scan filtered to that one address to catch
it. Until then, capability-gated controls stay hidden.

## MTU

The app requests an MTU of 512 after enabling notifications; the tested backpack and phone
negotiate 512–517. Data chunks are `MTU − 25` bytes (487 at MTU 512).

| Status line / error | Meaning |
| --- | --- |
| `Ready (MTU=…)` | Normal. |
| `Ready (MTU negotiation failed, using default)` | The phone kept the default MTU of 23 bytes. |
| Upload fails with "MTU … je příliš malé pro upload" | Chunks would be smaller than 64 bytes, so the app refuses to upload rather than send hundreds of tiny frames. Disconnect, toggle Bluetooth and reconnect. |

## Missing API keys

| Symptom | Fix |
| --- | --- |
| Search or categories show an error, everything else works | No valid key for that service. Add `GIPHY_API_KEY` / `KLIPY_API_KEY` to `BatohManager/local.properties` before building, or enter your own key in **Settings → API keys**. |
| **Lospec** source returns nothing | It is a Giphy pixel-art search and needs the Giphy key. |
| Key entered in Settings does not help | The field must contain the exact key; an empty field falls back to the key built into the APK. Check the provider's dashboard for rate limits or a revoked key. |

Keys are never required for the collection, import, editor, text banner, video conversion
or backpack upload.

## Upload rejected or failed

The upload card currently shows a generic *Upload failed*; the detailed reason is not yet
surfaced in the UI. The history records it as *Failed — not confirmed by backpack*, and the
BLE log shows which step stopped. The backpack's answers map as follows:

| Step | Backpack answer | What the app does | What to do |
| --- | --- | --- | --- |
| Start (Cmd 06) | status 1 | Sends the data. | — |
| Start (Cmd 06) | status 3 | Reports *This GIF is already on the backpack*, sends nothing, history ✓. | Nothing; it is stored. Not yet observed on hardware. |
| Start (Cmd 06) | status 2 | Fails: not enough free space. | Free space on the backpack. **Clear backpack contents** deletes everything — only if you mean it. Not yet observed on hardware. |
| Start (Cmd 06) | status 0, 4 or other | Fails: upload rejected. | Check the GIF is valid; reconnect and retry. |
| Start (Cmd 06) | no answer within 2 s | Fails and disconnects. | Retry. |
| Data chunk | ACK status ≠ 1 | Fails: chunk rejected, disconnects. | Retry; if it repeats at the same percentage, report it. |
| Data chunk | no ACK within 2 s | Fails, disconnects. | Move closer, retry. |
| End frame | status other than 1 or 3 | Fails: not confirmed. | Retry. |

Other causes of *Upload failed* before anything is sent:

- the file is not a GIF, is larger than 20 MiB, has more than 600 frames or is malformed;
- the session handshake did not get an answer (the log shows "Auth step … no/short
  response") — switch the backpack off and on;
- the connection dropped mid-transfer — the app cancels the transfer and does not claim
  success.

A failed upload is never shown as success. If the panel shows an unexpected picture after a
failed or cancelled upload, the backpack may have kept an earlier programme.

## Settings commands fail

"Could not complete: …" under **Display** means the backpack did not acknowledge the command
or did not answer within 2 s. An unanswered command closes the connection on purpose, so a
late answer cannot be mistaken for the next command's. Reconnect and use **Refresh display
status**.

## Import and library

| Symptom | Cause and fix |
| --- | --- |
| Shared GIF import failed | The file is malformed, too large or the sharing app revoked access. Try importing via **Import GIF** instead. |
| "Allow storage access to save the shared GIF" | On Android 9 and older, the app needs storage permission to write to `Pictures/GifPack`. |
| A GIF shows "Could not display the GIF" | The file is damaged. Download or import it again; delete the broken copy. |
| Upload of a large GIF takes long | Each chunk waits for its acknowledgement. A 453 KB GIF has uploaded successfully; the backpack's capacity is unknown. |
