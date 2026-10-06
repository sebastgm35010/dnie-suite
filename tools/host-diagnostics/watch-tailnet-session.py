"""Capture the next accepted tailnet session; passive, bounded, no APDU injection."""
import argparse
import json
import pathlib
import re
import subprocess
import time
import uuid


def phase(line):
    match = re.match(r'(\S+) bridge_phase=(\w+)(.*)', line)
    if not match:
        return None
    return {'serverTime': match[1], 'event': match[2],
            'fields': dict(re.findall(r'(header|status|type|code|requestId)=([A-Za-z0-9_]+)', match[3]))}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--host', required=True)
    parser.add_argument('--output-root', required=True)
    parser.add_argument('--arm-seconds', type=int, default=7200)
    parser.add_argument('--session-seconds', type=int, default=1800)
    parser.add_argument('--dry-run', action='store_true')
    parser.add_argument('--pcsc-attribution', action='store_true',
                        help='Administrative bounded ETW capture of PC/SC lifecycle')
    args = parser.parse_args()
    if not 1 <= args.arm_seconds <= 7200 or not 1 <= args.session_seconds <= 1800:
        parser.error('duration outside bounds')
    root = pathlib.Path(args.output_root)
    root.mkdir(parents=True, exist_ok=True)
    host = pathlib.Path(args.host)
    offset = host.stat().st_size if host.exists() else 0
    buffer = b''
    until = time.monotonic() + args.arm_seconds
    active = None
    target = None
    children = []
    attribution = None
    reason = 'arm_timeout'
    script_dir = pathlib.Path(__file__).resolve().parent
    (root / 'armed.json').write_text(json.dumps({'time': int(time.time()*1000), 'host': str(host),
        'mode': 'next_tailnet_session', 'armSeconds': args.arm_seconds}), encoding='utf-8')
    print('ARMED: next tailnet request; capture closes on session completion.', flush=True)
    try:
        while time.monotonic() < until:
            if (root / 'cancel.txt').exists():
                reason = 'monitor_cancelled'
                break
            if host.exists():
                if host.stat().st_size < offset:
                    offset, buffer = 0, b''
                with host.open('rb') as source:
                    source.seek(offset)
                    data = source.read(1024*1024)
                    offset = source.tell()
                lines = (buffer + data).split(b'\n')
                buffer = lines.pop()
                if len(buffer) > 1024*1024:
                    buffer = b''
                for raw in lines:
                    event = phase(raw.decode('utf-8', errors='replace').strip())
                    if not event:
                        continue
                    if active is None and event['event'] == 'tailnet_peer_verified':
                        active = root / uuid.uuid4().hex
                        active.mkdir()
                        target = (active / 'bridge.jsonl').open('w', encoding='utf-8')
                        (root / 'current-capture.txt').write_text(str(active), encoding='utf-8')
                        until = time.monotonic() + args.session_seconds
                        if not args.dry_run:
                            def launch(command, label):
                                with (active / (label+'-out.txt')).open('wb') as out, (active / (label+'-error.txt')).open('wb') as err:
                                    children.append(subprocess.Popen(command, stdout=out, stderr=err,
                                        creationflags=subprocess.CREATE_NO_WINDOW))
                            launch(['powershell.exe','-NoProfile','-File',str(script_dir/'watch-host.ps1'),
                                '-OutputDirectory',str(active),'-Seconds',str(args.session_seconds)],'host')
                            if args.pcsc_attribution:
                                attribution = root.parent / uuid.uuid4().hex
                                attribution.mkdir()
                                (active/'attribution-directory.txt').write_text(str(attribution), encoding='utf-8')
                                launch(['powershell.exe','-NoProfile','-File',str(script_dir/'Capture-PcscAttribution.ps1'),
                                    '-OutputDirectory',str(attribution),'-Seconds',str(min(300,max(15,args.session_seconds)))],
                                    'attribution')
                            launch(['node.exe',str(script_dir/'edge-capture.mjs'),'--port','19371','--seconds',str(args.session_seconds),
                                '--output',str(active/'edge.jsonl'),'--stop',str(active/'stop.txt')],'edge')
                        print('CAPTURE_STARTED: '+str(active), flush=True)
                    if target is not None:
                        target.write(json.dumps({'observedTime':int(time.time()*1000), **event})+'\n')
                        target.flush()
                        if event['event'] == 'tailnet_ready_for_next_session':
                            reason = 'session_completed'
                            return
                        if target.tell() >= 20*1024*1024:
                            reason = 'size_limit'
                            return
            time.sleep(.1)
        if active is not None and reason == 'arm_timeout':
            reason = 'session_timeout'
    finally:
        if active is not None:
            (active/'stop.txt').write_text(reason, encoding='utf-8')
        if attribution is not None:
            (attribution/'stop.txt').write_text(reason, encoding='utf-8')
        if target is not None:
            target.close()
        shutdown = time.monotonic()+15
        for child in children:
            try:
                child.wait(timeout=max(.1,shutdown-time.monotonic()))
            except subprocess.TimeoutExpired:
                child.terminate()
                child.wait(timeout=5)
        summary = {'time':int(time.time()*1000),'reason':reason,'capture':str(active) if active else None,
                   'attribution':str(attribution) if attribution else None,
                   'attributionReady': bool(attribution and (attribution/'attribution-ready.txt').exists()),
                   'collectorsStopped':all(child.poll() is not None for child in children)}
        (root/'result.json').write_text(json.dumps(summary),encoding='utf-8')
        print('MONITOR_STOPPED: '+reason,flush=True)


if __name__ == '__main__':
    main()
