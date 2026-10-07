import unittest
import xml.etree.ElementTree as ET
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[1]
ANDROID_ROOT = REPO_ROOT / "BatohManager"


class AndroidLocaleResourceTests(unittest.TestCase):
    def test_every_default_string_has_an_english_translation(self):
        default_files = sorted(ANDROID_ROOT.glob("**/src/main/res/values/strings*.xml"))
        self.assertTrue(default_files, "No Android string resource files were found")

        for default_file in default_files:
            with self.subTest(module=default_file.relative_to(ANDROID_ROOT)):
                english_file = default_file.parent.parent / "values-en" / default_file.name
                self.assertTrue(english_file.is_file(), f"Missing {english_file}")

                default_names = self._string_names(default_file)
                english_names = self._string_names(english_file)
                self.assertEqual(default_names, english_names)

    def test_locale_config_lists_czech_and_english(self):
        locale_file = ANDROID_ROOT / "app/src/main/res/xml/locale_config.xml"
        root = ET.parse(locale_file).getroot()
        locales = {
            item.attrib["{http://schemas.android.com/apk/res/android}name"]
            for item in root.findall("locale")
        }
        self.assertEqual({"cs", "en"}, locales)

    @staticmethod
    def _string_names(path: Path) -> set[str]:
        root = ET.parse(path).getroot()
        names = [node.attrib["name"] for node in root if node.tag == "string"]
        if len(names) != len(set(names)):
            raise AssertionError(f"Duplicate string resource names in {path}")
        return set(names)


if __name__ == "__main__":
    unittest.main()
