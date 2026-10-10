import contextlib
import importlib.util
import io
import json
import sys
import tempfile
import unittest
from pathlib import Path
from unittest import mock
import xml.etree.ElementTree as ET

MODULE_PATH = Path(__file__).with_name("apk_smoke.py")
spec = importlib.util.spec_from_file_location("apk_smoke", MODULE_PATH)
apk_smoke = importlib.util.module_from_spec(spec)
spec.loader.exec_module(apk_smoke)


class SmokeHarnessTests(unittest.TestCase):
    def test_adb_retries_transient_failure_but_not_multi_device(self):
        results = [mock.Mock(returncode=1, stderr="error: closed", stdout=""),
                   mock.Mock(returncode=0, stderr="", stdout="ok")]
        with mock.patch.object(apk_smoke.subprocess, "run", side_effect=results), \
                mock.patch.object(apk_smoke.time, "sleep"):
            self.assertEqual(apk_smoke.Smoke().adb("shell", "true"), "ok")
        multi = mock.Mock(returncode=1, stderr="adb: more than one device/emulator", stdout="")
        with mock.patch.object(apk_smoke.subprocess, "run", return_value=multi) as run:
            with self.assertRaisesRegex(RuntimeError, "More than one ADB device"):
                apk_smoke.Smoke().adb("shell", "true")
            self.assertEqual(run.call_count, 1)

    def test_click_resolves_enabled_app_owned_clickable_parent(self):
        tree = ET.fromstring(
            '<hierarchy><node package="io.github.peterkocz91.gifpack" clickable="true" enabled="true" '
            'bounds="[10,20][110,80]"><node package="io.github.peterkocz91.gifpack" text="Send" '
            'clickable="false" enabled="true" bounds="[20,30][40,50]"/></node></hierarchy>'
        )
        smoke = apk_smoke.Smoke()
        smoke.wait = lambda predicate, description: tree
        commands = []
        smoke.adb = lambda *args, **kwargs: commands.append(args) or ""

        smoke.click("Send")

        self.assertEqual([("shell", "input", "tap", "60", "50")], commands)
        leaf = list(tree.iter("node"))[1]
        resolved = apk_smoke.Smoke.clickable_node(tree, leaf)
        self.assertEqual("[10,20][110,80]", resolved.get("bounds"))

    def test_see_matches_accessibility_description_with_state_suffix(self):
        tree = ET.fromstring(
            '<hierarchy><node package="io.github.peterkocz91.gifpack" '
            'content-desc="Batoh, Nepřipojeno" /></hierarchy>'
        )
        smoke = apk_smoke.Smoke()
        smoke.wait = lambda predicate, description, timeout=20: tree if predicate(tree) else None
        self.assertIs(tree, smoke.see("Batoh"))

    def test_picker_labels_choose_current_czech_or_english_text(self):
        self.assertEqual("Collection", apk_smoke.Smoke.localized_label(["Collection"], "Knihovna", "Collection"))
        self.assertEqual("Knihovna", apk_smoke.Smoke.localized_label(["Knihovna"], "Knihovna", "Collection"))

    def test_picker_label_accepts_state_suffix_of_home_tile(self):
        labels = ["Batoh, Nepřipojeno, zařízení example, firmware verze 14", "Knihovna"]
        self.assertEqual("Batoh", apk_smoke.Smoke.localized_label(labels, "Batoh", "Backpack"))
        with self.assertRaises(RuntimeError):
            apk_smoke.Smoke.localized_label(["Batohy"], "Batoh", "Backpack")

    def test_clickable_parent_must_belong_to_app_and_be_enabled(self):
        for package, enabled in (("com.android.systemui", "true"),
                                 ("io.github.peterkocz91.gifpack", "false")):
            with self.subTest(package=package, enabled=enabled):
                tree = ET.fromstring(
                    f'<hierarchy><node package="{package}" clickable="true" enabled="{enabled}" '
                    'bounds="[0,0][20,20]"><node package="io.github.peterkocz91.gifpack" text="Send" '
                    'clickable="false" enabled="true"/></node></hierarchy>'
                )
                leaf = list(tree.iter("node"))[1]
                self.assertIsNone(apk_smoke.Smoke.clickable_node(tree, leaf))

    def test_progress_detection_accepts_localized_counter_prefix(self):
        self.assertTrue(apk_smoke.has_upload_progress(["Nahrávání", "Odesílání: 55 %"]))
        self.assertFalse(apk_smoke.has_upload_progress(["Nahrávání zrušeno", "Hotovo"]))

    def test_shared_import_error_detection_matches_current_ui_copy(self):
        self.assertTrue(apk_smoke.has_shared_import_error(["Import sdíleného GIFu selhal", "Zavřít"]))
        self.assertTrue(apk_smoke.has_shared_import_error(["GIF je poškozený nebo překračuje podporované limity.", "Zavřít"]))
        self.assertTrue(apk_smoke.has_shared_import_error(["The GIF is damaged or exceeds the supported limits."]))
        self.assertFalse(apk_smoke.has_shared_import_error(["GIF byl importován do knihovny"]))

    def test_panel_state_detection_accepts_czech_and_english_ui(self):
        self.assertEqual((7, "Displej zapnutý"),
                         apk_smoke.backpack_panel_state(["Jas: 7 / 10", "Displej zapnutý"]))
        self.assertEqual((10, "Display off"),
                         apk_smoke.backpack_panel_state(["Brightness: 10 / 10", "Display off"]))
        self.assertIsNone(apk_smoke.backpack_panel_state(["Jas: stav není známý", "Displej zapnutý"]))

    def test_command_error_detection_accepts_czech_and_english_ui(self):
        self.assertTrue(apk_smoke.has_backpack_command_error(["Nastavení času se nepodařilo provést"]))
        self.assertTrue(apk_smoke.has_backpack_command_error(["Could not complete: Set time"]))
        self.assertFalse(apk_smoke.has_backpack_command_error(["Jas: 7 / 10", "Displej zapnutý"]))

    def test_selected_app_language_reads_checked_choice_from_settings_sheet(self):
        tree = ET.fromstring(
            '<hierarchy><node><node checked="true"><node text="Podle systému" /></node>'
            '<node checked="false"><node text="Čeština" /></node><node checked="false"><node text="English" /></node>'
            '</node></hierarchy>'
        )
        self.assertEqual("system", apk_smoke.selected_app_language(tree))

        tree = ET.fromstring(
            '<hierarchy><node><node checked="false"><node text="System default" /></node>'
            '<node checked="false"><node text="Čeština" /></node><node checked="true"><node text="English" /></node>'
            '</node></hierarchy>'
        )
        self.assertEqual("en", apk_smoke.selected_app_language(tree))

    def test_cleanup_deletes_and_verifies_only_registered_fixture_uris(self):
        smoke = apk_smoke.Smoke()
        own_uri = "content://media/external/images/media/817"
        smoke.fixture_uris = [own_uri]
        calls = []

        def fake_adb(*args, **kwargs):
            calls.append(args)
            return "No result found." if args[1] == "content" and args[2] == "query" else ""

        smoke.adb = fake_adb
        self.assertTrue(smoke.cleanup_fixtures())
        self.assertIn(("shell", "content", "delete", "--uri", own_uri), calls)
        self.assertIn(("shell", "content", "query", "--uri", own_uri + "?includePending=1",
                       "--projection", "_id"), calls)
        self.assertEqual(2, len(calls))

    def test_cli_requires_fixtures_for_device_data_operations(self):
        cases = (("--upload", "--upload and --cancel-upload require --fixtures"),
                 ("--cancel-upload", "--upload and --cancel-upload require --fixtures"),
                 ("--import", "--import requires --fixtures"))
        for flag, expected in cases:
            with self.subTest(flag=flag), mock.patch.object(sys, "argv", ["apk_smoke.py", flag]):
                stderr = io.StringIO()
                with contextlib.redirect_stderr(stderr), self.assertRaises(SystemExit) as raised:
                    apk_smoke.main()
                self.assertEqual(2, raised.exception.code)
                self.assertIn(expected, stderr.getvalue())

    def test_failure_report_does_not_persist_exception_contents(self):
        sensitive = "secret=abc /home/example/private device-output"

        class FailingSmoke:
            checks = []
            fixture_uris = []
            dump_path = "/sdcard/smoke.xml"

            def __init__(self, serial=None):
                pass

            def run(self, *args, **kwargs):
                raise RuntimeError(sensitive)

            def cleanup_fixtures(self):
                return True

            def crash_check(self):
                pass

            def adb(self, *args, **kwargs):
                return ""

        with tempfile.TemporaryDirectory() as output_dir:
            argv = ["apk_smoke.py", "--output", output_dir]
            stdout, stderr = io.StringIO(), io.StringIO()
            with mock.patch.object(sys, "argv", argv), mock.patch.object(apk_smoke, "Smoke", FailingSmoke), \
                    contextlib.redirect_stdout(stdout), contextlib.redirect_stderr(stderr):
                self.assertEqual(1, apk_smoke.main())
            report_path = next(Path(output_dir).glob("*.json"))
            report = json.loads(report_path.read_text())
            serialized = report_path.read_text() + stdout.getvalue() + stderr.getvalue()
            self.assertEqual("Smoke run failed", report["error"])
            self.assertEqual("RuntimeError", report["error_type"])
            self.assertNotIn("abc", serialized)
            self.assertNotIn("/home/", serialized)
            self.assertNotIn("device-output", serialized)


if __name__ == "__main__":
    unittest.main()
