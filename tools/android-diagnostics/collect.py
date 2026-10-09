"""Pull debug JSONL directly over authorized ADB; no UI export, network server or secrets in Git."""
import argparse
import json
import os
from pathlib import Path
import subprocess
import tempfile


def validate(path):
    count = 0
    last = 0
    complete = False
    with path.open(encoding="utf-8") as source:
        for line in source:
            row = json.loads(line)
            if complete:
                raise ValueError("Data after completion record")
            if row.get("type") == "export_complete":
                if row.get("schema") != 2 or row.get("until") != last:
                    raise ValueError("Invalid export watermark")
                complete = True
            else:
                if not isinstance(row.get("id"), int) or row["id"] <= last or not isinstance(row.get("fields"), dict):
                    raise ValueError("Invalid event or ordering")
                last = row["id"]
                count += 1
    if not complete:
        raise ValueError("Incomplete export; previous file preserved")
    return count, last


def collect(adb, package, output, serial=None):
    if not package.endswith(".debug"):
        raise ValueError("Only the development application is supported")
    output.parent.mkdir(parents=True, exist_ok=True)
    descriptor, temporary = tempfile.mkstemp(prefix="diagnostics-", suffix=".part", dir=output.parent)
    temporary = Path(temporary)
    try:
        command = [str(adb)] + (["-s", serial] if serial else []) + ["exec-out", "content", "read", "--uri",
            f"content://{package}.diagnostic-export/events"]
        with os.fdopen(descriptor, "wb") as stream:
            result = subprocess.run(command, stdout=stream, stderr=subprocess.PIPE, timeout=120)
        if result.returncode:
            raise RuntimeError("ADB diagnostic read failed; check connection and installed debug version")
        count, last = validate(temporary)
        os.replace(temporary, output)
        return {"events": count, "lastId": last, "output": str(output)}
    finally:
        temporary.unlink(missing_ok=True)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--adb", type=Path, default=Path("C:/Users/LENOVO/AppData/Local/Android/Sdk/platform-tools/adb.exe"))
    parser.add_argument("--package", default="pe.gob.reniec.dnie.evolution.debug")
    parser.add_argument("--serial")
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    print(json.dumps(collect(args.adb, args.package, args.output, args.serial), ensure_ascii=False))
