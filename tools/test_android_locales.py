import re
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

    def test_plurals_and_placeholders_match_between_languages(self):
        """Every plurals block exists in both languages; cs has all four forms; placeholders agree."""
        placeholder = re.compile(r"%(?:\d+\$)?[sdf]|%%")
        for default_file in sorted(ANDROID_ROOT.glob("**/src/main/res/values/strings*.xml")):
            english_file = default_file.parent.parent / "values-en" / default_file.name
            czech, english = self._entries(default_file), self._entries(english_file)
            for name, (kind, cs_items) in czech.items():
                with self.subTest(file=default_file.name, key=name):
                    kind_en, en_items = english[name]
                    self.assertEqual(kind, kind_en, f"{name} is {kind} in cs but {kind_en} in en")
                    if kind == "plurals":
                        self.assertTrue({"one", "few", "many", "other"} <= set(cs_items),
                                        f"{name}: Czech needs one/few/many/other")
                        self.assertTrue({"one", "other"} <= set(en_items), f"{name}: English needs one/other")
                    cs_set = {tuple(sorted(placeholder.findall(text))) for text in cs_items.values()}
                    en_set = {tuple(sorted(placeholder.findall(text))) for text in en_items.values()}
                    self.assertEqual(cs_set, en_set, f"{name}: placeholders differ between cs and en")

    def test_copy_style_rules(self):
        """House style: informal Czech (ty), US English spelling, a real ellipsis character."""
        formal_czech = re.compile(r"\b(Zadejte|Vyberte|Zkuste|Vložte|Klepněte|Povolte|Zkontrolujte|Použijte|můžete|chcete)\b")
        for path in sorted(ANDROID_ROOT.glob("**/src/main/res/values*/strings*.xml")):
            english = path.parent.name == "values-en"
            for name, (_, items) in self._entries(path).items():
                for text in items.values():
                    with self.subTest(file=path.name, key=name):
                        self.assertNotIn("...", text, f"{name}: use the … character")
                        if english:
                            self.assertNotRegex(text, r"programme", f"{name}: spell it 'program'")
                        else:
                            self.assertIsNone(formal_czech.search(text), f"{name}: use informal 'ty'")

    @staticmethod
    def _entries(path: Path) -> dict:
        """name -> ("string"|"plurals", {quantity or "": text})."""
        entries = {}
        for node in ET.parse(path).getroot():
            if node.tag == "string":
                entries[node.attrib["name"]] = ("string", {"": "".join(node.itertext())})
            elif node.tag == "plurals":
                entries[node.attrib["name"]] = (
                    "plurals", {item.attrib["quantity"]: "".join(item.itertext()) for item in node})
        return entries

    @staticmethod
    def _string_names(path: Path) -> set[str]:
        root = ET.parse(path).getroot()
        names = [node.attrib["name"] for node in root if node.tag == "string"]
        if len(names) != len(set(names)):
            raise AssertionError(f"Duplicate string resource names in {path}")
        return set(names)


if __name__ == "__main__":
    unittest.main()
