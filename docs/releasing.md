# Releasing

GifPack is distributed as debug builds today (CI artifacts). This page describes the
prepared release path: an optional signing configuration in Gradle and a tag-triggered
workflow that publishes a signed APK to GitHub Releases. Nothing is published until the
repository owner completes the one-time setup below.

## How it works

- `BatohManager/app/build.gradle.kts` defines a `release` signing config **only** when all
  four environment variables are set:

  | Variable | Meaning |
  | --- | --- |
  | `GIFPACK_KEYSTORE_PATH` | Path to the keystore file (`.jks`) |
  | `GIFPACK_KEYSTORE_PASSWORD` | Keystore password |
  | `GIFPACK_KEY_ALIAS` | Alias of the signing key |
  | `GIFPACK_KEY_PASSWORD` | Password of the key |

  If any of them is missing, `assembleRelease` still builds, but produces an **unsigned**
  `app-release-unsigned.apk`. Debug builds are unaffected and keep using the default debug key.
- `.github/workflows/release.yml` runs on tags matching `v*`. It checks that the signing
  secrets exist; if not, it prints a *Release skipped* notice and stops without building or
  publishing anything. With the secrets it runs the unit tests, decodes the keystore into the
  runner's temporary directory, verifies its checksum, builds `:app:assembleRelease`, verifies
  the APK signature, deletes the keystore and creates a GitHub Release with
  `GifPack-<tag>.apk` and a `.sha256` file.
- Release builds contain **no API keys**: the workflow never sets `GIPHY_API_KEY` /
  `KLIPY_API_KEY`, so users enter their own in **Settings → API keys**. Do not add those as
  repository secrets or variables, or they would be compiled into a public APK.

## One-time setup (repository owner)

### 1. Generate the upload / signing key

Run on your own machine, outside the repository:

```bash
keytool -genkeypair -v \
  -keystore gifpack-release.jks \
  -storetype PKCS12 \
  -alias gifpack \
  -keyalg RSA -keysize 4096 \
  -validity 10000 \
  -dname "CN=GifPack"
```

- `keytool` asks for a keystore password; with PKCS12 the key password is the same.
- The `-dname` is embedded in every signed APK and anyone can read it. Use a neutral value
  such as `CN=GifPack` rather than a personal name, city or e-mail address.
- Never put the keystore inside the repository working tree.

### 2. Back it up

Android only accepts updates signed with the **same key**. If the keystore or its password
is lost, existing installs can never be updated — users must uninstall and lose app data.

- Store the `.jks` file and the passwords in a password manager or an encrypted backup, and
  keep at least one offline copy.
- Record the alias (`gifpack` in the example) next to it.
- If you later publish on Google Play with Play App Signing, this key becomes the *upload*
  key and Google holds the app signing key; the backup is still needed.

### 3. Add the repository secrets

In GitHub: *Settings → Secrets and variables → Actions → New repository secret*.

| Secret | Value |
| --- | --- |
| `GIFPACK_KEYSTORE_BASE64` | The keystore encoded as one line: `base64 -w 0 gifpack-release.jks` |
| `GIFPACK_KEYSTORE_SHA256` | Its checksum: `sha256sum gifpack-release.jks \| cut -d' ' -f1` |
| `GIFPACK_KEYSTORE_PASSWORD` | `<keystore-password>` |
| `GIFPACK_KEY_ALIAS` | `<key-alias>` |
| `GIFPACK_KEY_PASSWORD` | `<key-password>` (same as the keystore password for PKCS12) |

The checksum catches a truncated or mis-pasted base64 value before Gradle sees it. Delete any
temporary base64 file afterwards; do not paste these values into issues, logs or commits.

## Cutting a release

1. Bump `versionCode` (and, if wanted, `versionName`) in `BatohManager/app/build.gradle.kts`.
2. Move the `[Unreleased]` entries in `CHANGELOG.md` under a new version heading.
3. Commit, then tag and push the tag:

   ```bash
   git tag -a v1.0.0 -m "GifPack v1.0.0"
   git push origin v1.0.0
   ```

4. Watch the *Release* workflow. On success the GitHub Release contains the signed APK and its
   checksum, with auto-generated notes you can edit.

Users switching from a CI debug APK must uninstall it first: the release key differs from the
throw-away debug keys.

## Building a signed APK locally

```bash
export GIFPACK_KEYSTORE_PATH=<path-to>/gifpack-release.jks
export GIFPACK_KEY_ALIAS=<key-alias>
read -rs GIFPACK_KEYSTORE_PASSWORD; export GIFPACK_KEYSTORE_PASSWORD
read -rs GIFPACK_KEY_PASSWORD; export GIFPACK_KEY_PASSWORD
cd BatohManager
./gradlew -Dorg.gradle.java.home=<jdk17-path> :app:assembleRelease
```

A local build compiles in any `GIPHY_API_KEY` / `KLIPY_API_KEY` from `local.properties`. Do
not distribute such an APK; publish only the keyless build from the workflow.

## Still open before a store release

These are owner decisions, not covered by the prepared setup:

- Distribution channel (GitHub Releases only, F-Droid, Google Play) and its policies.
- Target API review (`targetSdk` is 34; Google Play requires newer levels over time).
- Whether to enable code shrinking (`isMinifyEnabled`) — needs keep rules and testing.
- Hardening items listed in [privacy](privacy.md): excluding API keys from backup, removing
  the unused `READ_MEDIA_VIDEO` permission, stripping BLE debug logs in release.
- Store listing: privacy policy URL ([PRIVACY.md](../PRIVACY.md)) and data-safety answers.
