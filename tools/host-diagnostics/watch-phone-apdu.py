"""Private opt-in bridge transcript capture over an already authorized debug connection."""
import argparse
import json
import pathlib
import subprocess
import time


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--adb', required=True)
    parser.add_argument('--device', required=True)
    parser.add_argument('--output', required=True)
    parser.add_argument('--seconds', type=int, default=1800)
    parser.add_argument('--metadata-only', action='store_true')
    args = parser.parse_args()
    if not 1 <= args.seconds <= 3600:
        parser.error('invalid capture duration')
    root = pathlib.Path(args.output)
    start = int(time.time()*1000)
    until = time.monotonic()+args.seconds
    seen = set()
    with (root/'phone-apdu.jsonl').open('a', encoding='utf-8') as target:
        while time.monotonic() < until and not (root/'stop.txt').exists():
            try:
                result = subprocess.run([args.adb, '-s', args.device, 'exec-out', 'run-as',
                    'pe.gob.reniec.dnie.evolution.debug', 'cat', 'files/bridge-apdu-trace.jsonl'],
                    capture_output=True, timeout=5, creationflags=subprocess.CREATE_NO_WINDOW)
                if result.returncode == 0:
                    for line in result.stdout.decode('utf-8', errors='replace').splitlines():
                        try:
                            row = json.loads(line)
                            if row['time'] < start-3000:
                                continue
                            key = (row['time'], row['session'], row['sequence'], row['event'])
                            if key in seen:
                                continue
                            seen.add(key)
                            if args.metadata_only:
                                row.pop('hex', None)
                            row['observedTime'] = int(time.time()*1000)
                            target.write(json.dumps(row)+'\n')
                        except (ValueError, KeyError, TypeError):
                            continue
                    target.flush()
            except (OSError, subprocess.TimeoutExpired):
                pass
            if target.tell() >= 50*1024*1024 or len(seen) >= 500_000:
                break
            time.sleep(2)


if __name__ == '__main__':
    main()
