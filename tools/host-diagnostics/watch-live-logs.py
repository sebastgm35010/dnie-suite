"""Tail only new diagnostic phases; never persist official log payloads."""
import argparse
import datetime as dt
import json
import pathlib
import re
import time


def classify(line, category):
    if category == 'windows_bridge':
        match = re.match(r'(\S+) bridge_phase=(\w+)(.*)', line)
        if match:
            return match[2], dict(re.findall(r'(header|status|type)=([A-Za-z0-9_]+)', match[3]))
    elif category == 'official_dotnet':
        for phrase, event in [('La tarjeta es un DNIe v3', 'v3_recognized'),
                              ('java.exe', 'java_started'),
                              ('Al parecer', 'dni_connection_error')]:
            if phrase in line:
                return event, {}
    elif category == 'official_java':
        match = re.search(r'Resultado de verificaci.{0,25}v([23]) es ([0-9A-Fa-f]{4})', line)
        if match:
            return 'version_probe', {'version': int(match[1]), 'status': match[2]}
        if 'Exception' in line:
            return 'exception_observed', {}
    return None


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--output', required=True)
    parser.add_argument('--host', required=True)
    parser.add_argument('--seconds', type=int, default=1800)
    args = parser.parse_args()
    if not 1 <= args.seconds <= 3600:
        parser.error('invalid duration')
    output = pathlib.Path(args.output)
    official = pathlib.Path.home() / '.dcdelivery-2.0'
    sources = [(pathlib.Path(args.host), 'windows_bridge')]
    sources.extend((p, 'official_dotnet') for p in official.glob('dcdelivery-app-*.log'))
    sources.extend((p, 'official_java') for p in official.glob('dcdelivery-rdr*.log'))
    offsets = {p: p.stat().st_size if p.exists() else 0 for p, _ in sources}
    buffers = {p: b'' for p, _ in sources}
    # Retain the bridge startup from this fresh session, official historical logs stay excluded.
    offsets[sources[0][0]] = 0
    until = time.monotonic() + args.seconds
    with (output / 'live-layers.jsonl').open('a', encoding='utf-8') as target:
        def write(category, event, fields):
            target.write(json.dumps(dict(time=int(time.time()*1000), category=category,
                                         event=event, fields=fields))+'\n')
            target.flush()
        write('capture', 'live_tail_started', {'limitSeconds': args.seconds, 'officialHistoryExcluded': True})
        while time.monotonic() < until and not (output / 'stop.txt').exists():
            for path, category in sources:
                if not path.exists():
                    continue
                if path.stat().st_size < offsets[path]:
                    offsets[path], buffers[path] = 0, b''
                with path.open('rb') as source:
                    source.seek(offsets[path])
                    data = source.read(1024*1024)
                    offsets[path] = source.tell()
                lines = (buffers[path]+data).split(b'\n')
                buffers[path] = lines.pop()
                if len(buffers[path]) > 1024*1024:
                    buffers[path] = b''
                for raw in lines:
                    result = classify(raw.decode('utf-8', errors='replace'), category)
                    if result:
                        write(category, *result)
            if target.tell() > 20*1024*1024:
                break
            time.sleep(.25)
        write('capture', 'live_tail_stopped', {})


if __name__ == '__main__':
    main()
