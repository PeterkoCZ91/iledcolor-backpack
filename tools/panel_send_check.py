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
ANOTHER = ("Jiný panel…", "Another panel…")
ALL = ("Všechny moje panely", "All my panels")
DONE = ("Hotovo", "Done")
SINGLE_DONE = ("Hotovo — obrázek je v panelu", "Done — image is on the panel")
FAILED = ("Nahrávání selhalo", "Upload failed")
ALL_SUMMARY = re.compile(r"(?:Hotovo na|Done on) (\d+) (?:z|of) (\d+) panel")
ACTIVE_SEND = ("Hledám tvoje panely…", "Looking for my panels…", "Panel 1 /", "Panel 2 /",
               "Příprava GIFu…", "Preparing GIF…", "Připojování panelu…", "Connecting to panel…",
               "Odesílání:", "Uploading:")


def result_rows(labels):
    """Sanitised per-panel outcomes; never write panel names or addresses to a report."""
    rows = []
    for label in labels:
        if not label.startswith(("✓ ", "✕ ", "– ")):
            continue
        name, _, status = label[2:].partition(": ")
        family = "ipixel" if name.startswith("LED_BLE_") else "backpack" if name.lower().startswith("iledcolor") else "unknown"
        outcome = ("sent" if status in ("Odesláno", "Sent") else
                   "already_present" if status in ("Už byl v panelu (potvrzeno)", "Already on panel (confirmed)") else
                   "not_in_range" if status in ("Není v dosahu", "Not in range") else "other")
        rows.append({"family": family, "outcome": outcome})
    return rows


def verify_all(labels, chosen, seen_active, ipixel_logs, backpack_logs):
    """Accept only a fresh result whose per-family sends have matching transfer log evidence."""
    summary = next((ALL_SUMMARY.search(label) for label in labels if ALL_SUMMARY.search(label)), None)
    if not (summary and chosen and seen_active):
        return None
    sent, total = map(int, summary.groups())
    breakdown = result_rows(labels)
    ipixel_sent = sum(row == {"family": "ipixel", "outcome": "sent"} for row in breakdown)
    backpack_sent = sum(row == {"family": "backpack", "outcome": "sent"} for row in breakdown)
    evidence = ipixel_logs >= ipixel_sent and backpack_logs >= backpack_sent
    rows_known = total > 0 and len(breakdown) == total and all(row["family"] != "unknown" for row in breakdown)
    complete = rows_known and sent == total and all(row["outcome"] == "sent" for row in breakdown) and evidence
    partial = rows_known and sent == ipixel_sent + backpack_sent and evidence and \
        all(row["outcome"] in ("sent", "not_in_range") for row in breakdown)
    return ("ok" if complete else "partial" if partial else "unverified",
            f"{sent}/{total} UI rows; iPixel logs {ipixel_logs}/{ipixel_sent}; backpack logs {backpack_logs}/{backpack_sent}",
            breakdown)


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

    def backpack_uploads(self):
        output = self.smoke.adb("logcat", "-d", "-s", "BackpackBLE:I")
        return sum(1 for line in output.splitlines() if "Chunks:" in line)

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
        print(f"round {index + 1}: checking {mode} (fresh connection: {fresh})", flush=True)
        if fresh:
            try:
                self.disconnect()
            except RuntimeError:
                pass
        before = self.uploads()
        before_backpack = self.backpack_uploads()
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
        outcome, detail = "timeout", ""
        chosen = False
        seen_active = False
        breakdown = []
        next_notice = started + 15
        while time.time() - started < 120:
            time.sleep(1.5)
            if time.time() >= next_notice:
                print(f"round {index + 1}: waiting for transfer result…", flush=True)
                next_notice += 15
            labels = self.labels()
            if any(c in labels for c in CHOOSER):
                if any(other in labels for other in ANOTHER):
                    smoke.click(next(other for other in ANOTHER if other in labels))
                    labels = self.labels()
                row = next((x for x in labels if mode != "all" and mode in x), None)
                if mode == "all":
                    all_label = next((label for label in ALL if label in labels), None)
                    if all_label is None:
                        outcome, detail = "no-target", "all-panels action unavailable"
                        break
                    smoke.click(all_label)
                elif row:
                    smoke.click(row)
                else:
                    outcome, detail = "no-target", "sheet had no row for " + mode
                    break
                chosen = True
                continue
            if mode == "all":
                if any(label.startswith(ACTIVE_SEND) for label in labels):
                    seen_active = True
                verified = verify_all(labels, chosen, seen_active, self.uploads() - before,
                                      self.backpack_uploads() - before_backpack)
                if verified:
                    outcome, detail, breakdown = verified
                    break
                # With exactly one available target, SendTargetPlan sends immediately without a sheet.
                if not chosen and seen_active and any(label in SINGLE_DONE for label in labels) and \
                        self.uploads() - before + self.backpack_uploads() - before_backpack >= 1:
                    outcome, detail = "ok", "1/1 single target; fresh transfer log"
                    break
            elif self.uploads() - before >= 1:
                outcome = "ok"
                break
            if (chosen or time.time() - started > 8) and self.any_label(FAILED, labels):
                outcome = "failed"
                detail = next((x for x in labels if "packet" in x or "paket" in x or "connect" in x.lower()), "")
                break
        elapsed = round(time.time() - started, 1)
        self.results.append({"round": index + 1, "mode": mode, "fresh_connection": fresh, "outcome": outcome,
                             "seconds": elapsed, "detail": detail, "panel_outcomes": breakdown})
        print(f"round {index + 1}: {mode:8} fresh={fresh!s:5} -> {outcome} ({elapsed}s) {self.results[-1]['detail']}", flush=True)
        if outcome == "failed":
            time.sleep(2)  # let the app settle before the next round


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--rounds", type=int, default=8)
    parser.add_argument("--sizes", nargs="*", default=["32 × 32", "64 × 16"], help="panel sizes shown in the sheet")
    parser.add_argument("--all-only", action="store_true", help="Send only to all picked panels currently in range")
    args = parser.parse_args()
    check = PanelCheck()
    smoke = check.smoke
    smoke.adb("logcat", "-c")
    smoke.adb("shell", "am", "start", "-n", PACKAGE + "/" + apk_smoke.ACTIVITY)
    smoke.tab("navigation_panel")
    modes = ["all"] if args.all_only else args.sizes + ["all"]
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
