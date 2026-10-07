#!/usr/bin/env python3
"""Bounded UI smoke test; hardware connection is opt-in, deletion is never confirmed."""
import argparse
import base64
import datetime
import json
import random
from pathlib import Path
import re
import subprocess
import sys
import time
import uuid
import xml.etree.ElementTree as ET

PACKAGE = "io.github.peterkocz91.gifpack"
# Kotlin namespace of the launcher activity (differs from the application id)
ACTIVITY = "com.batoh.manager.MainActivity"
ROOT = Path(__file__).resolve().parent.parent
# Two animated 64x64 RGB frames, generated once with Pillow; no runtime image dependency.
VALID_GIF = base64.b64decode(
    "R0lGODlhQABAAIEAAP8AAAAAAAAAAAAAACH/C05FVFNDQVBFMi4wAwEAAAAh+QQAFAAAACwAAAAAQABAAAAIaQABCBxIsKDBgwgTKlzIsKHDhxAjSpxIsaLFixgzatzIsaPHjyBDihxJsqTJkyhTqlzJsqXLlzBjypxJs6bNmzhz6tzJs6fPn0CDCh1KtKjRo0iTKl3KtKnTp1CjSp1KtarVq1izagUQEAAh+QQBFAABACwAAAAAQABAAIEAAP8AAAAAAAAAAAAIaQABCBxIsKDBgwgTKlzIsKHDhxAjSpxIsaLFixgzatzIsaPHjyBDihxJsqTJkyhTqlzJsqXLlzBjypxJs6bNmzhz6tzJs6fPn0CDCh1KtKjRo0iTKl3KtKnTp1CjSp1KtarVq1izagUQEAA7"
)
# Valid container/header + first frame descriptor, invalid LZW code size and truncated block.
# No valid first frame exists: accepting later/truncated frames cannot hide this regression.
MALFORMED_GIF = bytes.fromhex(
    "47494638396140004000800000ff00000000ff2c000000004000400000ffff00"
)
DETAIL_ERROR = "GIF se nepodařilo zobrazit. Soubor může být poškozený; zkus ho stáhnout znovu."


def make_cancel_gif(seed):
    """Build a large, valid 64x64 GIF fixture without runtime dependencies."""
    rng = random.Random(seed)
    output = bytearray(b"GIF89a\x40\x00\x40\x00\x80\x00\x00\x00\x00\x00\xff\x00\x00")
    # Roughly 450 KB keeps upload active long enough for a UI-driven cancel.
    frames = 96
    for _ in range(frames):
        output.extend((0x21, 0xF9, 4, 4, 5, 0, 0, 0,
                       0x2C, 0, 0, 0, 0, 64, 0, 64, 0, 0))
        pixels = bytes(rng.getrandbits(1) for _ in range(64 * 64))
        codes = [256]
        for offset, value in enumerate(pixels):
            codes.append(value)
            if (offset + 1) % 64 == 0:
                codes.append(256)  # bound the dictionary so every code stays nine bits
        codes.append(257)
        encoded = bytearray()
        bit_buffer = 0
        bit_count = 0
        for code in codes:
            bit_buffer |= code << bit_count
            bit_count += 9
            while bit_count >= 8:
                encoded.append(bit_buffer & 255)
                bit_buffer >>= 8
                bit_count -= 8
        if bit_count:
            encoded.append(bit_buffer & 255)
        output.append(8)
        for offset in range(0, len(encoded), 255):
            block = encoded[offset:offset + 255]
            output.append(len(block))
            output.extend(block)
        output.append(0)
    output.append(0x3B)
    return bytes(output)


def has_upload_progress(labels):
    """Return whether the app exposes its localized upload-progress status."""
    return any(label.startswith("Odesílání:") for label in labels)


SHARED_IMPORT_ERRORS = (
    "Import sdíleného GIFu selhal",
    "GIF je poškozený nebo překračuje podporované limity.",
    "The GIF is damaged or exceeds the supported limits.",
)


def has_shared_import_error(labels):
    """Return whether a shared-import failure message (typed, localized) is visible."""
    return any(message in labels for message in SHARED_IMPORT_ERRORS)


def backpack_panel_state(labels):
    brightness = None
    for label in labels:
        match = re.fullmatch(r"(?:Jas|Brightness): ([1-9]|10) / 10", label)
        if match:
            brightness = match.group(1)
            break
    screen = next((label for label in labels
                   if label in {"Displej zapnutý", "Displej vypnutý", "Display on", "Display off"}), None)
    if brightness is None or screen is None:
        return None
    return int(brightness), screen


def has_backpack_command_error(labels):
    return any("se nepodařilo provést" in label or "Could not complete:" in label for label in labels)


def selected_app_language(tree):
    parents = {child: parent for parent in tree.iter("node") for child in parent}
    language_tags = {
        "Podle systému": "system", "System default": "system",
        "Čeština": "cs", "English": "en",
    }
    for node in tree.iter("node"):
        language = language_tags.get(node.get("text", ""))
        parent = parents.get(node)
        if language and parent is not None and parent.get("checked") == "true":
            return language
    return None


class Smoke:
    def __init__(self, serial=None):
        self.base = ["adb"] + (["-s", serial] if serial else [])
        self.checks = []
        self.dump_path = f"/sdcard/batoh-smoke-{int(time.time())}.xml"
        self.log_since = None
        self.fixture_uris = []
        self.current_step = "initializing"

    def adb(self, *args, timeout=30):
        result = subprocess.run(self.base + list(args), capture_output=True, text=True, timeout=timeout)
        if result.returncode:
            # Do not include arbitrary device output, serials, paths or credentials in reports.
            raise RuntimeError(f"ADB operation failed: {args[0]}")
        return result.stdout

    def record(self, name, status="passed", detail=None):
        self.current_step = name
        entry = {"name": name, "status": status}
        if detail:
            entry["detail"] = detail
        self.checks.append(entry)
        print(f"{status}: {name}")

    def tree(self):
        self.adb("shell", "rm", "-f", self.dump_path)
        output = self.adb("shell", "uiautomator", "dump", self.dump_path, timeout=20)
        if "dumped to:" not in output:
            raise ET.ParseError("UIAutomator produced no fresh hierarchy")
        return ET.fromstring(self.adb("exec-out", "cat", self.dump_path))

    @staticmethod
    def labels(tree):
        return [node.get(key, "") for node in tree.iter("node")
                if node.get("package") == PACKAGE for key in ("text", "content-desc")]

    @staticmethod
    def matches_label(node, label):
        description = node.get("content-desc", "")
        return (node.get("text") == label or description == label
                or description.startswith(label + ","))

    @staticmethod
    def localized_label(labels, czech, english):
        # Controls with a state suffix ("Batoh, Nepřipojeno, …") match like matches_label does.
        for label in (czech, english):
            if label in labels or any(item.startswith(label + ",") for item in labels):
                return label
        raise RuntimeError("Expected localized control is missing")

    def wait(self, predicate, description, timeout=20):
        self.current_step = description
        deadline = time.monotonic() + timeout
        while time.monotonic() < deadline:
            try:
                tree = self.tree()
                if any(node.get("package") == PACKAGE for node in tree.iter("node")) and predicate(tree):
                    return tree
            except (ET.ParseError, subprocess.TimeoutExpired):
                pass
            time.sleep(0.4)
        raise RuntimeError(f"UI timeout: {description} (unlock phone and handle permission dialogs manually)")

    def see(self, label, timeout=20):
        return self.wait(
            lambda tree: any(node.get("package") == PACKAGE and self.matches_label(node, label)
                             for node in tree.iter("node")),
            label,
            timeout,
        )

    def click(self, label):
        # Always query fresh bounds from a node belonging to this app.
        tree = self.wait(
            lambda tree: any(self.clickable_node(tree, node) is not None for node in tree.iter("node")
                             if node.get("package") == PACKAGE and self.matches_label(node, label)),
            "enabled control: " + label,
        )
        matching_nodes = [node for node in tree.iter("node") if node.get("package") == PACKAGE
                          and self.matches_label(node, label)]
        for node in matching_nodes:
            if self.tap_node(tree, node):
                return
        raise RuntimeError(f"No enabled app control: {label}")

    @staticmethod
    def clickable_node(tree, node):
        parents = {child: parent for parent in tree.iter() for child in parent}
        while node.get("clickable") != "true" and node in parents:
            node = parents[node]
        if node.get("package") != PACKAGE or node.get("enabled") != "true" or node.get("clickable") != "true":
            return None
        return node

    def tap_node(self, tree, node):
        node = self.clickable_node(tree, node)
        if node is None:
            return False
        coords = re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.get("bounds", ""))
        if coords:
            left, top, right, bottom = map(int, coords.groups())
            if right > left and bottom > top:
                self.adb("shell", "input", "tap", str((left + right) // 2), str((top + bottom) // 2))
                return True
        return False

    def click_related(self, title, button_description):
        tree = self.see(title)
        parents = {child: parent for parent in tree.iter() for child in parent}
        title_node = next(node for node in tree.iter("node") if node.get("package") == PACKAGE
                          and self.matches_label(node, title))
        node = title_node
        while node in parents:
            candidates = [child for child in node.iter("node") if child.get("package") == PACKAGE
                          and button_description == child.get("content-desc")]
            if len(candidates) == 1 and self.tap_node(tree, candidates[0]):
                return
            node = parents[node]
        raise RuntimeError("Could not identify send button belonging to the valid fixture card")

    def back(self):
        self.wait(lambda _: True, "app before Back")
        self.adb("shell", "input", "keyevent", "KEYCODE_BACK")

    def crash_check(self):
        if not self.log_since:
            return
        logs = self.adb("logcat", "-b", "crash", "-d", "-v", "threadtime", "-T", self.log_since)
        # Crash buffer contains complete Java/native crash blocks; package identity narrows failures.
        if PACKAGE in logs and re.search(r"FATAL EXCEPTION|Fatal signal|>>>.*" + re.escape(PACKAGE) + r".*<<<", logs):
            raise RuntimeError("Application Java/native crash recorded during this smoke run")
        if not self.adb("shell", "pidof", PACKAGE).strip():
            raise RuntimeError("Application process disappeared during smoke run")
        self.record("no Java/native fatal crash during run")

    def seed_fixtures(self, metadata, cancel_upload=False):
        sdk = int(self.adb("shell", "getprop", "ro.build.version.sdk").strip())
        permission = "READ_MEDIA_IMAGES" if sdk >= 33 else "READ_EXTERNAL_STORAGE"
        if not re.search(r"android\.permission\." + permission + r":\s*granted=true", metadata):
            raise RuntimeError("--fixtures requires full image read permission; grant it manually in app settings, then retry")
        if sdk < 29:
            raise RuntimeError("--fixtures MediaStore seeding requires Android 10 or newer")
        prefix = "BatohSmoke_" + uuid.uuid4().hex[:12]
        fixtures = []
        fixtures_to_seed = [("valid", VALID_GIF), ("malformed", MALFORMED_GIF)]
        if cancel_upload:
            fixtures_to_seed.append(("cancel", make_cancel_gif(int(prefix[-12:], 16))))
        for suffix, data in fixtures_to_seed:
            title = prefix + "_" + suffix + ".gif"
            output = self.adb("shell", "content", "insert", "--uri", "content://media/external/images/media",
                              "--bind", "_display_name:s:" + title, "--bind", "mime_type:s:image/gif",
                              "--bind", "relative_path:s:Pictures/GifPack", "--bind", "is_pending:i:1")
            match = re.search(r"content://media/[^\s]+/\d+", output)
            if match:
                uri = match.group(0)
            else:
                # Android 16's content CLI does not print insert's returned URI.
                # Match only this generated name, including unpublished pending rows.
                rows = self.adb("shell", "content", "query", "--uri",
                                "content://media/external/images/media?includePending=1",
                                "--projection", "_id", "--where", f"\"_display_name = '{title}'\"")
                ids = re.findall(r"\b_id=(\d+)\b", rows)
                if len(ids) != 1:
                    raise RuntimeError("MediaStore fixture insert could not be verified")
                uri = "content://media/external/images/media/" + ids[0]
            # Register each exact URI immediately, even if the subsequent write fails.
            self.fixture_uris.append(uri)
            result = subprocess.run(self.base + ["shell", "content", "write", "--uri", uri],
                                    input=data, capture_output=True, timeout=30)
            write_output = result.stdout + result.stderr
            if result.returncode or b"Error" in write_output or b"Exception" in write_output:
                raise RuntimeError("MediaStore fixture write failed")
            self.adb("shell", "content", "update", "--uri", uri, "--bind", "is_pending:i:0")
            fixtures.append((title, suffix))
        self.record(f"{len(fixtures)} isolated MediaStore fixtures inserted")
        return fixtures

    def fixture_checks(self, fixtures):
        # Newest library rows are the two fixtures; no share/upload/send control is clicked.
        for title, kind in fixtures:
            self.see(title)
            self.click(title)
            if kind != "malformed":
                self.wait(lambda tree: any(label in self.labels(tree) for label in
                                           ("Náhled GIFu načten", "GIF preview loaded")),
                          "valid GIF preview loaded")
                if any(label in self.labels(self.tree()) for label in
                       (DETAIL_ERROR, "Could not display the GIF. The file may be damaged; try downloading it again.")):
                    raise RuntimeError("Valid fixture failed to decode")
                self.record("valid 64x64 GIF detail decode completes")
            else:
                # Broken library items open the removal dialog with the reason (since v50). The harness
                # only checks the text and presses Cancel; it never confirms a deletion.
                self.wait(lambda tree: any(label in self.labels(tree) for label in
                                           ("Odebrat GIF z knihovny?", "Remove GIF from the library?")),
                          "malformed GIF removal dialog")
                self.wait(lambda tree: any(label in self.labels(tree) for label in
                                           ("GIF je poškozený nebo používá nepodporované funkce.",
                                            "The GIF is damaged or uses unsupported features.")),
                          "malformed GIF friendly reason")
                self.click(self.localized_label(self.labels(self.tree()), "Zrušit", "Cancel"))
                self.record("malformed GIF shows friendly reason and removal is cancelled")
            self.crash_check()
            self.back()
            self.wait(lambda tree: any(label in self.labels(tree) for label in ("Moje sbírka", "My Collection")),
                      "collection after GIF detail")
        self.record("fixture gallery/detail navigation remains responsive")

    def cleanup_fixtures(self):
        failed = False
        for uri in self.fixture_uris:
            try:
                self.adb("shell", "content", "delete", "--uri", uri)
                remaining = self.adb("shell", "content", "query", "--uri", uri + "?includePending=1",
                                     "--projection", "_id")
                if "No result found" not in remaining:
                    failed = True
            except (RuntimeError, subprocess.SubprocessError, OSError):
                failed = True
        if self.fixture_uris:
            self.record("cleanup only this run's inserted fixture URIs", "failed" if failed else "passed")
        return not failed

    def upload_fixture(self, title):
        self.click_related(title, "Odeslat GIF do batohu")
        self.see("LED batoh")
        self.see("Hotovo — obrázek je v batohu", timeout=90)
        self.see(title)
        self.record("real valid fixture upload reports confirmed success")
        self.back()
        self.see("Moje sbírka")
        self.click("Domů")
        self.click("Batoh")
        self.see("Hotovo — obrázek je v batohu")
        self.see(title)
        self.record("successful upload result persists after leaving and reopening backpack UI")
        self.back()
        self.see("Informace o aplikaci")
        self.click("Knihovna")
        self.see("Moje sbírka")

    def cancel_and_retry_upload(self, title):
        self.click_related(title, "Odeslat GIF do batohu")
        self.see("LED batoh")
        self.wait(lambda tree: has_upload_progress(self.labels(tree)),
                  "upload progress", timeout=90)
        self.click("Zrušit nahrávání")
        self.see("Nahrávání zrušeno", timeout=30)
        self.record("in-flight upload cancels without reporting success")
        self.click("Zkusit znovu")
        self.see("Hotovo — obrázek je v batohu", timeout=180)
        self.see(title)
        self.record("cancelled upload reconnects and succeeds when retried")
        self.back()
        self.see("Moje sbírka")
        self.click("Domů")
        self.click("Batoh")
        self.see("Hotovo — obrázek je v batohu")
        self.see(title)
        self.record("retried upload result persists after navigation")
        self.back()
        self.see("Informace o aplikaci")
        self.click("Knihovna")
        self.see("Moje sbírka")

    def imported_rows(self, title):
        # Names contain only our generated ASCII UUID prefix, never arbitrary user input.
        prefix = title.removesuffix(".gif") + "_"
        if not re.fullmatch(r"BatohSmoke_[0-9a-f]+_(valid|malformed)_", prefix):
            raise RuntimeError("Unexpected fixture prefix")
        output = self.adb("shell", "content", "query", "--uri", "content://media/external/images/media",
                          "--projection", "_id:_display_name", "--where", f'"_display_name LIKE \'{prefix}%\'"')
        return set("content://media/external/images/media/" + match.group(1)
                   for match in re.finditer(r"_id=(\d+),\s*_display_name=" + re.escape(prefix), output))

    def import_checks(self, seeded):
        for title, kind in seeded:
            if kind not in ("valid", "malformed"):
                continue
            uri = self.fixture_uris[next(i for i, fixture in enumerate(seeded) if fixture[0] == title)]
            before = self.imported_rows(title)
            if kind == "valid":
                self.adb("shell", "am", "force-stop", PACKAGE)
            self.adb("shell", "am", "start", "-n", PACKAGE + "/" + ACTIVITY,
                     "-a", "android.intent.action.SEND", "-t", "image/gif",
                     "--eu", "android.intent.extra.STREAM", uri, "-f", "0x20000001")
            try:
                if kind == "valid":
                    self.see("GIF byl importován do knihovny")
                    self.see("Náhled GIFu načten")
                    self.see("Odeslat do batohu")
                    self.see("Upravit pro batoh")
                    self.record("cold ACTION_SEND imports valid GIF and opens send/edit detail")
                else:
                    self.wait(lambda tree: has_shared_import_error(self.labels(tree)), "malformed import error")
                    self.see("Moje sbírka")
                    self.record("warm ACTION_SEND rejects malformed GIF with visible error")
            finally:
                after = self.imported_rows(title)
                added = after - before
                # Track own copied rows before any assertion so failures still clean them up.
                self.fixture_uris.extend(sorted(added))
            if kind == "valid" and len(added) != 1:
                raise RuntimeError("Valid import did not create exactly one own library copy")
            if kind == "malformed" and added:
                raise RuntimeError("Malformed import unexpectedly copied data into the library")
            self.crash_check()
            # Close the persistent status banner through its app-labelled action.
            self.click("Zavřít")
            if kind == "valid":
                self.back()
                self.see("Moje sbírka")
        self.record("import validation creates only valid copy; malformed creates no row")

    def run(self, apk=None, hardware=False, fixtures=False, upload=False, imports=False,
            cancel_upload=False, locale_sweep=False):
        if self.adb("get-state").strip() != "device":
            raise RuntimeError("ADB device is not authorized")
        if apk:
            output = self.adb("install", "-r", str(apk), timeout=120)
            if "Success" not in output:
                raise RuntimeError("APK update install failed")
            self.record("APK installed as update, data retained")
        metadata = self.adb("shell", "dumpsys", "package", PACKAGE)
        name = re.search(r"versionName=(\S+)", metadata)
        code = re.search(r"versionCode=(\d+)", metadata)
        if not name or not code:
            raise RuntimeError("Package/version metadata unavailable")
        version = f"{name.group(1)} ({code.group(1)})"
        seeded = self.seed_fixtures(metadata, cancel_upload) if fixtures else []
        self.log_since = self.adb("shell", "date '+%m-%d %H:%M:%S.000'").strip()
        # Start from a known disconnected session; never clear data or uninstall.
        self.adb("shell", "am", "force-stop", PACKAGE)
        self.adb("shell", "am", "start", "-n", PACKAGE + "/" + ACTIVITY)
        labels = self.labels(self.tree())
        about = self.localized_label(labels, "Informace o aplikaci", "About the app")
        settings = self.localized_label(labels, "Nastavení aplikace", "App settings")
        library = self.localized_label(labels, "Knihovna", "Collection")
        home = self.localized_label(labels, "Domů", "Home")
        backpack = self.localized_label(labels, "Batoh", "Backpack")
        library_screen = self.localized_label(labels, "Moje sbírka", "My Collection")
        language = "cs" if about == "Informace o aplikaci" else "en"
        # The picker button lives on the backpack screen, not on Home: choose its text by language.
        backpack_picker = "Vybrat GIF z knihovny" if language == "cs" else "Choose a GIF from your library"

        self.see(about)
        self.record("home screen opens")
        self.click(about)
        self.see(("Verze: " if language == "cs" else "Version: ") + version)
        self.record("displayed version matches installed APK", detail=version)
        self.click("OK")
        self.click(settings)
        self.see("Nastavení" if language == "cs" else "Settings")
        self.record("settings dialog opens")
        self.back()
        self.click(library)
        self.see(library_screen)
        self.record("library opens")
        if seeded:
            self.fixture_checks(seeded)
        if imports:
            self.import_checks(seeded)
        if upload:
            self.upload_fixture(next(title for title, kind in seeded if kind == "valid"))
        if cancel_upload:
            self.cancel_and_retry_upload(next(title for title, kind in seeded if kind == "cancel"))
        self.click(home)
        self.click(backpack)
        self.see("LED batoh" if language == "cs" else "LED Backpack")
        tree = self.see(backpack_picker)
        labels = self.labels(tree)
        if any(re.search(r"rotac|zrcadl|Hardcoded MAC|Target:", label, re.I) for label in labels):
            raise RuntimeError("Removed experimental controls remain visible")
        if any(label in labels for label in ("Skrýt diagnostiku", "Hide diagnostics")):
            raise RuntimeError("Diagnostics are expanded by default")
        self.record("backpack UI has library picker and collapsed diagnostics")
        self.click(backpack_picker)
        self.see("Klepnutím na obrázek ho nahraješ do batohu." if language == "cs"
                 else "Tap a GIF to upload it to your backpack.")
        self.record("picker opens app library (not external phone picker)")
        self.back()
        self.see("LED batoh" if language == "cs" else "LED Backpack")
        self.record("picker cancels without selecting or uploading")
        if hardware:
            if "Batoh připojen" not in self.labels(self.tree()):
                self.click("Připojit batoh")
            self.see("Batoh připojen", timeout=40)
            state_tree = self.wait(
                lambda tree: backpack_panel_state(self.labels(tree)) is not None,
                "backpack display state response", timeout=15)
            state_labels = self.labels(state_tree)
            panel_state = backpack_panel_state(state_labels)
            if has_backpack_command_error(state_labels):
                raise RuntimeError("Clock sync or display state command failed")
            self.record("clock sync and display state query completed",
                        detail=f"brightness={panel_state[0]}/10; {panel_state[1]}")
            self.click("Smazat obsah batohu")
            self.see("Smazat obsah batohu?")
            self.wait(lambda tree: any("GIFy v knihovně telefonu zůstanou zachované" in label
                                      for label in self.labels(tree)), "deletion explanation")
            self.click("Zrušit")
            self.wait(lambda tree: "Smazat obsah batohu?" not in self.labels(tree), "cancel deletion")
            self.record("delete confirmation opens and cancels (no deletion command)")
        else:
            self.record("hardware connection/deletion confirmation", "skipped", "Use --hardware with the backpack present")
        if locale_sweep:
            self.locale_sweep(seeded)
        remaining = "navigation during transfer and physical image quality"
        if not upload and not cancel_upload:
            remaining = "actual upload, upload cancel/retry, " + remaining
        elif not cancel_upload:
            remaining = "upload cancel/retry, " + remaining
        if not fixtures:
            remaining = "GIF decoding, " + remaining
        self.record(remaining, "manual", "See tools/APK_SMOKE.md")
        self.crash_check()

    def locale_sweep(self, fixtures):
        self.back()
        self.click("Nastavení aplikace")
        settings_tree = self.see("Nastavení")
        original_language = selected_app_language(settings_tree)
        if original_language not in {"system", "cs", "en"}:
            raise RuntimeError("Could not determine app language to restore after locale sweep")
        original_home_label = "Home" if "Home" in self.labels(settings_tree) else "Domů"
        try:
            self.click("English")
            self.wait(lambda tree: "App language" in self.labels(tree) or
                      "Search GIFs" in self.labels(tree),
                      "English locale applied", timeout=30)
            if "App language" in self.labels(self.tree()):
                self.back()
            self.see("Search GIFs", timeout=20)
            self.record("English home screen opens")

            self.click("Categories")
            self.see("Discover", timeout=30)
            self.record("English categories screen opens")
            self.click("Search")
            self.see("Search GIFs…", timeout=30)
            self.record("English search screen opens")
            self.click("Collection")
            self.see("My Collection", timeout=30)
            self.record("English collection screen opens")
            valid_fixture = next((title for title, kind in fixtures if kind == "valid"), None)
            if valid_fixture:
                self.click(valid_fixture)
                self.see("GIF preview loaded", timeout=20)
                self.see("Edit for backpack", timeout=20)
                self.record("English GIF detail screen opens")
                self.click("Edit for backpack")
                self.see("Edit GIF for backpack", timeout=20)
                self.see("Result preview · 64 × 64", timeout=30)
                self.see("Rotation", timeout=20)
                self.record("English GIF editor screen opens")
                self.back()
                self.back()
                self.see("My Collection", timeout=20)
            self.click("Home")
            self.click("Backpack")
            self.see("LED Backpack", timeout=20)
            self.see("Choose a GIF from your library", timeout=20)
            self.record("English backpack screen opens")
        finally:
            self.restore_app_language(original_language, original_home_label)

        self.click("Kategorie")
        self.see("Objevovat", timeout=30)
        self.record("Czech categories screen opens")
        self.click("Hledat")
        self.see("Hledat GIFy...", timeout=30)
        self.record("Czech search screen opens")
        self.click("Knihovna")
        self.see("Moje sbírka", timeout=30)
        self.record("Czech collection screen opens")
        valid_fixture = next((title for title, kind in fixtures if kind == "valid"), None)
        if valid_fixture:
            self.click(valid_fixture)
            self.see("Náhled GIFu načten", timeout=20)
            self.see("Upravit pro batoh", timeout=20)
            self.record("Czech GIF detail screen opens")
            self.click("Upravit pro batoh")
            self.see("Upravit GIF pro batoh", timeout=20)
            self.see("Náhled výsledku · 64 × 64", timeout=30)
            self.see("Rotace", timeout=20)
            self.record("Czech GIF editor screen opens")
            self.back()
            self.back()
            self.see("Moje sbírka", timeout=20)
        self.click("Domů")
        self.click("Batoh")
        self.see("LED batoh", timeout=20)
        self.see("Vybrat GIF z knihovny", timeout=20)
        self.record("Czech backpack screen opens")

    def restore_app_language(self, original_language, original_home_label):
        labels = self.labels(self.tree())
        if "App language" not in labels and "Jazyk aplikace" not in labels:
            if "Home" in labels:
                self.click("Home")
            elif "Domů" in labels:
                self.click("Domů")
            else:
                self.back()
            labels = self.labels(self.tree())
            if "App language" not in labels and "Jazyk aplikace" not in labels:
                self.click("App settings" if "Home" in labels else "Nastavení aplikace")

        english_ui = "App language" in self.labels(self.tree())
        restore_label = {
            "system": "System default" if english_ui else "Podle systému",
            "cs": "Čeština",
            "en": "English",
        }[original_language]
        self.see("App language" if english_ui else "Jazyk aplikace", timeout=20)
        self.click(restore_label)
        original_settings_title = "App language" if original_home_label == "Home" else "Jazyk aplikace"
        restored_tree = self.wait(
            lambda tree: original_home_label in self.labels(tree) or
            original_settings_title in self.labels(tree),
            "original locale restored", timeout=30)
        if original_settings_title in self.labels(restored_tree):
            self.back()
        self.see(original_home_label, timeout=30)
        labels = self.labels(self.tree())
        if "App language" in labels or "Jazyk aplikace" in labels:
            self.back()
        self.record("Original app language restored", detail=original_language)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", help="ADB serial; omit with exactly one connected phone")
    parser.add_argument("--apk", type=Path, help="Update-install an APK using adb install -r")
    parser.add_argument("--hardware", action="store_true", help="Connect real backpack; test deletion dialog CANCEL only")
    parser.add_argument("--fixtures", action="store_true", help="Seed two isolated valid/malformed GIFs, test details, delete ONLY seeded row URIs")
    parser.add_argument("--upload", action="store_true", help="Requires --fixtures: send valid fixture to the REAL backpack and test persistent success")
    parser.add_argument("--cancel-upload", action="store_true", help="Requires --fixtures: cancel a larger real upload, retry, and test persistent success")
    parser.add_argument("--locale-sweep", action="store_true", help="Visit English app screens, then restore the selected app language")
    parser.add_argument("--import", dest="imports", action="store_true", help="Requires --fixtures: test cold/warm ACTION_SEND valid and invalid import; clean only own copies")
    parser.add_argument("--output", type=Path, default=ROOT / "artifacts" / "apk-smoke", help="Report directory (local, ignored by Git)")
    args = parser.parse_args()
    if (args.upload or args.cancel_upload) and not args.fixtures:
        parser.error("--upload and --cancel-upload require --fixtures")
    if args.imports and not args.fixtures:
        parser.error("--import requires --fixtures")
    smoke = Smoke(args.serial)
    report = {"started_utc": datetime.datetime.now(datetime.timezone.utc).isoformat(), "hardware_enabled": args.hardware,
              "fixtures_enabled": args.fixtures}
    report["upload_enabled"] = args.upload
    report["cancel_upload_enabled"] = args.cancel_upload
    report["import_enabled"] = args.imports
    report["locale_sweep_enabled"] = args.locale_sweep
    exit_code = 0
    try:
        smoke.run(args.apk, args.hardware, args.fixtures, args.upload, args.imports, args.cancel_upload,
                  args.locale_sweep)
        report["status"] = "passed"
    except (RuntimeError, subprocess.SubprocessError, OSError, ET.ParseError) as error:
        exit_code = 1
        report["status"] = "failed"
        # Errors are controlled summaries; raw ADB/log/UI contents are deliberately not persisted.
        # Exceptions can accidentally contain device output, paths, or user data.
        # Keep reports useful for grouping failures without persisting raw messages.
        report["error"] = "Smoke run failed"
        report["error_type"] = type(error).__name__
        report["last_step"] = getattr(smoke, "current_step", "unknown")
        print(report["error"], file=sys.stderr)
        try:
            smoke.crash_check()
        except (RuntimeError, subprocess.SubprocessError, OSError) as crash_error:
            report["crash_check_error"] = type(crash_error).__name__
    finally:
        if not smoke.cleanup_fixtures():
            exit_code = 1
            report["status"] = "failed"
            report["cleanup_error"] = "Could not remove this run's inserted fixture rows; retry ADB connection"
        report["checks"] = smoke.checks
        args.output.mkdir(parents=True, exist_ok=True)
        destination = args.output / (datetime.datetime.now().strftime("%Y%m%d-%H%M%S") + ".json")
        destination.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n")
        print(f"Report: {destination.resolve()}")
        try:
            smoke.adb("shell", "rm", "-f", smoke.dump_path)
        except (RuntimeError, subprocess.SubprocessError, OSError):
            pass
    return exit_code


if __name__ == "__main__":
    sys.exit(main())
