#!/usr/bin/env python3
"""Repeated hardware check of sending library GIFs to the user's own panels (needs panels in range).

Each round picks a different GIF tile in the app's library picker and sends it to one panel (chosen by its
size in the "Where to send?" sheet) or to all of the user's panels. Only reads the UI hierarchy before every tap;
it never clears panel memory, deletes files or connects to panels the user did not pick in the app.

    ANDROID_SERIAL=<serial> python3 tools/panel_send_check.py --rounds 8
"""
import argparse
import datetime
import importlib.util
import json
import re
import sys
import time
from pathlib import Path

HERE = Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location("apk_smoke", HERE / "apk_smoke.py")
apk_smoke = importlib.util.module_from_spec(spec)
spec.loader.exec_module(apk_smoke)
PACKAGE = apk_smoke.PACKAGE

PICK = ("Odeslat GIF", "Send a GIF")
SOURCE_COLLECTION = ("Ze sbírky", "From my collection")
DISCONNECT = ("Odpojit panel", "Disconnect panel")
CHOOSER = ("Kam poslat?", "Where to send?")
DONE = ("Hotovo", "Done")
FAILED = ("Nahrávání selhalo", "Upload failed")


def tiles(tree):
    """Clickable, unlabeled grid cards of the picker sheet, top to bottom."""
    found = [n for n in tree.iter("node") if n.get("package") == PACKAGE and n.get("clickable") == "true"
             and not (n.get("text") or n.get("content-desc"))]
    def top(node):
        match = re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.get("bounds", ""))
        return (int(match.group(2)), int(match.group(1))) if match else (0, 0)
    return sorted(found, key=top)


class PanelCheck:
    def __init__(self):
        self.smoke = apk_smoke.Smoke()
        self.results = []

    def labels(self):
        return [label for label in self.smoke.labels(self.smoke.tree()) if label]

    def any_label(self, options, labels=None):
        labels = labels if labels is not None else self.labels()
        return next((x for x in labels if any(x.startswith(o) for o in options)), None)

    def uploads(self):
        """Completed iPixel transfers logged by the app; the UI keeps old "Done" texts, the log does not."""
        output = self.smoke.adb("logcat", "-d", "-s", "BackpackBLE:I")
        return sum(1 for line in output.splitlines() if "iPixel upload:" in line)

    def swipe_down(self):
        width, height = self.smoke.screen_size()
        self.smoke.adb("shell", "input", "swipe", str(width // 2), str(int(height * 0.3)),
                       str(width // 2), str(int(height * 0.75)))

    def reach(self, options):
        """Scroll the panel screen until one of [options] is on screen, then return its label."""
        for direction in (self.swipe_down, self.smoke.swipe_up):
            for _ in range(5):
                label = next((o for o in options if o in self.labels()), None)
                if label:
                    return label
                direction()
        raise RuntimeError("Control not found on the panel screen: " + options[0])

    def disconnect(self):
        if not any(x in self.labels() for x in ("Panel připojen", "Panel connected")):
            return
        self.smoke.click(self.reach(DISCONNECT))
        time.sleep(3)

    def round(self, index, mode, fresh):
        smoke = self.smoke
        if fresh:
            try:
                self.disconnect()
            except RuntimeError:
                pass
        before = self.uploads()
        entry = self.reach(PICK)
        smoke.click(entry)
        smoke.wait(lambda tree: any(o in smoke.labels(tree) for o in SOURCE_COLLECTION), "source sheet", timeout=10)
        labels = self.labels()
        smoke.click(next(o for o in SOURCE_COLLECTION if o in labels))
        smoke.wait(lambda tree: len(tiles(tree)) > 0, "GIF tiles in the picker")
        for _ in range(index // 6):  # reach further GIFs for variety
            smoke.swipe_up()
        grid = tiles(smoke.tree())
        smoke.tap_node(smoke.tree(), grid[index % max(1, len(grid))])
        started = time.time()
        expected = 2 if mode == "all" else 1
        outcome, detail = "timeout", ""
        chosen = False
        while time.time() - started < 120:
            time.sleep(1.5)
            labels = self.labels()
            if any(c in labels for c in CHOOSER):
                row = next((x for x in labels if mode != "all" and mode in x), None)
                if mode == "all":
                    smoke.click("Všechny moje panely" if "Všechny moje panely" in labels else "All my panels")
                elif row:
                    smoke.click(row)
                else:
                    outcome, detail = "no-target", "sheet had no row for " + mode
                    break
                chosen = True
                continue
            logged = self.uploads() - before
            if logged >= expected:
                outcome = "ok"
                detail = next((x for x in labels if re.search(r"\d+ (of|z) \d+", x)), "")
                break
            if (chosen or time.time() - started > 8) and self.any_label(FAILED, labels):
                outcome = "failed"
                detail = next((x for x in labels if "packet" in x or "paket" in x or "connect" in x.lower()), "")
                break
        elapsed = round(time.time() - started, 1)
        self.results.append({"round": index + 1, "mode": mode, "fresh_connection": fresh, "outcome": outcome,
                             "seconds": elapsed, "detail": re.sub(r"LED_BLE_\w+", "<panel>", detail)})
        print(f"round {index + 1}: {mode:8} fresh={fresh!s:5} -> {outcome} ({elapsed}s) {self.results[-1]['detail']}")
        if outcome == "failed":
            time.sleep(2)  # let the app settle before the next round


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--rounds", type=int, default=8)
    parser.add_argument("--sizes", nargs="*", default=["32 × 32", "64 × 16"], help="panel sizes shown in the sheet")
    args = parser.parse_args()
    check = PanelCheck()
    smoke = check.smoke
    smoke.adb("logcat", "-c")
    smoke.adb("shell", "am", "start", "-n", PACKAGE + "/" + apk_smoke.ACTIVITY)
    smoke.tab("navigation_panel")
    modes = args.sizes + ["all"]
    for index in range(args.rounds):
        check.round(index, modes[index % len(modes)], fresh=index % 2 == 0)
    ok = sum(1 for r in check.results if r["outcome"] == "ok")
    print(f"{ok}/{len(check.results)} rounds succeeded")
    out = HERE.parent / "artifacts" / "panel-send"
    out.mkdir(parents=True, exist_ok=True)
    destination = out / (datetime.datetime.now().strftime("%Y%m%d-%H%M%S") + ".json")
    destination.write_text(json.dumps(check.results, ensure_ascii=False, indent=2) + "\n")
    print("Report:", destination)
    return 0 if ok == len(check.results) else 1


if __name__ == "__main__":
    sys.exit(main())
