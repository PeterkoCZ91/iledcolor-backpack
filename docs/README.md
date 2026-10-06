# Documentation

This directory documents the public behaviour of GifPack. Device identifiers
(Bluetooth addresses, serial numbers, advertised names), API keys, local paths and raw
device captures do not belong here.

## Choose a path

### I want to use the app

1. [User guide](user-guide.md) — home, search, collection, editor, scrolling text and the
   backpack screen, step by step.
2. [Troubleshooting](troubleshooting.md) — Bluetooth permissions, GATT error 133, MTU,
   missing API keys and rejected uploads.

### I want to build or test it

1. [Building](building.md) — JDK 17, the Gradle JVM settings that keep the build stable,
   API keys and installing a debug APK.
2. [Testing](testing.md) — unit tests per module, the Python tests and the ADB smoke
   harness, including which flags write to a real backpack.

### I want to understand or extend it

1. [Architecture](architecture.md) — modules, the GIF → payload → BLE upload flow and the
   key classes.
2. [BLE protocol](ble-protocol.md) — frame format, commands, upload sequence and status
   codes, documented from the owner's own device and app traffic.
3. [Device capabilities](device-capabilities.md) — the advertisement fields
   (`funCode`, firmware version, panel size, colour type) and how the app gates features on
   them.
4. [Roadmap](roadmap.md) — remaining hardware verification and planned features.

## Feature maturity

"Verified" means confirmed on the tested backpack (iledcolor 64 × 64, `funCode 0x0044`,
firmware 14) with a phone, not only by unit tests.

| Area | Maturity | Notes |
| --- | --- | --- |
| GIF search (Giphy, Klipy, pixel-art mode) | Working | Needs API keys; pixel-art mode is a Giphy search for square pixel-art results. |
| Curated categories, pinned / trending / recent | Working | Retry on network errors. |
| Background download with status and retry | Working | WorkManager; downloaded files are validated before saving. |
| Collection, picker import, share-to-app import | Verified on a phone | Copies are written to `Pictures/GifPack`; malformed GIFs are rejected without a crash. |
| Safe GIF decoder | Working, unit-tested | 20 MiB, 600 frames, 4 M canvas pixels, 64 M decoded pixels; cancellable. |
| 64 × 64 panel preview (library, detail) | Working, unit-tested | Same sampling as the upload converter. |
| Editor: rotation, flips, fit / crop | Working, unit-tested | Physical colours and playback on the panel not audited. |
| Editor: programme speed and brightness | Experimental | Bytes are written into the payload; effect on the panel not verified. |
| Scrolling text banner | Verified | A 136-frame / 85 KB banner uploaded (backpack confirmed) and scrolls on the panel. |
| Video URL → GIF | Working | MP4 up to 100 MB, up to 30 frames at 64 × 64. |
| GIF upload (Cmd 06 → chunks → end) | Verified | Small GIF, 453 KB / 96-frame GIF, cancel and retry. |
| "Already on backpack" status | Implemented, unit-tested | Not yet observed on hardware. |
| "Not enough space" status | Implemented, unit-tested | Not yet observed on hardware. |
| Brightness 1–10 | Verified | Round trip 7 → 6 → 7 confirmed by the status query. |
| Screen on / off | Verified | Round trip confirmed by the status query. |
| Status query | Verified | Reads screen, brightness, rotation, mirror — not free memory. |
| Clock sync on connect | Gated, verified | Sent only when the device advertises time support (bit `0x0001`); skipped on the tested backpack. |
| Device card | Verified | Name, firmware, panel size, colours and features from the advertisement. |
| Upload history | Verified | Last 10 uploads; separates "confirmed by backpack" from failed / cancelled. |
| Built-in programmes | Count verified, playback unverified | The 0x0D query answers 0 on the tested backpack, so the picker stays hidden. |
| Panel rotation / mirror | Unverified, gated | Shown only with bit `0x0100`; the tested firmware does not advertise it and ignored the command. |
| Clear backpack contents | Implemented, destructive | Only the confirmation dialog has been exercised (and cancelled). |
| Backpack password (bit `0x0040`) | Not implemented | Researched only; risk of locking the device. |
| Czech / English UI | Verified on a phone | Per-app language; a test checks every Czech string has an English translation. |

## Documentation rules

- Examples use placeholders: `AA:BB:CC:DD:EE:FF` for Bluetooth addresses, `<jdk17-path>`
  for local paths, `<your-giphy-key>` for keys.
- Secret fields name the property (`GIPHY_API_KEY`), never a value.
- Behaviour not reproduced on hardware is labelled experimental, unverified or new.
- Manufacturer firmware, server endpoints and decompiled code are not described or linked.

The project overview is in the root [README](../README.md). Security reports follow
[SECURITY.md](../SECURITY.md); contributions follow [CONTRIBUTING.md](../CONTRIBUTING.md).
