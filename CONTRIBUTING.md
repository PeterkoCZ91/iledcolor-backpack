# Contributing

Thank you for your interest in contributing. GifPack is a Kotlin / Jetpack Compose
Android app (`BatohManager/`) with a few Python tools (`tools/`) for testing it on a phone.

Read the [documentation index](docs/README.md), the [architecture](docs/architecture.md) and
the [feature-maturity table](docs/README.md#feature-maturity) before proposing a new feature.
Anything not reproduced on real hardware must stay labelled experimental, unverified or new
— in the UI, in the docs and in the changelog.

## Setup

```bash
git clone <REPO_URL>
cd <repo>/BatohManager

# Android SDK location and optional API keys — this file is git-ignored
cat >> local.properties <<'EOF'
sdk.dir=<android-sdk-path>
GIPHY_API_KEY=<your-giphy-key>
KLIPY_API_KEY=<your-klipy-key>
EOF
```

**Never commit `local.properties`, keystores, API keys, BLE captures or logcat dumps** — they
are excluded by `.gitignore`.

## Build and test

Use JDK 17; see [building](docs/building.md) for why the JVM flags matter.

```bash
./gradlew -Dorg.gradle.java.home=<jdk17-path> \
  '-Dorg.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8 -XX:TieredStopAtLevel=1' \
  --max-workers=2 \
  :app:assembleDebug :app:lintDebug :app:testDebugUnitTest \
  :core:conversion:testDebugUnitTest :core:data:testDebugUnitTest :core:storage:testDebugUnitTest \
  :feature:backpack:testDebugUnitTest :feature:library:testDebugUnitTest :feature:search:testDebugUnitTest

cd ..
python3 -m unittest discover -s tools -p 'test_*.py'
git diff --check
```

All of these must pass before a pull request. Details per module are in
[testing](docs/testing.md).

Keep logic testable on the JVM: protocol framing, payloads, parsers, layout math and state
machines live in plain Kotlin classes (`BackpackFrame`, `BackpackPayload`,
`BackpackCommands`, `TextBannerLayout`, `UploadHistory`, ...) with Android and GATT kept at
the edges. A protocol change needs a unit test against a known-good frame.

## Working with real hardware

- The default smoke run (`python3 tools/apk_smoke.py`) does not touch the backpack.
  `--upload` and `--cancel-upload` **write** to it; `--hardware` connects but writes nothing.
  See the flag table in [testing](docs/testing.md#what-each-flag-does).
- Never confirm **Clear backpack contents** as part of a test or cleanup.
- Do not flash, modify or redistribute device firmware, and do not add code that does.
- New commands must be gated on the capability the device advertises (see
  [device capabilities](docs/device-capabilities.md)); hide a control rather than send a
  command the device does not report.

## Translations

The default resources are Czech (`values/`), with English in `values-en/`. Every new string
needs both; `tools/test_android_locales.py` fails otherwise. Technical error details stay in
the log, the UI shows a localised message.

## Branch naming

| Type | Prefix | Example |
|---|---|---|
| New feature | `feature/` | `feature/upload-effects` |
| Bug fix | `fix/` | `fix/mtu-fallback` |
| Documentation | `docs/` | `docs/user-guide` |

## Code style

- Kotlin official code style (`kotlin.code.style=official`), 4-space indentation, LF line
  endings.
- Android lint clean for the touched module.
- No API keys, Bluetooth addresses, device serials or names, local paths, personal names or
  e-mail addresses in code, comments, tests, fixtures or docs. Use placeholders such as
  `AA:BB:CC:DD:EE:FF`.

## What we accept

- Bug fixes with a clear root cause and a regression test.
- Hardware reports for other compatible backpacks (capabilities and results, with
  identifiers removed).
- Verification results for features currently labelled experimental.
- Translations and documentation improvements.

## What we do not accept

- Commits containing keys, keystores, captures with device identifiers or personal data.
- Manufacturer firmware, decompiled code or copies of vendor assets.
- Features that write to the backpack without being gated on its advertised capabilities.
- Mandatory paid services or accounts for the core library / editor / upload path.
