# Testing the APK over ADB

Python 3 (standard library only) and `adb` on PATH. The phone must be unlocked and authorised for USB debugging. Grant the app's Bluetooth permission before the test; the script does not confirm system dialogs automatically. Do not use the phone while the test runs.

Run from the repository root:

```sh
python3 tools/apk_smoke.py
python3 tools/apk_smoke.py --apk ../BatohManager-v45-debug.apk
python3 tools/apk_smoke.py --serial DEVICE_SERIAL
```

Results: `artifacts/apk-smoke/<time>.json` in the project (git-ignored). Exit 0 = the automatic checks passed, exit 1 = a failure. Every step starts from a fresh UIAutomator XML dump, checks the package and looks for a specific text/label; coordinates are derived from the control it found. Waits have a timeout.

The script stops the app process and opens a new task so that it starts without an active backpack connection. Run it only after in-progress downloads/uploads have finished. It does not uninstall the app, delete its data, change GIFs or write any content to the backpack. `--apk` explicitly allows an update install that keeps data. It checks Home, the version against the real APK metadata, settings, the library, the backpack controls, the collapsed diagnostics and opening/cancelling the picker from its own library. It reads the crash buffer from the start of its own run; older crashes from before the test are ignored. It does not store raw logs, XML or the serial number.

Optional check with a real backpack:

```sh
python3 tools/apk_smoke.py --hardware
```

This option connects to the backpack, verifies that the time sync and the status query did not end in an error and that the UI shows both brightness and display state, then opens the "Smazat obsah batohu" (Clear backpack contents) dialog and presses **Zrušit** (Cancel). It never confirms the deletion and performs no real upload. Without `--hardware` this check is reported as skipped.

English walk-through of the screens:

```sh
python3 tools/apk_smoke.py --fixtures --locale-sweep
```

Reads the language selected in settings, walks the main screens in English, restores the original choice and also verifies the Czech categories, search, collection and backpack screens. A valid fixture GIF is opened in both the detail and the editor in both languages. Cleanup removes only the exact URIs inserted by this run. No backpack needs to be connected.

## Manual check

1. Open the library with an existing GIF and check the animated preview and the detail. A corrupted GIF must show an error without crashing the app.
2. Find a GIF, download it to the library and send it with the arrow. Watch the name/preview, preparation, connecting, the percentage and the persistent "Hotovo" (Done); verify the image on the backpack.
3. Leave the screen during a transfer and come back: the transfer and its state must continue.
4. On the next upload press "Zrušit nahrávání" (Cancel upload), then "Zkusit znovu" (Try again). Verify that the old transfer does not overlap with the new one and that retry reconnects the backpack.
5. Switch the backpack off during a transfer: an error is shown, not a false "Hotovo". Switch it on again and repeat.
6. Verify the unknown display state, loading the status, brightness and the display switch. Try clearing the contents only deliberately, once you have the GIFs saved on the phone.
7. In the app settings switch the language to **English** and walk through the home screen, categories, search, library, editor and backpack. Close and reopen the app; the choice must persist. Then select **Podle systému** (System default). On Android 13+ also check the language in the system app info.

Visual quality and playback on the backpack are not confirmed by merely opening the UI. The default run creates no test GIFs and deletes no existing files.

## Optional GIF regression tests

```sh
python3 tools/apk_smoke.py --fixtures
```

Only this explicit option creates two test MediaStore records in `Pictures/GifPack` under the unique names `BatohSmoke_<random ID>_valid.gif` and `..._malformed.gif`. It needs Android 10+ and full image access granted to the app beforehand; selected photos alone are not enough. The script does not change the permission itself.

The valid GIF is 64×64 with two coloured animated phases. The malformed one has a GIF header, a descriptor and an invalid/truncated LZW block without a valid first phase. The test opens both from the library: for the valid one it waits for a real `onSuccess` decode ("Náhled GIFu načten", GIF preview loaded), for the malformed one it requires an understandable error, a return to the library and no fatal crash. If the decoder surprisingly accepts the malformed file, the test fails instead of falsely claiming the error branch was tested. The GIFs are not sent to the backpack.

The `finally` block removes **only the exact URIs of the records inserted by this run**, even if the test fails. Other GIFs are not deleted. Killing the process with kill -9 or unplugging the phone can prevent the cleanup; the JSON report flags a cleanup failure. The default run without `--fixtures` leaves GIFs untouched.

## Explicitly sending a test GIF to the backpack

```sh
python3 tools/apk_smoke.py --fixtures --upload
```

`--upload` works only together with `--fixtures` and really writes a valid red/blue GIF to a switched-on backpack. The script finds the arrow on its own valid GIF's card in the library, waits for a confirmed transfer success and verifies that the result stays visible after leaving and reopening the controls. The malformed file is never sent. The test does not issue the clear-backpack command; the test animation may therefore remain on the backpack. Cleanup removes only the two own test GIFs from the phone.

Verify by eye on the backpack that the colours are displayed correctly. The ADB test proves the UI and completion of the protocol upload, not the image on a real panel. `--hardware` can be combined with the upload, but it only additionally opens and cancels the clear dialog.

## Cancelling and retrying an upload

```sh
python3 tools/apk_smoke.py --fixtures --cancel-upload
```

This variant adds its own valid 64×64 GIF with 96 frames, starts a real upload, presses "Zrušit nahrávání" (Cancel upload) and then "Zkusit znovu" (Try again). It verifies the cancelled state without a false success and the success state after the retry and after navigation. The retry does write the test animation to the backpack; the script never presses the delete confirmation. Afterwards it cleans up only its own MediaStore GIFs on the phone.

## Import from the gallery / sharing

```sh
python3 tools/apk_smoke.py --fixtures --import
```

`--import` requires `--fixtures`. Using a real `ACTION_SEND` with a URI grant it tests a cold start of the app with a valid GIF and then sharing a malformed GIF into the running app. The valid file must create exactly one own copy and open the detail with a loaded preview and the send/edit buttons; the malformed one must show an error and create no copy. The script finds added copies only by this run's unique prefix and stores their exact URIs for cleanup. It deletes no other GIFs. `--fixtures --import --upload` can be combined.

Also verify the "Importovat GIF" (Import GIF) button with the Android picker manually: pick a GIF outside the GifPack folder, revoke the app's broad photo access (Android 10+) and verify that the picker grant produces an own copy. Cancelling the picker creates no file.
