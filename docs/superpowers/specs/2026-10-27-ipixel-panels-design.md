# Design: support for iPixel Color panels (`LED_BLE_*`) next to the backpack

Date: 2026-10-27 · Status: draft for owner approval

## Intent

GifPack today talks to exactly one kind of device: the iledcolor LED backpack, 64 × 64. The owner
also has panels from the **iPixel Color** family (advertised as `LED_BLE_xxxxxxxx`), including a
32 × 32 square panel and a 64 × 16 long panel that another family member uses with the vendor's own app. The goal
is that GifPack simply *works* with whichever supported panel is connected: it recognises the panel
family and its resolution, adapts the image / GIF / scrolling text to that resolution and uploads it
with the right protocol. The user never has to tell the app what panel it is.

This is about completeness of the app (the repository is public, others own these panels too),
not about replacing the vendor app for the other panel's user.

## Goals

1. Discover and connect to iPixel Color panels as well as the backpack.
2. Recognise family and resolution automatically.
3. Send to an iPixel panel: a still image, an animated GIF, and the scrolling text banner — each
   rendered at the panel's resolution.
4. Previews (library "as on the panel", editor, text banner) use the resolution of the connected
   panel, not a hard-coded 64 × 64.
5. **Prepared texts** (owner's request, mainly for the long panel): the user can save a list of
   texts in the app (with the banner options they were made with) and send any of them to the
   connected panel with one tap. Works for every panel family; the text is rendered at send time
   for the connected panel's resolution. *Designer's reading of the request:* a saved list in the
   app, not texts stored inside the panel.
6. Nothing that works with the backpack today changes behaviour.

## Non-goals

- Several panels connected at the same time, or broadcasting one image to many panels. One panel at
  a time — the one currently connected.
- iPixel extras: brightness, clock modes, timers, scoreboards, rhythm mode, schedules, saving into
  numbered slots, deleting slots, password. (Can come later; not in this work.)
- The iledcolor backpack protocol is not touched beyond what the generalisation needs.
- Other panel families (Divoom, iDotMatrix, …). The design should not make them hard, but they are
  not built.

## Anti-goals (would be a failure even if it "works")

- Any regression on the backpack (upload, cancel/retry, settings, capability gating, history).
- GifPack silently connecting to a panel the user did not mean (e.g. a second panel in the household while the
  user wants the backpack) and overwriting what it shows.
- GifPack destroying content stored by the vendor app on an iPixel panel. Uploads go to **slot 0**
  (display only, nothing saved), never to a numbered slot, and no delete/clear command is sent.
- Copying third-party code. The protocol is re-implemented from the description below; the Python
  library `lucagoc/pypixelcolor` is only the reference for byte layouts (it was read, not vendored).

## What is verified on hardware (2026-10-27)

Both panels were driven from a Linux PC with a small `bleak` script
(kept outside the repo). The owner confirmed both test images
displayed correctly (colours and an "F" in the top-left corner — no rotation or mirroring).

**GATT.** Service `0x00FA`: write characteristic `0000fa02-…`, notify characteristic `0000fa03-…`.
The panels also expose the JieLi OTA service `0x00AE` (`AE01`/`AE02`) like the backpack; it is not
used. No pairing, no handshake was needed. BlueZ negotiated MTU 23; writes with response of
244-byte pieces worked (stack splits them).

**Advertisement.** Name `LED_BLE_` + 8 hex digits. Manufacturer data, company key `0x5254`, value
7 bytes, e.g. `00 72 00 07 02 00 81` (32 × 32) and `00 72 00 07 01 00 83` (64 × 16). There is no
iledcolor `TBD`/`SL` signature, so the existing parser correctly ignores these devices.

**Device info.** Write `08 00 01 80 HH MM SS 00` (current time, language 0). Answer on FA03, e.g.
`0c 00 01 80 81 1b 31 00 00 01 00 01`. Byte 4 is the device type:

| byte 4 | type | size (w × h) |  | byte 4 | type | size |
|---|---|---|---|---|---|---|
| 0x80 | 0 | 64 × 64 | | 0x8A | 10 | 64 × 32 |
| 0x81 | 2 | 32 × 32 ✔ HW | | 0x8B | 11 | 96 × 32 |
| 0x82 | 4 | 32 × 16 | | 0x8C | 12 | 128 × 32 |
| 0x83 | 3 | 64 × 16 ✔ HW | | 0x8D | 13 | 96 × 32 |
| 0x84 | 1 | 96 × 16 | | 0x8E | 14 | 160 × 32 |
| 0x85 | 5 | 64 × 20 | | 0x8F | 15 | 192 × 32 |
| 0x86 | 6 | 128 × 32 | | 0x90 | 16 | 256 × 32 |
| 0x87 | 7 | 144 × 16 | | 0x91 | 17 | 320 × 32 |
| 0x88 | 8 | 192 × 16 | | 0x92 | 18 | 384 × 32 |
| 0x89 | 9 | 48 × 24 | | 0x93 | 19 | 448 × 32 |

Only 0x81 and 0x83 are hardware-verified; the rest comes from pypixelcolor. On both units the
**last byte of the manufacturer value equals this type byte**, so resolution is probably known from
the scan alone (2 units — treat as a hint, confirm with the info query after connecting).

**Upload (image or GIF).** One message per 12 KiB window:

```
len16LE( frame + 2 ) | frame
frame = kind 00 option | size32LE | crc32LE | tail | file bytes of this window
kind   = 02 for PNG (tail = 00 slot), 03 for GIF (tail = 02 slot)
option = 00 first window, 02 following windows
size, crc32 = of the whole file (standard CRC-32, little-endian)
slot   = 00 → display only (what we use)
```

The message is written in 244-byte pieces. After each window the panel notifies `05 00 02 00 cc`:
`cc` = 0/1 window accepted, 3 = whole file done. Verified: one-window PNG, answer `…03`.
GIF (kind 03) and multi-window uploads are verified on both panels (spike 2026-10-27): ~1.5 KB
animated GIFs → `05 00 03 00 03`; ~41 KB GIFs in 4 windows → code 1 after windows 0–2, code 3
after the last; 10–16 s at MTU 23. (Byte 2 of the answer echoes the kind: 02 PNG, 03 GIF.)

## Decisions made with the owner

- One panel at a time, the connected one.
- iPixel support = still image + animated GIF + scrolling text banner.
- Prepared texts in the app, especially for the long panel.
- **Connecting:** GifPack connects to an iPixel panel only after the user has picked it from the
  list in GifPack at least once; after that it is reconnected as the last-used panel. Send while
  disconnected and first use never auto-pick an iPixel panel. The backpack keeps today's behaviour.
- Implementation is done by subagents (one per task of the plan).
- Backpack stays fully as it is.

## Calls made by the designer (owner may override)

- **Text banner on iPixel = rendered GIF**, reusing `TextBannerRenderer` generalised to any
  width × height (font height derived from panel height), not the panel's native text command. Same
  look across families, one code path, works on the 64 × 16 panel where text matters most.
- **Still images are sent as a 1-frame GIF** for both families unless GIF fails for stills on
  iPixel, in which case PNG (kind 02, verified) is used for single-frame images. The builder decides
  after testing kind 03.
- **Hardware spike first.** The plan starts by verifying, with the PC `bleak` scripts, a kind-03
  GIF upload and a multi-window (> 12 KiB) upload on both panels, before Android work on the iPixel
  path. If GIF does not work, the plan stops and comes back to the owner (fallbacks: PNG frames,
  the panel's own text command) instead of guessing.
- **Accepted iPixel types.** All 20 types of the table are accepted; only 0x81 and 0x83 are marked
  verified, the others show "untested size" on the device card. An unknown type byte → the panel is
  listed but not offered for sending. The info reply wins over the scan hint when they disagree
  (logged).
- **Backpack-only send paths on iPixel.** Hidden on iPixel: editor speed/light/effect, "send as
  sequence" (multi-programme playlist), effect/playlist tests, built-in programmes. Kept: the GIF
  chain (it produces one concatenated GIF, which is sent normally) and the test image (rendered at
  the panel's size).
- **Library format unchanged.** Library items stay as they are (originals, existing 64 × 64 copies);
  conversion to the panel's size happens at send time. The editor, the "as on the panel" preview and
  the text banner use the connected panel's size, or the last-used panel's size when disconnected
  (64 × 64 if none). A banner saved to the library is saved at that size.
- **Which panel to connect to.** Keep "reconnect to the last used panel". Add a short list of
  nearby supported panels (family, resolution, name, signal) to choose from / switch to. On first
  use with more than one supported panel advertising, ask instead of taking the first one. This is
  what protects another family member's panel (anti-goal 2).
- **Fit for non-square panels.** Default for a GIF whose aspect differs strongly from the panel
  (e.g. square GIF → 64 × 16) is *fit with black bars*, with the existing editor offering crop.
  Centre-crop of a square GIF to 64 × 16 would throw away 75 % of it.
- **UI naming.** The "Backpack" screen becomes the "Panel" screen; backpack-only sections
  (built-ins, rotation, password status, brightness, clock) are shown only for the backpack, the same
  capability gating the app already uses.

## Constraints

- Kotlin / Compose / Hilt, existing module layout (`core/data/bluetooth`, `core/conversion`,
  `feature/backpack`); BLE protocol code stays JVM-unit-testable like the backpack's.
- Pure Android BLE, no new third-party BLE library.
- The repository is public and English; Czech strings in `res/values` alongside English.
- `BackpackTransferManager` owns one transaction at a time; the iPixel path must keep that rule.
- Hardware: owner has one 32 × 32 and one 64 × 16 iPixel panel and the backpack; one Android test phone.

## Left to the builder

- How to abstract "panel family" (interface/sealed type, where the GATT UUIDs live, how
  `BluetoothLeManager` discovers either service set).
- Whether to request a larger MTU on Android for iPixel and the exact piece size.
- Exact per-panel limits (GIF size / frame count). Start conservative and log; the backpack's 453 KB
  / 600-frame guard does not transfer.
- Upload state mapping into the existing `Preparing → … → Success | Error | Cancelled` flow and
  `UploadHistory`.
- Tests: frame builder and advertisement parser for iPixel as JVM tests with the bytes above;
  generalised renderer/processor tests for 32 × 32 and 64 × 16; a hardware check of GIF and a
  multi-window (> 12 KiB) upload on both panels before calling it done.
- Docs: a new section in `docs/ble-protocol.md` and `docs/device-capabilities.md` for the iPixel
  family, with the same confidence tags.
