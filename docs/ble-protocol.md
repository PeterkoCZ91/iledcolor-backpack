# iledcolor LED backpack: BLE protocol

This document describes the Bluetooth Low Energy protocol of the iledcolor-brand 64×64 LED
backpack. It is what GifPack uses to talk to the panel. The protocol was worked out for
interoperability with hardware the author owns. Most of it has not been documented publicly
before.

The protocol is described in clean-room style: behaviour and byte layouts only. No code from the
manufacturer app is reproduced.

Related document: [device-capabilities.md](device-capabilities.md) describes the advertisement
record that tells the app what a particular unit supports.

---

## Contents

1. [Confidence levels](#1-confidence-levels)
2. [Verification status](#2-verification-status)
3. [GATT layout](#3-gatt-layout)
4. [Connection sequence](#4-connection-sequence)
5. [Frame format](#5-frame-format)
6. [Uploading data](#6-uploading-data)
7. [Responses and status codes](#7-responses-and-status-codes)
8. [Timeouts and flow control](#8-timeouts-and-flow-control)
9. [Programme payload format](#9-programme-payload-format)
10. [Command catalogue](#10-command-catalogue)
11. [Pitfalls and corrected assumptions](#11-pitfalls-and-corrected-assumptions)
12. [Open questions](#12-open-questions)

---

## 1. Confidence levels

Every statement in this document has one of three confidence levels:

| Tag | Meaning |
|---|---|
| **[HW]** Verified on hardware | GifPack sent this to a real backpack, and the result was observed (an ACK, a state read-back, or a completed upload). |
| **[CAP]** Verified against captured traffic | Matches BLE traffic recorded from the manufacturer app talking to the same backpack, byte for byte. |
| **[DER]** Derived from the manufacturer app | Comes from analysing how the manufacturer app behaves. It has not yet been tested against hardware. |

If a statement has more than one tag, each tag applies independently.

All multi-byte integers are **big-endian** unless stated otherwise. Hex bytes are written
`54 06 00 0D`. `d[i]` means byte *i* of a received notification, counting from 0.

---

## 2. Verification status

The tested unit is a 64×64 RGB backpack that advertises `funCode 0x0044` and `versionCode 14`
(see [device-capabilities.md](device-capabilities.md)).

| Area | Status | Notes |
|---|---|---|
| GATT services A950 / AE00, notifications, MTU request | [HW] | The app connects and operates reliably. The test phone negotiated MTU 517. |
| JieLi authentication on AE00 | [HW] | Done before the first command on each connection. Whether the panel requires it is untested. |
| Frame format and 16-bit checksum | [HW] [CAP] | 114 of 114 manufacturer frames are reproduced exactly. 160 of 160 captured frames have a valid checksum. |
| Data chunking, end frame, ACK flow | [HW] [CAP] | Small and 453 KB GIF uploads, including cancel and retry, completed on hardware. |
| Programme payload (24 B header, CRC-32C file ID, item type 6) | [HW] [CAP] | 7 of 7 captured payloads are rebuilt byte for byte. Uploads were accepted on hardware. |
| Cmd 06 status 1 (send data) | [HW] [CAP] | |
| Cmd 06 status 3 (already present, skip data) | [HW] | Re-sending an identical programme (same file ID) answered `54 06 00 03 03 00 60`; no chunks were needed. |
| Cmd 06 status 2 (insufficient space) | [DER] | Implemented in the app but not yet observed. |
| 0x09 brightness | [HW] | A round trip 7 → 6 → 7 was confirmed by reading the state back with 0x10. |
| 0x0A screen on/off | [HW] | A round trip on → off → on was confirmed with 0x10. |
| 0x10 state query | [HW] | Answers on the tested unit, even though its versionCode is below 30. |
| 0x0B rotation / mirror | [HW] partial | The unit ACKed without error, but its state did not change. The unit does not advertise rotation support. |
| 0x0C time sync | [HW] partial | The unit ACKed with a success status. No visible effect, and the unit does not advertise time support. |
| 0x0D built-in programme count | [HW] [CAP] | GifPack's own query on the tested unit: `54 0D 00 03 00 00 64` → `54 0D 00 04 00 00 00 65` (count 0). |
| Item type 5 (built-in programme) | [DER] | Not testable: the unit reports 0 built-in programmes. |
| 0x02 clear all programmes | [DER] | Implemented in the app but never sent to hardware (it is destructive). |
| 0x0E / 0x0F password | [DER] | Not implemented in the app. |
| 0x04 music rhythm, 0x11 linkage | [DER] | Not implemented in the app. |
| 0x03 / 0x08 | [HW] | Several programmes in turn (playlist). See §10.13. |
| 0x07 | [CAP] | Not needed for uploads. See §10.13. |

---

## 3. GATT layout

All UUIDs use the Bluetooth base UUID: `0000xxxx-0000-1000-8000-00805f9b34fb`.

| Service | Characteristic | Direction | Use |
|---|---|---|---|
| **A950** (application) | **A951** | app → device | Single-frame commands, including the first frame of an upload. [HW] [CAP] |
| | **A952** | app → device | Upload data chunks, the end-of-transfer frame (`54 01 …`), and music-rhythm frames. [HW] [CAP] |
| | **A953** | device → app (notify) | All application responses and ACKs. [HW] [CAP] |
| **AE00** (JieLi RCSP) | **AE01** | app → device | JieLi authentication and RCSP messages. [HW] |
| | **AE02** | device → app (notify) | JieLi authentication and RCSP responses. [HW] |

**Write type.** Both the app and the manufacturer app write with the characteristic's
**default write type**, which Android derives from the characteristic properties. Neither forces
a write type. A951 and A952 are expected to be write-without-response [DER]. On a
write-without-response characteristic, GifPack treats a missing Android write callback
after 500 ms as success [HW].

AE00 belongs to the JieLi Bluetooth SoC stack. In the manufacturer app, its OTA/RCSP library
handles AE00, and none of the application commands below are sent on it. [DER]

---

## 4. Connection sequence

### 4.1 What GifPack does [HW]

1. **Capability record.** If no advertisement is cached for the target address, the app runs a
   short filtered scan (up to 3 s, low latency) to capture the scan record. Panel size, firmware
   version and feature flags are only available there. See
   [device-capabilities.md](device-capabilities.md).
2. **Connect.** The app calls `connectGatt(autoConnect = false, transport = LE)`. Forcing LE
   transport avoids stale-cache errors on some phones.
3. **Discover services.**
4. **Enable notifications one at a time.** The app enables A953 first, by writing the CCCD
   `0x2902` = `01 00`. When `onDescriptorWrite` confirms it, the app enables AE02. Android
   allows only one GATT operation in flight, so each step waits for the previous callback.
5. **Request MTU 512.** The app requests MTU 512 after the last descriptor write. If AE00 is
   missing, it requests the MTU right after A953. Whatever value `onMtuChanged` reports is then
   used for chunk sizing. The test phone reported **517**.
6. **JieLi authentication** on AE01/AE02 (§4.3). This happens once per connection, before the
   first application command.
7. **Post-connect commands** on A951, in this order:
   - **0x0C time sync.** Sent only if the unit advertises time support (funCode bit `0x0001`) and
     is not an "old" device. If no advertisement is known, the app sends it anyway.
   - **0x0D built-in programme count.** Optional, with a 1.5 s timeout. If there is no answer,
     the built-in programme feature stays hidden.
   - **0x10 state query.** Fills in the brightness, screen and rotation controls.

### 4.2 What the manufacturer app does, for comparison [DER]

- It enables notifications on **every** characteristic of both A950 and AE00, one at a time,
  then requests MTU 512.
- After the MTU change, the JieLi SDK runs authentication and an RCSP "get target info" request
  by itself. The application layer only learns about this from a "ready" event.
- After that event, for devices that are not old: it sends time sync (0x0C) only if funCode
  bit `0x0001` is set, and the 0x10 state query only if `versionCode >= 30`.
- It sends 0x0F with six zero bytes to check whether a password is set (§10.10).

GifPack differs in a few ways. It also sends 0x10 on units below versionCode 30, which works
on the tested unit. It also sends 0x0D. It does not check for a password.

### 4.3 JieLi authentication (application-level view) [HW]

The backpack uses a JieLi Bluetooth SoC. JieLi devices use a challenge–response handshake on
AE01/AE02, and other open-source projects have implemented it too. GifPack carries a port of
such an implementation (`JieLiAuth.kt`, which credits its source). The cipher and key material are
not described here. At the message level, the exchange is:

| Step | Direction | Bytes |
|---|---|---|
| 1 | app → AE01 | `00` followed by 16 random bytes (the app's challenge) |
| 2 | AE02 → app | 17 bytes: the device's answer to that challenge |
| 3 | app → AE01 | `02 70 61 73 73` (`02` + ASCII `pass`) |
| 4 | AE02 → app | 17 bytes: the device's challenge |
| 5 | app → AE01 | 17 bytes: the app's encrypted answer to the device's challenge |
| 6 | AE02 → app | `02 70 61 73 73` means authentication succeeded |
| 7 | app → AE01 | RCSP "get target info": `FE DC BA C0 03 00 06 00 FF FF FF FF 00 EF` |

GifPack is deliberately lenient:

- It computes the expected device answer in step 2 and logs a mismatch, but continues anyway.
- It continues if the step 6 answer is unexpected or missing.
- It ignores the step 7 answer.

Each step has a 3 s timeout. Step 7 has a 2 s timeout. The app waits 200 ms after step 6 and again
after step 7. It aborts only when step 2 or step 4 has no answer or an answer shorter than 17
bytes.

Open point: the panel might accept A951/A952 commands without this handshake. That has not been
tested.

---

## 5. Frame format

Every application message on A951, A952 and A953 uses the same frame. [HW] [CAP]

```
54 | cmd | len (u16) | payload (N bytes) | checksum (u16)
```

| Offset | Size | Field |
|---|---|---|
| 0 | 1 | Header, always `0x54` |
| 1 | 1 | Command (opcode) |
| 2 | 2 | `len = N + 2` (payload plus checksum), big-endian |
| 4 | N | Payload |
| 4+N | 2 | `checksum = (sum of all preceding bytes) & 0xFFFF`, big-endian |

The total frame length is `N + 6`, which also equals `len + 4`.

The checksum is a plain **16-bit byte sum**, not a CRC. It covers the header, the opcode, the
length field and the payload.

### 5.1 Worked examples

Brightness level 7 (§10.4). The payload is `04 00 00 00 00 00 00 00 00` (9 bytes):

```
54 09 00 0B 04 00 00 00 00 00 00 00 00 00 6C
            └──────── payload ─────────┘ └cs┘
len = 9 + 2 = 0x000B
sum = 0x54 + 0x09 + 0x00 + 0x0B + 0x04 = 0x006C
```

Upload start, captured from the manufacturer app [CAP]:

```
54 06 00 0D  BB DA ED 3D 00 00 30 2E 00 00 00  03 84
sum = 54+06+0D+BB+DA+ED+3D+30+2E = 0x0384
```

The checksum's high byte is `03` here. This example shows why a one-byte checksum is wrong (§11).

More frames, all verified:

```
54 01 00 03 01 00 59                 end of transfer, and also the device's echo of it
54 10 00 03 00 00 67                 state query
54 0D 00 03 00 00 64                 built-in programme count query
54 0A 00 0B 00 00 00 00 00 00 00 00 00 00 69   screen OFF
54 0A 00 0B 01 00 00 00 00 00 00 00 00 00 6A   screen ON
```

### 5.2 Validating received frames

The manufacturer app does not check the checksum of received frames [DER]. GifPack does
check it. It accepts a notification only if the header is `0x54`, `len == size − 4`, and the
checksum matches. There is one exception: the data-chunk ACK (§7.2) declares `len = 5`, which
does not count its own two checksum bytes. The app accepts this firmware quirk only for 11-byte
`54 00 00 05 …` frames, and the checksum must still be correct. [HW] [CAP]

---

## 6. Uploading data

Anything the panel displays is uploaded as a **programme payload** (§9). An upload is one
transaction of three phases, strictly stop-and-wait. [HW] [CAP]

```
app  → A951   54 06 …  upload header (file ID + size)
dev  → A953   54 06 00 03 ST 00 cs        ST = 1: send data
app  → A952   54 00 …  chunk 0
dev  → A953   54 00 00 05 00 00 00 00 01 cs16   chunk 0 OK
app  → A952   54 00 …  chunk 1
dev  → A953   54 00 00 05 00 00 00 01 01 cs16
…
app  → A952   54 01 00 03 01 00 59        end of transfer
dev  → A953   54 01 00 03 01 00 59        echo means success
```

No other frames are needed. Once the echo arrives, the panel starts playing the new programme.
The manufacturer app sends nothing further after it. [CAP]

### 6.1 Upload header: Cmd 0x06 (on A951)

Payload, 11 bytes:

| Offset | Size | Field |
|---|---|---|
| 0 | 4 | File ID: the first 4 bytes of the programme payload, which are its CRC-32C (§9.1) |
| 4 | 4 | Total programme payload length in bytes (u32) |
| 8 | 3 | `00 00 00` |

The payload has **no slot or type byte**. [HW] [CAP]

Example from a captured 35,984-byte GIF programme with file ID `C8163A1B` [CAP]:

```
54 06 00 0D  C8 16 3A 1B  00 00 8C 90  00 00 00  02 B6
```

### 6.2 Data chunks: Cmd 0x00 (on A952)

The programme payload is cut into consecutive chunks of **n = MTU − 25** bytes. The last chunk is
simply shorter: there is no padding and no "last" flag. [HW] [CAP]

| Offset | Size | Field |
|---|---|---|
| 0 | 1 | `0x54` |
| 1 | 1 | `0x00` |
| 2 | 2 | `len = 4 + 2 + chunkLen + 2` |
| 4 | 4 | Chunk index (u32), starting at 0 |
| 8 | 2 | `chunkLen` (u16) |
| 10 | chunkLen | Payload bytes |
| 10+chunkLen | 2 | Checksum (16-bit sum of all preceding bytes) |

The 25 bytes of margin come from 12 bytes of chunk framing (`54 00 len idx chunkLen cs`) plus 13
bytes of ATT and application headroom. The whole frame is therefore at most MTU − 13 bytes.

| Negotiated MTU | chunkLen | Frame size |
|---|---|---|
| 512 | 487 (`01 E7`) | 499 |
| 517 (observed on the test phone) | 492 (`01 EC`) | 504 |
| 247 | 222 | 234 |
| 185 | 160 | 172 |

GifPack refuses to upload if `chunkLen < 64`.

Synthetic example: chunk 0 carrying the 3 bytes `AA BB CC`:

```
54 00 00 0B  00 00 00 00  00 03  AA BB CC  02 93
```

From the captured 35,984-byte upload at MTU 512 (35,984 = 73 × 487 + 433) [CAP]:

```
chunk 0  : 54 00 01 EF  00 00 00 00  01 E7  C8 16 3A 1B 01 00 …(487 B)  cs16
…
chunk 73 : 54 00 01 B9  00 00 00 49  01 B1  …(433 B)  cs16
```

In total, that upload is 76 frames: one header, 74 chunks and one end frame.

### 6.3 End of transfer: Cmd 0x01 (on **A952**)

`54 01 00 03 01 00 59` is sent on the **data** characteristic, after the last chunk ACK. The
device echoes the same seven bytes. A `d[4]` value of `01` means the upload succeeded. [HW] [CAP]

### 6.4 Cancelling

No abort command is known. To cancel an upload part-way through, GifPack disconnects. A
retry starts a fresh connection and re-uploads from Cmd 06. This was tested on hardware: a
453 KB upload was cancelled mid-transfer and then retried successfully. [HW]

---

## 7. Responses and status codes

All responses arrive as notifications on **A953**.

### 7.1 Header and command ACK (7 bytes)

`54 cmd 00 03 ST 00 cs`. The status is `d[4]`.

| ST | Meaning after Cmd 06 (upload header) | Meaning after a settings command |
|---|---|---|
| `0` | **Error** ("please try again"). This is what a frame with a bad checksum gets. [HW] [CAP] | Error |
| `1` | OK: start sending data chunks. [HW] [CAP] | OK [HW] |
| `2` | Error: insufficient space. [DER] | Error |
| `3` | OK, no data needed: the file is already on the device. The upload is complete. [HW] | OK [DER] |
| `4` | Error. [DER] | Error |

GifPack accepts `1` or `3` as success for settings commands and for the end-of-transfer
echo. The manufacturer app requires exactly `1` in the end-of-transfer echo. [DER]

Commands whose 7-byte reply carries something other than a status are 0x0D (a count), 0x0E and
0x0F (password results). See their sections in §10.

### 7.2 Data-chunk ACK (11 bytes)

`54 00 00 05 idx(4) ST cs(2)`. The status is `d[8]`. [HW] [CAP]

| d[8] | Meaning |
|---|---|
| `1` | Chunk accepted: send the next chunk. |
| anything else | Error. The manufacturer app treats `0`, `2` and `3` as errors and ignores other values. GifPack treats any value other than `1` as an error. |

The length field says `5` even though 7 bytes follow it (a firmware quirk, §5.2). Captured
example [CAP]:

```
54 00 00 05  00 00 00 07  01  00 61      chunk 7 OK
```

GifPack also checks that the ACK's index matches the chunk it just sent.

### 7.3 Data-carrying responses

| Cmd | Layout | Section |
|---|---|---|
| 0x0D | 7 bytes: count in `d[4]`. 8 bytes: count in `d[4] << 8 \| d[5]` | §10.9 |
| 0x10 | Variable length, state bytes from offset 4 | §10.8 |

---

## 8. Timeouts and flow control

The protocol is **strictly stop-and-wait**. The next frame is written only after the previous
frame's ACK arrives. There are no deliberate delays between frames. On the manufacturer side, a
chunk took about 60–150 ms per round trip in the captures. [CAP]

| Item | GifPack | Manufacturer app [DER] |
|---|---|---|
| Control-command response | 2 s (1.5 s for 0x0D) | 6 s per step |
| Chunk ACK | 2 s per chunk | 6 s, timer restarted after each chunk |
| End-of-transfer echo | 2 s | 6 s |
| Retry on error or timeout | None. The app disconnects to drop the uncertain session, and the user may retry. | None. The task is aborted. |
| GATT write callback | 2 s; 500 ms for write-without-response, then assumed sent | — |
| Gap between queued background tasks | — | 100 ms |

Concurrency rules in GifPack [HW]:

- Only one GATT write is in flight at a time, as Android requires.
- Only one control command waits for a response at a time. A response is matched to it by
  **opcode** (`d[1]`) and must pass the checksum check. Notifications for other opcodes, and
  late answers after a timeout, are ignored.
- If a settings command gets no answer, the app disconnects. Otherwise a late ACK could be
  mistaken for the answer to a retry.

The manufacturer app does not compare the ACK's opcode with the command it sent. Worse, any
notification it does not recognise aborts the running task. [DER]

---

## 9. Programme payload format

The bytes sent through Cmd 06 and the data chunks make up a **programme**: a 24-byte header
followed by one or more items. [HW] [CAP]

### 9.1 Programme header (24 bytes)

| Offset | Size | Field |
|---|---|---|
| 0 | 4 | **File ID** = CRC-32C of `payload[24 … end]`, stored big-endian |
| 4 | 1 | Number of partitions (items), 1–4 |
| 5 | 3 | `00 00 00` |
| 8 | 4 × 4 | Offset of item *i* (u32), counted from offset 24. **The first item always has offset 0, and unused slots are 0.** |

**CRC-32C** is the Castagnoli CRC: polynomial `0x1EDC6F41` (reflected `0x82F63B78`), init
`0xFFFFFFFF`, reflected input and output, final XOR `0xFFFFFFFF`. The check value of
`"123456789"` is `0xE3069283`. The CRC starts at **offset 24**: it does not cover the whole file,
and it does not cover the header. For a single-item programme, it equals the CRC of the item.
[HW] [CAP]

### 9.2 Item (22-byte header plus data)

| Offset in item | Size | Field | GIF upload value |
|---|---|---|---|
| 0 | 2 | Rectangle x (left) | 0 |
| 2 | 2 | Rectangle y (top) | 0 |
| 4 | 2 | Rectangle width | 64 |
| 6 | 2 | Rectangle height | 64 |
| 8 | 3 | Reserved, `00 00 00` | `00 00 00` |
| 11 | 1 | **Item type** (see below) | **6** |
| 12 | 2 | Number of data frames | 1 |
| 14 | 1 | Effect | 0 |
| 15 | 1 | Speed | 100 (`0x64`) |
| 16 | 1 | Stay time | 4 for a GIF file (default elsewhere: 50) |
| 17 | 1 | Frame or border style (0 = none) | 0 |
| 18 | 1 | Item brightness | 100 (`0x64`) |
| 19 | 3 | Reserved, `00 00 00` | `00 00 00` |
| 22 | … | Data frames, back to back | the **unmodified GIF file** |

The GIF upload values are [HW] [CAP]. The meaning of the stay-time and border fields is [DER].

Single-byte fields are truncated to 8 bits.

**Item types:**

| Type | Content | Status |
|---|---|---|
| 1 | Static bitmap ("graffiti"): one frame of raw pixels in the panel's colour format (RGB888 on the tested unit, so 64 × 64 × 3 = 12,288 bytes) | [CAP] |
| 2 | Frame-by-frame animation: N raw bitmaps, followed by a u16 delay per frame | [DER] |
| 3 | Clock: a 14-byte configuration. The firmware draws the clock. **Not supported by the tested unit:** an experimental 60-byte clock programme was accepted and confirmed, but the panel showed a white screen with coloured garbage instead of a clock. | [DER] [HW] |
| 5 | **Built-in programme**: one 8-byte frame, `00 00 00 00` + programme ID (u32). It plays a programme already stored in the firmware. | [DER] |
| 6 | **GIF file**: the whole GIF file as a single frame. The firmware decodes it. | [HW] [CAP] |
| 7 | Text rendered by the app into a GIF file (same layout as type 6) | [DER] |

The panel never receives text as characters. The manufacturer app always renders text into
pixels or a GIF first. [DER]

### 9.3 GIF programmes (what GifPack sends) [HW] [CAP]

- The GIF must be exactly **64×64**. GifPack converts other sizes before upload. The
  manufacturer app rescales with a palette-preserving resize.
- The file is sent byte for byte, extension blocks included, ending at the GIF trailer `3B`.
  Nothing is trimmed or re-encoded.
- The programme has one partition, with all offsets zero.

Example header plus item for the captured file `C8163A1B` [CAP]:

```
C8 16 3A 1B  01 00 00 00  00×16                       ← 24-byte programme header
00 00 00 00 00 40 00 40  00 00 00  06  00 01           ← rect, reserved, type 6, 1 frame
00 64 04 00 64  00 00 00                               ← effect 0, speed 100, stay 4, border 0, brightness 100, reserved
47 49 46 38 39 61 …  3B                                ← "GIF89a" … trailer
CRC-32C(payload[24:]) = 0xC8163A1B
```

### 9.4 Built-in programmes (item type 5) [DER]

When the device reports a built-in programme count above 0 (0x0D, §10.9), the manufacturer app
can play programme *k* (1 ≤ *k* ≤ count) by uploading a normal programme with a single type-5 item:

```
item = 00 00 00 00  00 40 00 40  00 00 00  05  00 01  <effect> <speed> 04 00 <brightness>  00 00 00
       00 00 00 00  00 00 00 0k
```

It is sent like any other upload: Cmd 06, then chunks, then the end frame. Nothing new is stored
in the firmware. GifPack implements this, but shows it only when 0x0D reports a count above
0. The tested unit reports 0, so the feature has not been hardware-tested.

### 9.5 Text effect codes [DER]

These values go in the item's effect byte. The manufacturer app uses this table when
`versionCode >= 13`:

| Effect | Code |
|---|---|
| Static | 0 |
| Move left / right / up / down | 1 / 2 / 3 / 4 |
| Snowflakes | 5 |
| Scroll | 6 |
| Laser | 7 |
| Vertical scroll | 8 |
| Continuous left / right | 9 / 10 (sent as 1 / 2 with stay time 0) |

On older firmware, codes 8–10 are shifted: there is no vertical scroll, and continuous left/right
are 8/9.

---

## 10. Command catalogue

All commands below are written to **A951** and answered on **A953** with a 7-byte ACK (§7.1),
unless stated otherwise. Payload sizes are given in bytes. The examples include the checksum.

### Summary

| Cmd | Name | Payload | Response | Status |
|---|---|---|---|---|
| 0x00 | Data chunk (A952) | §6.2 | 11-byte chunk ACK | [HW] [CAP] |
| 0x01 | End of transfer (A952) | `01` | Echo | [HW] [CAP] |
| 0x02 | Clear all programmes | `00` | ACK | [DER] |
| 0x03 | Playlist item header | N, i, fileId, length, playCount | like 0x06 | [HW] |
| 0x04 | Music rhythm (A952, no ACK) | `00 01` / `01 style mags×48` | none | [DER] |
| 0x06 | Upload header | file ID, size, `00 00 00` | ACK; status 1 means send data | [HW] [CAP] |
| 0x07 | Unknown, seen before uploads | `00 00 nn 00 00 00 00` | status 0 | [CAP] |
| 0x08 | End of playlist | `01` | ACK | [HW] |
| 0x09 | Brightness | `11−level` + 8 × `00` | ACK | [HW] |
| 0x0A | Screen on/off | `on` + 8 × `00` | ACK | [HW] |
| 0x0B | Rotation and mirror | `rot \| mirror<<4` | ACK | [HW] partial / [DER] |
| 0x0C | Time sync | `YY MM DD hh mm ss 00` | ACK | [HW] partial |
| 0x0D | Built-in programme count | `00` | count | [CAP] |
| 0x0E | Set / change / clear password | 7 or 13 bytes | result | [DER] |
| 0x0F | Check password / query password state | 6 bytes | result | [DER] |
| 0x10 | Query panel state | `00` | state | [HW] |
| 0x11 | Start synchronised ("linkage") playback | `00 00` | ACK | [DER] |

The manufacturer app has no commands to: change speed or effect on their own (both are part of
the uploaded programme), pick a stored programme, delete one programme, read free space or a file
list, factory-reset, or read the firmware version or panel size. The version and size are in the
advertisement. [DER]

### 10.1 0x06: upload header

See §6.1. [HW] [CAP]

### 10.2 0x00 and 0x01: data chunk and end of transfer

See §6.2 and §6.3. [HW] [CAP]

### 10.3 0x02: clear all programmes [DER]

```
payload  00
frame    54 02 00 03 00 00 59
```

Deletes every stored programme. The manufacturer app shows "cleared" straight away, without
waiting for the ACK, and does not check any capability bit for this command. GifPack sends
it only after the user confirms. It has **not** been sent to the tested unit.

### 10.4 0x09: brightness [HW]

```
payload  (11 − level)  00 00 00 00 00 00 00 00        level 1 (dimmest) … 10 (brightest)
level 10 54 09 00 0B 01 00 00 00 00 00 00 00 00 00 69
level 7  54 09 00 0B 04 00 00 00 00 00 00 00 00 00 6C
```

The value is **inverted**: a raw byte of 1 is the brightest setting and 10 the dimmest. The
manufacturer app enables this command only when `versionCode >= 6` [DER]. On the tested unit,
level 7 → 6 → 7 was confirmed by reading the state back with 0x10.

### 10.5 0x0A: screen on/off [HW]

```
payload  on(1)/off(0)  00 00 00 00 00 00 00 00
ON       54 0A 00 0B 01 00 00 00 00 00 00 00 00 00 6A
OFF      54 0A 00 0B 00 00 00 00 00 00 00 00 00 00 69
```

This command only switches the panel on or off. It does **not** "activate slot 0/1". Sending
`00` after an upload switches the panel **off** (§11). The round trip on → off → on was confirmed
with 0x10.

### 10.6 0x0B: rotation and mirror [DER] / [HW] partial

```
payload  rotCode | (mirror << 4)
rotCode  index 0 → 0, 1 → 1, 2 → 2, 3 → 4          (index 0–3, probably 0°/90°/180°/270°)
example  index 1, no mirror:   54 0B 00 03 01 00 63
         index 3, mirrored:    54 0B 00 03 14 00 76
```

The manufacturer app offers this command only when funCode bit **`0x0100`** is set. The tested
unit does not set that bit (funCode `0x0044`). When the command was sent anyway, the unit ACKed
without error, but 0x10 showed no change. Current GifPack hides rotation and mirror and does
not send 0x0B unless the bit is set. The angle for each index is inferred from the manufacturer
UI's icons, not confirmed.

### 10.7 0x0C: time sync [HW] partial

```
payload  YY MM DD hh mm ss 00       YY = year % 100, local time; the 7th byte is 0 (not a weekday)
example  2026-10-06 15:04:03 → 54 0C 00 09 1A 0A 06 0F 04 03 00 00 A9
```

The manufacturer app sends this command after connecting, but only to units that are not "old"
and that set funCode bit **`0x0001`** [DER]. Firmware-drawn clock items (type 3) rely on it. An
earlier GifPack build sent it to the tested unit, which lacks bit `0x0001`. The unit returned
a success ACK, with no visible effect. The current app follows the manufacturer's rule and skips
it on that unit. A firmware-drawn clock programme (type 3) was also sent to it once as an experiment (accepted
and confirmed, rendered as garbage), so clock items stay off for units without bit `0x0001`.

### 10.8 0x10: query panel state [HW]

```
request   54 10 00 03 00 00 67
response  54 10 len  p0 p1 p2 p3 p4 …  cs16
```

| Byte (payload offset) | Meaning |
|---|---|
| p0, p1 | Not used by either app |
| p2 bit 0 | Screen on (1) or off (0) — mirrors 0x0A |
| p3 | Raw brightness. `level = 11 − p3` — mirrors 0x09 |
| p4 low nibble | Rotation code 1/2/4 → index 1/2/3, otherwise 0 — mirrors 0x0B |
| p4 high nibble | Mirror flag (non-zero means mirrored) |

The screen and brightness fields are [HW]. The rotation and mirror fields are [DER]; they always
read 0 on the tested unit. GifPack requires a frame of at least 11 bytes with a valid
checksum, and rejects brightness values outside 1–10. The manufacturer app sends this query only
when `versionCode >= 30` [DER]. The tested unit, at versionCode 14, still answers.

### 10.9 0x0D: built-in programme count [HW] [CAP]

```
request   54 0D 00 03 00 00 64
response  54 0D 00 03 nn cs16           (7 bytes)   count = nn
       or 54 0D 00 04 hi lo cs16        (8 bytes)   count = hi << 8 | lo
captured  54 0D 00 04 00 00 00 65       → count = 0
```

This is **not** a "ready" handshake. The count tells the app which built-in programme IDs exist
for item type 5 (§9.4). An older manufacturer build sends the query. In the analysed build, only
the response handling remains. GifPack queries it after connecting, with a 1.5 s timeout.

### 10.10 0x0E and 0x0F: password [DER]

Available when funCode bit **`0x0040`** is set. The tested unit sets this bit. The password is
exactly 6 bytes (UTF-8 input that must encode to 6 bytes).

**0x0F: check the password or query whether one is set**

```
query  payload 00 00 00 00 00 00      → 54 0F 00 08 00 00 00 00 00 00 00 6B
check  payload <6-byte password>
reply  54 0F 00 03 r cs16             r = 1: password OK; r = 3: no password set; other: wrong password
```

The manufacturer app sends the query after connecting. If the reply is not `3`, it asks the user
for the password and does not continue until the check returns `1` or `3`. A unit with a password
set probably refuses other commands until it is unlocked; this is not confirmed.

**0x0E: set, change or clear the password**

| Operation | Payload | Size |
|---|---|---|
| Set (no password yet) | `00` + 6 × `00` + new(6) | 13 |
| Change | `01` + old(6) + new(6) | 13 |
| Clear | `02` + old(6) | 7 |

Reply: `54 0E 00 03 r cs16`. `r = 1` means success. `r = 2` or `3` means the old password was
wrong.

[HW] On the tested unit, with no password set, the query `54 0F 00 08 00 00 00 00 00 00 00 6B` was answered with `54 0F 00 03 03 00 69` (`r = 3`, no password), twice in a row. GifPack only implements this read-only query (diagnostics → *Password status*); it implements no password-changing command.

GifPack does not implement either password-changing command. They are listed here for completeness.
Be careful: setting a password you then lose may lock you out of the panel.

### 10.11 0x04: music rhythm [DER]

Written to **A952** without waiting for any ACK, at most once every 100 ms.

```
stop     payload 00 01                         → 54 04 00 04 00 01 00 5D
data     payload 01 <style> <48 magnitudes>    (50 bytes)
```

`style` is the index of the selected visual effect. The magnitudes are the first 48 bins of an
audio FFT (`hypot(re, im)` per bin, clamped to 0–127). GifPack does not implement this
command.

### 10.12 0x11: start "linkage" playback [DER]

```
payload  00 00                     → 54 11 00 04 00 00 00 69
```

The manufacturer app uses this for paired displays, for example left and right "eyes"
(funCode bits `0x0800` / `0x1000`). It uploads one programme to each connected device, then sends
0x11, presumably to start playback on all of them at once. A single backpack does not need it.

### 10.13 Notes on 0x03, 0x07 and 0x08

- **0x03: playlist item header [HW].** Several programmes that the panel plays in turn are sent
  as a playlist. Each programme is announced with `0x03` instead of `0x06`; the data chunks and
  the end frame `54 01 00 03 01 00 59` (on A952) are exactly as for `0x06`. Frame (20 bytes,
  payload 14):

  ```
  54 03 00 10 | N i | fileId(4) | length(4) | playCount 00 | 00 00 | cs16
  ```

  `N` is the number of programmes, `i` the zero-based index, `fileId` and `length` are the same
  values that the `0x06` header would carry for that programme, and `playCount` was `01`
  (probably "how many times to play it", not varied). The ACK is `54 03 00 03 ST 00 cs`, with the
  same status meaning as for `0x06` (1 = send the data, 3 = already present, 2 = no space).
  Confirmed on the tested unit with two programmes (`N = 2`, `i = 0` and `i = 1`, accepted with
  status 1): the panel then alternates between them. The order `N i` comes from the manufacturer
  app's code; the opposite order was not needed.
- **0x08: end of playlist [HW].** `54 08 00 03 01 00 60` after the last programme; the unit
  answers with the same seven bytes (status 1). It is sent once, after all programmes.
  GifPack's diagnostic *Playlist test* sends exactly this sequence.
- **0x07: unknown [CAP].** The captured manufacturer traffic sometimes contains
  `54 07 00 09 00 00 nn 00 00 00 00 cs16` right before an upload (`nn` increases between uploads).
  The device always replies `54 07 00 03 00 00 5E` (status 0), and the app ignores the reply. At
  least one captured upload went through without any 0x07, and no code path that builds it was
  found in the analysed app build. **0x07 is not required.**

A GIF upload needs only 0x06, the chunks and the end frame. 0x03, 0x07, 0x08, 0x0A and 0x0D are
not part of it. [HW] [CAP]

---

## 11. Pitfalls and corrected assumptions

Earlier unofficial notes, including this project's own early versions, contained several mistakes
that made the panel reject every upload. They are listed here so nobody repeats them.

| Wrong assumption | What is actually true | Why it seemed to work |
|---|---|---|
| The checksum is **one byte** (`sum mod 256`). | It is **two bytes**, `sum & 0xFFFF`, big-endian. [HW] [CAP] | Short frames with a sum below 256 (for example `54 0A 00 0B 00 … 00 69`) look the same either way. |
| Cmd 06 has a **"slot" byte** before the checksum (`…00 00 00 01 B6`) that "varies". | That byte is the **high byte of the 16-bit checksum**, so it changes with the frame contents. The correct frame is `…00 00 00 02 B6` (sum `0x02B6`). The payload has no slot. [HW] [CAP] | — |
| Data frames carry a **magic `01 E7`** followed by 488 data bytes and a 1-byte checksum. | `01 E7` = 487 is the **chunk length** (MTU 512 − 25). It is followed by exactly 487 data bytes and a 2-byte checksum. The chunk length depends on the negotiated MTU (492 at MTU 517). [HW] [CAP] | — |
| Chunks are always 488 bytes. | `chunkLen = MTU − 25`. | — |
| ACK **status 0** means "new file / unknown". | Status 0 is an **error**. Malformed frames get it, which is exactly why the old uploads failed. Status 1 means proceed, 3 means already present, and 2 means out of space. [HW] [CAP] for 0 and 1. | Every malformed upload got status 0, so it looked like a normal answer. |
| Cmd 0x0A "activates slot 0/1" after an upload. | 0x0A is **screen on/off**. "Slot 0" switches the panel **off**. [HW] | The panel went dark, which looked like a failed upload. |
| 0x0D is a "ready" handshake that the device sends by itself. | 0x0D is a **built-in programme count** query. [CAP] | — |
| Uploads need 0x07 / 0x0D / 0x0A / a 0x03 + 0x08 playlist. | Only 0x06, the chunks and 0x01 are needed. [HW] [CAP] | — |
| The data-chunk ACK is `… idx(4) status(2) cs(1)`. | It is `… idx(4) status(1) cs(2)`, with a length field of 5 that excludes the checksum. [CAP] | — |
| Panel size and version come from the RCSP "target info" reply. | They come from the **advertisement**. See [device-capabilities.md](device-capabilities.md). [HW] | — |
| The GIF must be trimmed or post-processed (for example by cutting 74 trailing bytes). | The GIF is sent **unmodified**. The supposed extra bytes were an artefact of extracting a payload from logs on 488-byte boundaries, which inserts one stray checksum byte every 487 bytes. [CAP] | — |
| The end frame goes on A951. | The end frame `54 01 …` goes on **A952**, like the data. [HW] [CAP] | — |

---

## 12. Open questions

- Whether the panel requires JieLi authentication before it accepts A950 commands.
- What 0x06 statuses 2 and 4 look like on real hardware. Status 3 ("already present") is
  verified: the device recognises a programme by its file ID (CRC-32C).
- The maximum programme size. 453 KB has been uploaded successfully. No command reports free space.
- The meaning of 0x07, of the 0x10 payload bytes p0 and p1, and of the item's stay-time value 4
  for GIF items.
- The exact length of the 0x10 response on the tested unit. The app only requires at least 11 bytes.
