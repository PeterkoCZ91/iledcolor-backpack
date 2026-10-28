# GifPack

*Repository: `iledcolor-backpack` — an unofficial open-source Android client and BLE protocol
documentation for iledcolor-compatible 64 × 64 LED backpacks and iPixel Color LED panels.*

[![CI](https://github.com/PeterkoCZ91/iledcolor-backpack/actions/workflows/ci.yml/badge.svg)](https://github.com/PeterkoCZ91/iledcolor-backpack/actions/workflows/ci.yml)
[![Android 8.0+](https://img.shields.io/badge/Android-8.0%2B%20%28API%2026%29-3DDC84.svg)](https://developer.android.com/about/versions/oreo)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9-7F52FF.svg)](https://kotlinlang.org/)
[![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4.svg)](https://developer.android.com/jetpack/compose)
[![License: MIT](https://img.shields.io/badge/license-MIT-green.svg)](LICENSE)

An Android app that finds, collects, converts and edits animated GIFs and sends them over
Bluetooth Low Energy to an **iledcolor-compatible 64 × 64 LED backpack** or to an
**iPixel Color `LED_BLE_*` panel** (for example 32 × 32 or 64 × 16). It replaces the
manufacturer app for one job — getting the animation you want onto the panel — with a local
library, an editor and previews at the connected panel's resolution, a scrolling-text
generator with prepared texts, and an upload that reports what the panel actually confirmed.

> **Project status:** debug builds only (versionCode 66); there is no store listing or
> signed release. **Download:** the APK is on the
> [releases page](https://github.com/PeterkoCZ91/iledcolor-backpack/releases) (pre-release, no
> login, no API keys inside). GIF upload, several programmes played in turn, joining GIFs, the
> scrolling text banner, display brightness, screen on/off, the device card and upload
> history are verified end to end on one backpack. Programme speed/brightness and built-in
> programme playback are implemented and unit-tested but **not yet verified on the panel** (the
> effect byte of a GIF item has no effect on the tested unit), and are labelled as such below
> and in the app. The BLE protocol was documented from the owner's own device and app traffic;
> other models or firmware may behave differently. Read [Safety](#safety) before using
> **Clear backpack contents**.

[**Download the APK**](https://github.com/PeterkoCZ91/iledcolor-backpack/releases) · [Česky](#česky) · [Documentation](docs/README.md) · [Changelog](CHANGELOG.md)

## Supported panels

| Panel | Size | Status |
| --- | --- | --- |
| iledcolor LED backpack | 64 × 64 | **Verified** (one unit), full feature set |
| iPixel Color (`LED_BLE_XXXXXXXX`), device type `0x81` | 32 × 32 | **Verified** on hardware: PNG and GIF uploads, including a multi-window GIF (about 41 KB) |
| iPixel Color, device type `0x83` | 64 × 16 | **Verified** on hardware, same uploads |
| iPixel Color, any other device type of the family (18 more sizes between 32 × 16 and 448 × 32) | per type | **Untested** — the size comes from the open-source [pypixelcolor](https://github.com/lucagoc/pypixelcolor) project and is shown as "untested size" in the app; an unknown type is listed but nothing is sent to it |

How picking a panel works:

- The **Panel** screen lists the supported panels GifPack sees nearby (family, size, signal).
  The backpack keeps its old behaviour: it is found and reconnected automatically.
- An **iPixel panel is connected only after you pick it** in that list. GifPack never takes one
  on its own, so it cannot overwrite a panel that belongs to someone else. Once picked, it is
  reconnected as the last-used panel.
- Uploads to an iPixel panel go only to the **display slot**. Nothing stored on the panel by the
  vendor app is deleted or overwritten, and GifPack never sends a delete or clear command to it.
- Images, GIFs and the scrolling text are converted to the connected panel's size automatically
  (a square GIF on a 64 × 16 panel is fitted with black bars). The editor and the previews use that
  size too. An iPixel panel has its own brightness, display on/off, rotation and clock-face
  settings; backpack-only controls (built-in programmes, programme speed/effect, sending as a
  sequence, clearing) are hidden for it.
- Every panel you pick joins **My panels**. The **All my panels** row of the *Where to send?* sheet (and **Send to all my panels** in Detail, the Editor and the text screen) sends the same image, GIF or
  text to each of them that is in range, one after another, converted for each panel's size. Only
  panels you picked are contacted, never other panels that happen to be nearby.

## Tested hardware

| Device | Advertised capabilities | Result |
| --- | --- | --- |
| iledcolor 64 × 64 RGB LED backpack (one unit) | `funCode 0x0044` (GIF + password), firmware `versionCode 14`, colour type RGB888 | Upload, cancel/retry, brightness, screen on/off, status query verified. No rotation support (bit `0x0100` not set); 0 built-in programmes reported. |
| iPixel Color 32 × 32 panel (one unit) | manufacturer data company `0x5254`, type `0x81` | PNG and GIF upload verified, including a multi-window GIF. |
| iPixel Color 64 × 16 panel (one unit) | manufacturer data company `0x5254`, type `0x83` | PNG and GIF upload verified, including a multi-window GIF. |

Other panels that use the same BLE service may work; the app reads each device's
capabilities from its advertisement and hides controls the device does not report. What the
advertisement fields mean is documented in [device capabilities](docs/device-capabilities.md)
and the wire format in the [BLE protocol](docs/ble-protocol.md) reference.

## Features

| Area | What it does | Maturity |
| --- | --- | --- |
| GIF search | Giphy (GIFs and stickers), Klipy, a pixel-art mode, curated categories with pinned / trending / recent lists, content-rating and aspect filters | Working; needs API keys ([below](#api-keys)) |
| Download to library | Background download (WorkManager) with visible pending / running / done / error state and retry; files are validated before saving | Working |
| My collection | Local library in `Pictures/GifPack`; import with the system picker or by sharing a GIF to the app (`ACTION_SEND`); delete; "as on the panel" preview at the connected panel's size | Working, verified on a phone |
| Join GIFs / sequence | Select several GIFs in the collection, then join their frames into one GIF or send each as its own programme so the backpack plays them in turn | **Verified** on the backpack (2 programmes); limits above 453 KB / 8 programmes unknown |
| Detail | Animated preview, pixel-exact panel preview (64 × 64 by default, the connected panel's size otherwise), save, share, convert, send, edit | Working |
| Editor | Rotate 90° / 180° / 270°, flip horizontally / vertically, fit or crop to the panel's shape, animated preview at the panel's size, save as a copy or send | Working |
| Programme speed and brightness | Two sliders (0–255, default 100) written into the uploaded programme | **Experimental** — effect on the panel not verified |
| Scrolling text | Renders text (up to 60 characters, 9 colours, 3 sizes, 3 speeds, bold) into a looping GIF at the connected panel's size (64 × 64 by default) to save or send | **New** — not yet verified on the panel |
| Prepared texts | Save up to 30 banner texts with their options and send any of them to the connected panel with one tap; the text is rendered for that panel's size at send time | **New** — unit-tested |
| iPixel Color panels | Discover `LED_BLE_*` panels, read their size, send images, GIFs and text banners to the display slot; brightness, display on/off, rotation and clock face | **Verified** on 32 × 32 and 64 × 16 (other sizes untested) |
| Send to all my panels | Sends one image, GIF, text banner or prepared text to every picked panel in range in turn, each converted for its own family and size, with a per-panel result | **New** — unit-tested planning |
| Video → GIF | Downloads an MP4 from a URL (up to 100 MB) and converts it to a 64 × 64 GIF (up to 30 frames) | Experimental; the URL flow and the converter test passed on a phone |
| Backpack upload | Scan / connect, upload with progress, cancel and retry; distinguishes "uploaded", "already on backpack", "not enough space" and "rejected" | **Verified** (small GIF and a 453 KB, 96-frame GIF) |
| Backpack controls | Device card, brightness 1–10, screen on/off, status refresh, upload history (last 10), diagnostics log | Brightness and screen **verified**; others working |
| Built-in programmes | Plays a programme stored in firmware (count from the device) | Hidden when the device reports 0 — **unverified** |
| Panel rotation / mirror | Sent only when the device advertises rotation support | Hidden on the tested backpack — **unverified** |
| Languages | System default, Czech or English, switchable in **More** | Working, verified on a phone |

The app accepts GIFs up to **20 MiB** and **600 frames** (with canvas and decoded-pixel
limits). These are safety limits of the app's decoder, **not** the backpack's storage
capacity, which is unknown. Every upload is converted to the connected panel's size first
(64 × 64 for the backpack; nearest neighbour, transparency → black; centre crop or, for a
strongly different shape such as a square GIF on a 64 × 16 panel, fit with black bars).

## Screenshots

| Home | Search | Panel | Collection |
| :---: | :---: | :---: | :---: |
| <img src="docs/images/home.png" width="240" alt="Home dashboard with panel status, search bar, quick actions and recent GIFs"> | <img src="docs/images/search.png" width="240" alt="Search tab showing the GIF categories"> | <img src="docs/images/panel.png" width="240" alt="Panel tab with connection, send actions and my panels"> | <img src="docs/images/collection.png" width="240" alt="Collection tab with saved GIFs"> |

| More | API keys and About | Scrolling text |
| :---: | :---: | :---: |
| <img src="docs/images/more.png" width="240" alt="More tab with tools and settings"> | <img src="docs/images/api-keys.png" width="240" alt="API keys section with empty Klipy and Giphy fields, and About with the version"> | <img src="docs/images/scrolling-text.png" width="240" alt="Scrolling text generator with preview"> |

Captured in English. The API key fields are
empty: public builds do not include keys, you enter your own.

## Quick start

Requirements: a phone with Android 8.0 (API 26) or newer and Bluetooth LE; to build, JDK 17
and Android SDK Platform 34.

**Without building:** download the debug APK (`GifPack-v65-debug.apk`) from the
[releases page](https://github.com/PeterkoCZ91/iledcolor-backpack/releases) (no login needed;
the SHA-256 is in the release notes). It is a debug build, so it is marked as a pre-release.
Or take the newest development build: every CI run on `main` publishes a debug APK as the
`gifpack-debug-<commit>` artifact (open the latest green run under
[Actions](https://github.com/PeterkoCZ91/iledcolor-backpack/actions/workflows/ci.yml); downloading
needs a GitHub login, artifacts are kept 30 days). It contains **no API keys** — enter your own
in **More → API keys**. Each CI APK is signed with a throw-away debug key, so uninstall the
previous one before installing a newer build.

To build it yourself:

```bash
git clone https://github.com/PeterkoCZ91/iledcolor-backpack.git
cd iledcolor-backpack/BatohManager

# Optional: API keys for online search (see below)
printf 'GIPHY_API_KEY=<your-giphy-key>\nKLIPY_API_KEY=<your-klipy-key>\n' >> local.properties

./gradlew -Dorg.gradle.java.home=<jdk17-path> :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Then in the app:

1. Open the **Panel** tab, allow the Bluetooth permissions, tap **Find device**
   and pick your backpack or iPixel panel. Later sessions reconnect with **Connect backpack**.
2. Get a GIF: search and download one, import it in the **Collection** tab, or share it to the
   app from a gallery.
3. Send it to the panel (from the GIF detail, or **Send a GIF** on the Panel tab). The Panel tab shows
   progress and the confirmed result.

Full build notes, including the Gradle JVM settings that keep the build stable, are in
[building](docs/building.md); every screen is described in the [user guide](docs/user-guide.md).

## API keys

Online search uses third-party GIF services. Keys are **optional** — without them the
library, import, editor, text banner, video conversion and backpack upload all work; only
online search returns an error.

| Service | Where the app reads the key | Where to get one |
| --- | --- | --- |
| Giphy (also used by the pixel-art mode and categories) | `GIPHY_API_KEY` in `BatohManager/local.properties`, or **More → API keys** | developers.giphy.com |
| Klipy | `KLIPY_API_KEY` in `BatohManager/local.properties`, or **More → API keys** | partner.klipy.com |

A key typed in the app takes precedence; an empty field falls back to the key compiled into
the build. `local.properties` is git-ignored. Keys may also come from a Gradle property or an
environment variable of the same name. Note that a key compiled into an APK can be extracted
from it — do not distribute builds with your personal key.

## Safety

- **Clear backpack contents is destructive.** It deletes every stored image and animation
  from the backpack's memory. Your phone library is not touched, but the backpack cannot be
  restored except by uploading again. The app asks for confirmation; there is no undo.
- **Cancelling an upload resets the connection.** No verified protocol abort exists, so the
  app disconnects after partial data. Retry starts a clean upload.
- Uploads are stored on the backpack. Repeated test uploads use up its (unknown) capacity.
- The diagnostics log can contain your backpack's Bluetooth address and name. Remove them
  before sharing a log in an issue.

## Limitations

- One backpack model and firmware have been tested. Of the iPixel Color family only the 32 × 32
  and 64 × 16 sizes have been tested; the other sizes are accepted but unverified, and iPixel
  panels support images, GIFs and text only (no brightness, clock or timers).
- The backpack's storage capacity and the largest safe programme size are unknown; 453 KB is
  the largest upload confirmed so far.
- The status query reads display settings only — not free memory, model or firmware.
- Speed / brightness bytes, the text banner and built-in programmes are not verified on the
  panel. Rotation is hidden because the tested firmware does not advertise it.
- Uploads run while the app is in use; there is no foreground service, so locking the phone
  or leaving the app for long can interrupt a transfer.
- A detailed failure reason is not yet shown in the UI; the upload card shows a generic
  error and the history records it as "Failed".
- Online results depend on the availability and terms of Giphy and Klipy.

## Documentation

| Document | Contents |
| --- | --- |
| [docs/README.md](docs/README.md) | Index and feature-maturity table |
| [User guide](docs/user-guide.md) | Every screen, step by step |
| [Architecture](docs/architecture.md) | Modules, data flow GIF → payload → BLE, key classes |
| [BLE protocol](docs/ble-protocol.md) | Frames, commands, upload sequence and status codes |
| [Device capabilities](docs/device-capabilities.md) | Advertisement fields and what the app does with them |
| [Building](docs/building.md) | JDK, Gradle settings, keys, installing a debug APK |
| [Testing](docs/testing.md) | Unit tests, Python tests, the ADB smoke harness |
| [Troubleshooting](docs/troubleshooting.md) | Permissions, GATT 133, MTU, keys, rejected uploads |
| [Roadmap](docs/roadmap.md) | Planned verification and features |
| [Privacy](docs/privacy.md) | Network requests, stored data and permissions |
| [Releasing](docs/releasing.md) | Signed release builds and the tag workflow |

## Acknowledgements

The BLE protocol was worked out by reverse engineering the manufacturer's app (a Flutter app,
so most of its logic lives in a Dart AOT binary). Tools used:

- [morluto/rea](https://github.com/morluto/rea) – reverse-engineering toolkit
- [worawit/blutter](https://github.com/worawit/blutter) – Dart AOT snapshot analysis
- [Ghidra](https://github.com/NationalSecurityAgency/ghidra) – native binary analysis

Contributions follow [CONTRIBUTING.md](CONTRIBUTING.md); security reports follow
[SECURITY.md](SECURITY.md). **Privacy:** no account, analytics or tracking; search text goes
to Giphy / Klipy only when you search — see [PRIVACY.md](PRIVACY.md).
GifPack is released under the [MIT License](LICENSE).
It is an independent project and is not affiliated with the manufacturers of the backpack or of the iPixel Color panels.

The iPixel Color byte layouts follow the open-source
[pypixelcolor](https://github.com/lucagoc/pypixelcolor) project as a reference (no code was
copied); the parts marked verified were checked on real panels.

---

## Česky

**GifPack** je aplikace pro Android, která vyhledává, ukládá, převádí a upravuje
animované GIFy a posílá je přes Bluetooth LE do **LED batohu 64 × 64 kompatibilního
s iledcolor** nebo do panelů **iPixel Color** (`LED_BLE_*`, např. 32 × 32 a 64 × 16).

> **Stav projektu:** zatím jen debug buildy (versionCode 66), bez vydání v obchodě.
> **APK ke stažení** je na stránce
> [Releases](https://github.com/PeterkoCZ91/iledcolor-backpack/releases) (předběžné vydání, bez
> přihlášení, bez API klíčů). Nahrání GIFu, střídání více programů, spojování GIFů, běžící text,
> jas, zapnutí/vypnutí displeje, karta zařízení a historie nahrávání jsou ověřené na jednom
> batohu. Rychlost a jas programu a přehrání vestavěných programů jsou implementované a pokryté
> testy, ale **na panelu zatím neověřené**. Protokol byl zdokumentován z vlastního zařízení
> a provozu vlastní aplikace.

**Testovaný hardware:** jeden batoh iledcolor 64 × 64, `funCode 0x0044`, firmware 14.
Rotaci nepodporuje; hlásí 0 vestavěných programů.

**Podporované panely:** batoh iledcolor 64 × 64 (ověřeno) a panely iPixel Color — **ověřené jsou
32 × 32 a 64 × 16**, ostatní velikosti této řady jsou neotestované (velikost vychází z projektu
[pypixelcolor](https://github.com/lucagoc/pypixelcolor), který slouží jako předloha rozložení
bajtů, kód nebyl převzat). Panel iPixel se připojí **až poté, co ho v aplikaci vybereš**
v seznamu panelů; sám se nikdy nevybere. Nahrává se jen do zobrazovacího slotu, nic uloženého
v panelu se nemaže. Obrázky, GIFy i běžící text se převedou na velikost připojeného panelu
(čtvercový GIF na panelu 64 × 16 se vloží s černými pruhy); ovládání jen pro batoh (jas,
vestavěné programy aj.) je u iPixelu skryté. Připravené texty uložíš a pošleš jedním klepnutím.

**Funkce:** vyhledávání (Giphy, Klipy, pixel-art režim, kategorie), stahování do sbírky,
import přes výběr souborů a sdílení z galerie, editor (otočení, zrcadlení, Fit/Crop,
náhled v rozlišení panelu), experimentální rychlost a jas programu, běžící text, převod videa z URL,
nahrávání s průběhem/zrušením/opakováním, karta zařízení, jas 1–10, displej zap/vyp,
historie nahrávání a čeština/angličtina.

**Bez sestavování:** každý běh CI na `main` přikládá debug APK jako artefakt
`gifpack-debug-<commit>` (záložka Actions, nutné přihlášení na GitHub, drží se 30 dní).
Neobsahuje žádné API klíče — zadej vlastní v **Nastavení → API klíče**.

**Rychlý start:** v `BatohManager/` spusť
`./gradlew -Dorg.gradle.java.home=<jdk17-path> :app:assembleDebug` a nainstaluj
`app/build/outputs/apk/debug/app-debug.apk`. Podrobnosti v [docs/building.md](docs/building.md).

**API klíče jsou volitelné.** Bez nich funguje vše kromě online vyhledávání. Aplikace je
čte z `BatohManager/local.properties` (`GIPHY_API_KEY`, `KLIPY_API_KEY`) nebo z
**Nastavení → API klíče** (klíč z nastavení má přednost).

**Bezpečnost:** **Smazat obsah batohu** nevratně odstraní všechny programy v batohu (GIFy
v telefonu zůstanou). Zrušení nahrávání odpojí batoh. Diagnostický log může obsahovat
Bluetooth adresu batohu — před sdílením ji odstraň.

**Omezení:** testován jeden batoh a firmware, z panelů iPixel jen 32 × 32 a 64 × 16; kapacita batohu není známá (největší
potvrzený upload 453 KB); limity 20 MiB / 600 snímků jsou limity aplikace, ne batohu;
nahrávání neběží jako služba na pozadí.

**Soukromí:** žádný účet, analytika ani sledování; hledaný text jde do Giphy / Klipy jen
při vyhledávání — viz [PRIVACY.md](PRIVACY.md) (anglicky).

Licence: [MIT](LICENSE).

**Poděkování:** protokol BLE vznikl reverzním inženýrstvím aplikace výrobce (Flutter, logika
je v binárce Dart AOT). Použité nástroje: [morluto/rea](https://github.com/morluto/rea),
[worawit/blutter](https://github.com/worawit/blutter) (analýza Dart AOT) a
[Ghidra](https://github.com/NationalSecurityAgency/ghidra) (analýza nativních binárek).
