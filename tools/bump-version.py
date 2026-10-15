"""Advance product patch + Android code once per delivery; builds themselves remain reproducible."""
from pathlib import Path
import argparse
import json
import re

ROOT = Path(__file__).resolve().parents[1]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--next-patch", action="store_true", help="Increment patch and Android versionCode")
    args = parser.parse_args()
    path = ROOT / "gradle.properties"
    text = path.read_text(encoding="utf-8")
    def value(key):
        matches = re.findall(r"^" + re.escape(key) + r"=(.+)$", text, re.M)
        if len(matches) != 1:
            raise ValueError("Expected one property: " + key)
        return matches[0].strip()
    version = value("dnie.versionName")
    if not re.fullmatch(r"(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)", version):
        raise ValueError("Version must be major.minor.patch")
    code = int(value("dnie.androidVersionCode"))
    if not 1 <= code < 2_100_000_000:
        raise ValueError("Android versionCode out of range")
    if args.next_patch:
        major, minor, patch = map(int, version.split("."))
        version = f"{major}.{minor}.{patch + 1}"
        code += 1
        text = re.sub(r"^dnie\.versionName=.+$", "dnie.versionName=" + version, text, flags=re.M)
        text = re.sub(r"^dnie\.androidVersionCode=.+$", "dnie.androidVersionCode=" + str(code), text, flags=re.M)
        path.write_text(text, encoding="utf-8")
    print(json.dumps({"versionName": version, "versionCode": code, "advanced": args.next_patch}))


if __name__ == "__main__":
    main()
