#!/usr/bin/env python3
"""Pick a test PNG/JPG from the phone and send its one-frame GIF to all picked panels in range.

The script inserts one uniquely named MediaStore item and removes only that exact URI. It reads
the picker hierarchy before tapping, never picks another image, and never erases panel content.

    ANDROID_SERIAL=<test phone> python3 tools/static_image_send_check.py
    ANDROID_SERIAL=<test phone> python3 tools/static_image_send_check.py --image photo.jpg
"""
import argparse
import datetime
import importlib.util
import json
import re
import struct
import subprocess
import time
import uuid
import zlib
from pathlib import Path
from panel_send_check import ACTIVE_SEND, verify_all

HERE = Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location("apk_smoke", HERE / "apk_smoke.py")
apk_smoke = importlib.util.module_from_spec(spec)
spec.loader.exec_module(apk_smoke)
SUMMARY = re.compile(r"(?:Hotovo na|Done on) (\d+) (?:z|of) (\d+) panel")


def test_png():
    """An asymmetric, small image with only standard-library dependencies."""
    width = height = 64
    rows = b"".join(b"\0" + b"".join(bytes((255, 230 if x < 32 else 20,
        25 if y < 32 else 220, 30)) for x in range(width)) for y in range(height))

    def chunk(tag, data):
        return struct.pack(">I", len(data)) + tag + data + struct.pack(">I", zlib.crc32(tag + data) & 0xffffffff)

    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(rows)) + chunk(b"IEND", b""))


def insert_image(smoke, title, mime, data):
    uri = None
    try:
        output = smoke.adb("shell", "content", "insert", "--uri", "content://media/external/images/media",
                           "--bind", "_display_name:s:" + title, "--bind", "mime_type:s:" + mime,
                           "--bind", "relative_path:s:Pictures/GifPack", "--bind", "is_pending:i:1")
        match = re.search(r"content://media/[^\s]+/\d+", output)
        if match:
            uri = match.group(0)
        else:
            rows = smoke.adb("shell", "content", "query", "--uri",
                             "content://media/external/images/media?includePending=1", "--projection", "_id",
                             "--where", f"\"_display_name = '{title}'\"")
            ids = re.findall(r"\b_id=(\d+)\b", rows)
            if len(ids) != 1:
                raise RuntimeError("Fixture insert could not be verified")
            uri = "content://media/external/images/media/" + ids[0]
        result = subprocess.run(smoke.base + ["shell", "content", "write", "--uri", uri],
                                input=data, capture_output=True, timeout=30)
        if result.returncode or b"Error" in result.stdout + result.stderr:
            raise RuntimeError("Fixture write failed")
        smoke.adb("shell", "content", "update", "--uri", uri, "--bind", "is_pending:i:0")
        return uri
    except Exception:
        if uri:
            smoke.adb("shell", "content", "delete", "--uri", uri)
        raise


def tap_picker_item(smoke, title):
    deadline = time.monotonic() + 20
    while time.monotonic() < deadline:
        tree = smoke.tree()
        nodes = [node for node in tree.iter("node")
                 if title in (node.get("text", "") + " " + node.get("content-desc", ""))]
        if nodes:
            parents = {child: parent for parent in tree.iter("node") for child in parent}
            node = nodes[0]
            while node.get("clickable") != "true" and node in parents:
                node = parents[node]
            bounds = re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.get("bounds", ""))
            if node.get("clickable") != "true" or not bounds:
                raise RuntimeError("Named picker item is not tappable")
            left, top, right, bottom = map(int, bounds.groups())
            smoke.adb("shell", "input", "tap", str((left + right) // 2), str((top + bottom) // 2))
            return
        time.sleep(0.5)
    raise RuntimeError("Named image is not visible in the picker")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--image", type=Path, help="A local JPG or PNG; default is a generated 64x64 PNG")
    args = parser.parse_args()
    suffix = args.image.suffix.lower() if args.image else ".png"
    if suffix not in (".jpg", ".jpeg", ".png"):
        parser.error("--image must be JPG or PNG")
    mime = "image/png" if suffix == ".png" else "image/jpeg"
    data = args.image.read_bytes() if args.image else test_png()
    smoke = apk_smoke.Smoke()
    title = "BatohStatic_" + uuid.uuid4().hex[:12] + suffix
    uri = None
    result = {"format": mime, "status": "failed"}
    stage = "insert fixture"
    try:
        uri = insert_image(smoke, title, mime, data)
        print("Test image inserted on phone", flush=True)
        stage = "open Panel screen"
        smoke.adb("shell", "am", "start", "-n", apk_smoke.PACKAGE + "/" + apk_smoke.ACTIVITY)
        smoke.tab("navigation_panel")
        stage = "open phone picker"
        smoke.click_key("send_action")
        smoke.click("Z telefonu" if "Z telefonu" in smoke.labels(smoke.tree()) else "From my phone")
        stage = "select named image"
        tap_picker_item(smoke, title)
        print("Named image selected; waiting for target chooser", flush=True)
        stage = "choose all panels"
        smoke.see_any(("Kam poslat?", "Where to send?"), timeout=30)
        labels = smoke.labels(smoke.tree())
        another = next((label for label in ("Jiný panel…", "Another panel…") if label in labels), None)
        if another:
            smoke.click(another)
            labels = smoke.labels(smoke.tree())
        before_logs = smoke.adb("logcat", "-d", "-s", "BackpackBLE:I")
        before_ipixel = before_logs.count("iPixel upload:")
        before_backpack = before_logs.count("Chunks:")
        smoke.click("Všechny moje panely" if "Všechny moje panely" in labels else "All my panels")
        print("Sending to all picked panels in range", flush=True)
        stage = "verify transfer result"
        smoke.wait(lambda t: any(label.startswith(ACTIVE_SEND) for label in smoke.labels(t)),
                   "new upload is active", timeout=30)
        smoke.wait(lambda t: title in smoke.labels(t), "new image appears in upload card", timeout=30)
        tree = smoke.wait(lambda t: title in smoke.labels(t) and
                          any(SUMMARY.search(label) for label in smoke.labels(t)),
                          "multi-panel result", timeout=120)
        after_logs = smoke.adb("logcat", "-d", "-s", "BackpackBLE:I")
        verified = verify_all(smoke.labels(tree), chosen=True, seen_active=True,
                              ipixel_logs=after_logs.count("iPixel upload:") - before_ipixel,
                              backpack_logs=after_logs.count("Chunks:") - before_backpack)
        if verified is None:
            raise RuntimeError("Fresh panel result is unavailable")
        outcome, detail, rows = verified
        result.update({"status": "passed" if outcome == "ok" else outcome,
                       "detail": detail, "panel_outcomes": rows})
        print(f"Converted image: {detail}; {outcome}", flush=True)
    except (RuntimeError, subprocess.SubprocessError, OSError) as error:
        result["error_type"] = type(error).__name__
        result["last_stage"] = stage
        print(f"Static image test failed at {stage} ({type(error).__name__})", flush=True)
    finally:
        # A failed picker lookup otherwise leaves DocumentsUI above the app and can spoil the next smoke run.
        for _ in range(3):
            try:
                packages = {node.get("package", "") for node in smoke.tree().iter("node")}
                if "com.google.android.documentsui" not in packages:
                    break
                smoke.adb("shell", "input", "keyevent", "KEYCODE_BACK")
            except (RuntimeError, subprocess.SubprocessError, OSError):
                break
        if uri:
            try:
                smoke.adb("shell", "content", "delete", "--uri", uri)
                print("Only this run's test image removed from phone", flush=True)
            except (RuntimeError, subprocess.SubprocessError, OSError):
                result["status"] = "failed"
                result["cleanup_error"] = "Could not remove this run's exact MediaStore URI"
                print("Test image cleanup failed; see the report", flush=True)
        output = HERE.parent / "artifacts" / "panel-send"
        output.mkdir(parents=True, exist_ok=True)
        destination = output / (datetime.datetime.now().strftime("%Y%m%d-%H%M%S") + "-static.json")
        destination.write_text(json.dumps(result, indent=2) + "\n")
        print("Report:", destination, flush=True)
    return 0 if result["status"] == "passed" else 1


if __name__ == "__main__":
    raise SystemExit(main())
