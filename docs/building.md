# Building

The Android project lives in `BatohManager/` and uses the included Gradle wrapper
(Gradle 8.7, Android Gradle Plugin 8.2, Kotlin 1.9).

## Requirements

| Tool | Version | Notes |
| --- | --- | --- |
| JDK | **17** | Point Gradle at it explicitly (see below). |
| Android SDK | Platform 34, Build-Tools 34.0.0 | `compileSdk` / `targetSdk` 34, `minSdk` 26. |
| Android Studio | optional | The command line build is the reference. |
| `adb` | any recent | Only to install the APK or run the smoke harness. |

Tell Gradle where the SDK is in `BatohManager/local.properties` (Android Studio writes this
for you):

```properties
sdk.dir=<android-sdk-path>
```

## JDK 17 and a stable Gradle JVM

On the development machine the Gradle daemon occasionally crashed (JIT segfaults) under
JDK 21 and under JDK 17 with default settings. The build has been stable with JDK 17 passed
explicitly and the JIT limited to its first tier:

- `-Dorg.gradle.java.home=<jdk17-path>` — the JDK Gradle itself runs on, independent of
  `JAVA_HOME`;
- `-XX:TieredStopAtLevel=1` in `org.gradle.jvmargs`;
- `--max-workers=2` to keep memory use predictable.

If a daemon still dies with `SIGSEGV`, run the same command again; a single retry has always
been enough so far.

## Build command

From `BatohManager/`:

```bash
./gradlew -Dorg.gradle.java.home=<jdk17-path> \
  '-Dorg.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8 -XX:TieredStopAtLevel=1' \
  --max-workers=2 --console=plain \
  :app:assembleDebug :app:lintDebug
```

The full check used before handing over a build adds every module's unit tests:

```bash
./gradlew -Dorg.gradle.java.home=<jdk17-path> \
  '-Dorg.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8 -XX:TieredStopAtLevel=1' \
  --max-workers=2 --console=plain \
  :app:assembleDebug :app:lintDebug \
  :app:testDebugUnitTest \
  :core:conversion:testDebugUnitTest :core:data:testDebugUnitTest :core:storage:testDebugUnitTest \
  :feature:backpack:testDebugUnitTest :feature:library:testDebugUnitTest :feature:search:testDebugUnitTest
```

The debug APK is written to `BatohManager/app/build/outputs/apk/debug/app-debug.apk`. The
version comes from `app/build.gradle.kts` (`versionCode`, `versionName = "v<versionCode>"`);
current debug builds are `versionCode 51`.

## API keys

Online search needs keys for the GIF services; everything else builds and runs without
them. The `core/data` module reads each key, in order, from:

1. `BatohManager/local.properties` (git-ignored),
2. a Gradle property (`-PGIPHY_API_KEY=...`),
3. an environment variable of the same name,

and compiles it into `BuildConfig`. A missing key becomes an empty string — the build does
not fail.

```properties
# BatohManager/local.properties
GIPHY_API_KEY=<your-giphy-key>
KLIPY_API_KEY=<your-klipy-key>
```

Users can also enter their own keys at runtime in **Settings → API keys**; a non-empty value
there overrides the compiled-in key. A key compiled into an APK can be extracted from it, so
do not share APKs built with a personal key. Never commit `local.properties`.

## Installing a debug APK

Tagged debug builds are attached to the [releases](https://github.com/PeterkoCZ91/iledcolor-backpack/releases)
page (no login needed; the checksum is in the notes). A prebuilt debug APK without API keys is also
attached to every CI run on `main` as the
`gifpack-debug-<commit>` artifact (30-day retention, GitHub login required). It is signed with
the CI runner's temporary debug key, so it cannot update an APK built elsewhere — uninstall first.

```bash
adb devices                                   # exactly one authorised phone
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

`-r` updates an existing installation and keeps the app's data (collection metadata,
settings, cached device capabilities, upload history). A debug APK is signed with the local
debug key: installing a build signed with a different key requires uninstalling first, which
**deletes the app's data**. GIFs already saved to `Pictures/GifPack` stay on the phone.

The APK can also be copied to the phone and opened from a file manager (allow installing
from that source when Android asks).

## Release builds

There is no release configuration yet: no signing config, no minification and no store
listing. See the [roadmap](roadmap.md#before-a-public-release).
