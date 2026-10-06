# Security Policy

## Supported versions

Only the latest version on `main` receives security fixes. There are no released builds
yet; reports should refer to the `versionCode` shown in the app.

## Reporting a vulnerability

Please **do not** open a public GitHub issue for security vulnerabilities.

Send a description through the repository's private vulnerability reporting (GitHub
Security Advisories). Include:
- Affected component and reproduction steps
- Android version and phone model
- Potential impact
- Any suggested mitigations

**Do not include device identifiers** — no Bluetooth addresses, advertised device names,
ADB serials, raw BLE logs or logcat dumps with those values, and no API keys. Replace them
with placeholders (`AA:BB:CC:DD:EE:FF`, `<device-name>`, `<api-key>`). If an identifier is
essential to reproduce the issue, say so and a maintainer will arrange a private exchange.

You will receive an acknowledgement within **7 days**.

## Scope

- **BLE pairing and session authentication** — the per-connection handshake, how the app
  selects and reconnects to a device, and whether another app or nearby device can make the
  app send commands or uploads.
- **Destructive commands** — any path that can clear the backpack's programmes or change its
  settings without the user's explicit action.
- **API key storage** — keys compiled into `BuildConfig` from `local.properties`, and
  user-entered keys stored in app-private SharedPreferences.
- **Untrusted input** — GIFs from search results, shares and imports, and MP4 URLs: decoder
  limits, memory exhaustion, crashes on malformed files.
- **Leakage of identifiers** through the diagnostics log, clipboard copy, smoke-harness
  reports or the upload history.

Known and deliberate:

- A key compiled into an APK can be extracted from it. Do not distribute builds that contain
  a personal key; users can enter their own key in Settings instead.
- User-entered API keys are stored in plain text in app-private storage, not in the Android
  Keystore.
- The diagnostics log shown on the backpack screen includes the device name and Bluetooth
  address so that connection problems can be diagnosed; copying it puts them on the
  clipboard.

## Out of scope

- Physical access to an unlocked phone or to the backpack.
- The backpack's firmware and its BLE stack — report those to the manufacturer.
- Vulnerabilities in third-party libraries (AndroidX, OkHttp/Retrofit, Coil, Room, Hilt) —
  report those to their maintainers.
- The availability or content of third-party GIF services.
