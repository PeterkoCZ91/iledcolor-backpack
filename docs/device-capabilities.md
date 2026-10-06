# iledcolor LED backpack: device capabilities

The backpack has **no GATT command** that reports its panel size, firmware version or feature
set. All of this is in a short binary record inside its **BLE advertisement** (scan record). Both
the manufacturer app and GifPack read this record before or while connecting, and decide
from it which controls to offer.

The confidence tags are the same as in [ble-protocol.md](ble-protocol.md#1-confidence-levels):
**[HW]** verified on hardware, **[CAP]** verified against captured traffic, **[DER]** derived from
the manufacturer app and not yet hardware-tested.

In examples, devices are referred to by placeholders such as `iledcolor-XXXX` (advertised name)
and `AA:BB:CC:DD:EE:FF` (address).

---

## 1. Finding the record

The app scans the **raw scan record bytes** (advertisement plus scan response, exactly as Android
returns them) for one of two 5-byte signatures. It ignores the AD structure boundaries. The
record starts at the signature, and the fields below are counted from its first byte (`b[0]`).
At least 17 bytes are needed. [HW]

| Signature | Variant | screenTypeId |
|---|---|---|
| `01 54 42 44 nn` (`01` + ASCII `TBD` + nn, where nn is 1–21) | current devices | `0x544244nn` |
| `01 53 4C 00 05` (`01` + ASCII `SL` + `00 05`) | "old" device | `0x534C0005` |

If neither signature is present, the device is not treated as a backpack.

## 2. Field layout

| Field | Current (`TBD`) | Old (`SL`) | Notes |
|---|---|---|---|
| screenTypeId | `b[1..4]` | `b[1..4]` | u32 |
| height | `b[5..6]` | `b[5..6]` | u16, pixels |
| width | `b[7..8]` | `b[7..8]` | u16, pixels |
| colour type | `b[9]` | `b[9]` | see §4 |
| (unused) | `b[10]` | — | not read by either app |
| versionCode | `b[11..12]` | `b[10..11]` | u16, firmware version |
| customerId | `b[13..14]` | `b[12..13]` | u16 |
| funCode | `b[15..16]` | `b[15..16]` | u16 feature bit field, §3 |

All multi-byte values are big-endian. Height comes **before** width. The field offsets are [DER]
from the manufacturer app. They were confirmed on hardware by reading plausible values (64×64,
colour type 3) from the tested unit [HW].

Example (synthetic, taken from the app's unit tests):

```
02 01 06                         flags AD structure
15 FF                            manufacturer-specific AD header
01 54 42 44 05                   signature "TBD", nn = 5
00 40  00 40                     height 64, width 64
03                               colour type 3 (RGB888)
00                               unused
00 1F                            versionCode 31
00 02                            customerId 2
01 05                            funCode 0x0105 = time + GIF + rotation
```

## 3. funCode bits

| Bit | Feature | Effect in the manufacturer app [DER] |
|---|---|---|
| `0x0001` | Time | Sends time sync (0x0C) after connecting; offers clock items (type 3) |
| `0x0002` | Partitions | Allows multi-item programmes |
| `0x0004` | GIF file | GIFs are uploaded as a file (item type 6). Without this bit they are decoded into raw frames (type 2). |
| `0x0020` | Border | Offers frame and border styles |
| `0x0040` | Password | Offers the 0x0E / 0x0F password commands |
| `0x0100` | Rotation | Offers rotation and mirror (0x0B) |
| `0x0200` | (unknown) | Purpose not determined |
| `0x0400` | GIF text | Text is uploaded as a rendered GIF file (item type 7) instead of raw frames |
| `0x0800` | Left unit | For paired "linkage" displays (0x11) |
| `0x1000` | Right unit | For paired "linkage" displays (0x11) |

## 4. Colour type

The colour type sets the pixel format of raw bitmaps (item types 1 and 2). It does not matter for
GIF-file items, which the firmware decodes itself.

| Value | Pixel format | Status |
|---|---|---|
| 0 | 1 bit per pixel, row-major, MSB first, threshold on luminance | [DER] |
| 1 | 1 bit per pixel, column-major | [DER] |
| 2 | 3-bit RGB, two pixels per byte (high nibble first) | [DER] |
| 3 | **RGB888**, 3 bytes per pixel, row-major, top-left first | [CAP]: a 64×64 static bitmap in captured traffic is exactly 64 × 64 × 3 bytes |
| 4 | RGB565, 2 bytes per pixel, big-endian | [DER] |
| 5, 6, 7 | RGB332, 1 byte per pixel | [DER] |

## 5. versionCode thresholds

| Condition | Effect in the manufacturer app [DER] |
|---|---|
| `versionCode >= 6` | Brightness (0x09) offered |
| `versionCode >= 13` | Extended text-effect codes (vertical scroll and so on; see [ble-protocol.md §9.5](ble-protocol.md#95-text-effect-codes-der)) |
| `versionCode >= 30` | State query (0x10) sent automatically after connecting |
| "old" device (`SL`) | Neither time sync nor 0x10 is sent after connecting |

## 6. What GifPack gates on the record

| Feature | Rule in GifPack | Matches the manufacturer? |
|---|---|---|
| Rotation and mirror (0x0B) | Controls are shown and 0x0B is sent **only if funCode has `0x0100`** | Yes |
| Time sync (0x0C) on connect | Sent **only if funCode has `0x0001` and the device is not old**. If no record is known, it is sent anyway. | Yes, apart from the unknown-record fallback |
| State query (0x10) | Always sent after connecting | No: the manufacturer requires `versionCode >= 30`. It works on the tested unit (versionCode 14). |
| Brightness (0x09) | Always shown; `versionCode >= 6` is only listed as a feature | Mostly: the manufacturer hides it below version 6 |
| Built-in programmes (item type 5) | Shown only if the 0x0D count is above 0. Uses the advertised width and height, falling back to 64×64. | Yes |
| Password (0x0E / 0x0F) | Listed as a feature. The commands are not implemented. | — |
| GIF upload | Always uses item type 6 at 64×64. funCode `0x0004` is not checked. | Partly: the manufacturer would fall back to raw frames without `0x0004` |

The app caches the raw record per device address, so later direct connections do not need a new
scan. If the cache is empty, `connectToTarget` first runs a filtered low-latency scan for that
address (at most 3 s), then connects. The parsed record is written to the in-app BLE log as
`ADV iledcolor-XXXX: funCode=0x…, version=…, size=…`. [HW]

## 7. The tested unit

These values were read from the advertisement of the backpack used during development [HW]:

| Field | Value | Meaning |
|---|---|---|
| Variant | `TBD` (not old) | |
| Size | 64 × 64 | |
| Colour type | 3 | RGB888 |
| versionCode | 14 | ≥ 6 (brightness) and ≥ 13 (extended effects); < 30 |
| funCode | `0x0044` | `0x0004` GIF file + `0x0040` password. **No** time (`0x0001`) and **no** rotation (`0x0100`). |
| customerId | parsed as `0x0401` (1025) | Meaning unconfirmed. This field could be offset by one byte on this unit. |

### 7.1 Command status on the tested unit

| Command | Result on the tested unit | Status |
|---|---|---|
| Upload (0x06 → chunks → 0x01), GIF item type 6 | Small and 453 KB GIFs uploaded; cancel and retry work | **[HW]** |
| 0x06 status 3 "already present" | Not observed yet | [DER] |
| 0x09 brightness | 7 → 6 → 7 confirmed by read-back | **[HW]** |
| 0x0A screen on/off | on → off → on confirmed by read-back | **[HW]** |
| 0x10 state query | Answers (screen and brightness fields correct) despite versionCode < 30 | **[HW]** |
| 0x0B rotation / mirror | ACKed without error, **no state change**. This matches the missing `0x0100` bit, and the app now hides the controls. | [HW], unsupported |
| 0x0C time sync | ACKed with a success status by an earlier app build; no observable effect. Matches the missing `0x0001` bit, and the app now skips it. | [HW] ACK only |
| 0x0D built-in count | Answers 0 (GifPack query and captured manufacturer session) | [HW] |
| Item type 5 (built-in programme) | Not testable (count is 0) | [DER] |
| 0x02 clear all programmes | Never sent (destructive) | [DER] |
| 0x0E / 0x0F password | Not implemented; funCode says it is supported | [DER] |
| 0x04 music rhythm, 0x11 linkage | Not implemented | [DER] |
| JieLi authentication on AE00 | Completes on every connection | **[HW]** |

Other units may advertise different capabilities. If you have one, the `ADV` line in the app's
BLE log shows its values. Reports of other funCode and versionCode combinations would help
confirm the rows marked [DER].
