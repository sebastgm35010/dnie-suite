"""Bounded selector operation and status IPC. UI reads status, requests stop.

No remote control socket and no browser or card interaction. Runtime/dependency
paths are caller settings. Frida is not bundled or installed globally here.
"""
import argparse
import json
import os
import pathlib
import sys
import threading
import time

from selector_engine import ReniecSelector, process_role


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--directory', required=True)
    parser.add_argument('--dependency-dir', required=True)
    parser.add_argument('--runtime', default=str(pathlib.Path.home()/'.dcdelivery-2.0/files/customjre/bin/java.exe'))
    parser.add_argument('--seconds', type=int, default=28800)
    parser.add_argument('--self-test', action='store_true')
    parser.add_argument('--probe-classes', default=r'D:\DNIeToolchain\ReaderSelector')
    args = parser.parse_args()
    if not 1 <= args.seconds <= 28800:
        parser.error('Duration outside bounds')
    directory = pathlib.Path(args.directory)
    directory.mkdir(parents=True, exist_ok=True)
    stop = directory/'stop.txt'
    state = {'pid': os.getpid(), 'phase': 'starting', 'filteredLists': 0, 'failureCount': 0}
    lock = threading.RLock()

    def record(event, **fields):
        with lock:
            if event == 'reader_filter_applied':
                state['filteredLists'] += 1
            if event in ('attach_failed', 'selector_error'):
                state['failureCount'] += 1
                state['phase'] = 'degraded'
            state['event'], state['time'] = event, int(time.time()*1000)
            temp = directory/'status.tmp'
            temp.write_text(json.dumps(state), encoding='utf-8')
            temp.replace(directory/'status.json')
            log = directory/'events.jsonl'
            if log.exists() and log.stat().st_size > 1024*1024:
                log.replace(directory/'events.previous.jsonl')
            with log.open('a', encoding='utf-8') as target:
                target.write(json.dumps({'time':state['time'],'event':event,**fields})+'\n')

    record('starting')
    engine = None
    lease = None
    try:
        if not args.self_test:
            import msvcrt
            lease_root = pathlib.Path(os.environ['LOCALAPPDATA'])/'DNIeBridge'
            lease_root.mkdir(parents=True, exist_ok=True)
            lease = (lease_root/'reader-selector.lock').open('a+b')
            if lease.seek(0, 2) == 0:
                lease.write(b'0'); lease.flush()
            lease.seek(0)
            msvcrt.locking(lease.fileno(), msvcrt.LK_NBLCK, 1)
        sys.path.insert(0, args.dependency_dir)
        import frida
        import psutil
        local = pathlib.Path(os.environ['LOCALAPPDATA'])/'Apps/2.0'
        windows = os.environ.get('WINDIR', r'C:\Windows')
        role_for = lambda name, path: process_role(name, path, args.runtime, str(local), windows)
        device = frida.get_local_device()
        source = pathlib.Path(__file__).with_name('pcsc-bridge-selector.js').read_text(encoding='utf-8')
        engine = ReniecSelector(device, source, role_for, record)
        state['phase'] = 'running'
        record('ready')
        if args.self_test:
            passed = threading.Event()
            def output(pid, fd, data):
                if b'bridge_only_metadata_passed' in data:
                    passed.set()
            device.on('output', output)
            pid = device.spawn([args.runtime, '-client', '-cp', args.probe_classes,
                                'ReaderProbeParent', '--assert-bridge-only'], stdio='pipe')
            engine.instrument(pid, 'launcher')
            device.resume(pid)
            success = passed.wait(15) and engine.filtered_lists > 0
            record('self_test', passed=success)
            if not success:
                raise RuntimeError('Scoped selector test failed')
            return
        retries = {}
        until = time.monotonic()+args.seconds
        while time.monotonic() < until and not stop.exists():
            now = time.monotonic()
            current = set()
            # Query executable paths only for candidates, not every Windows process.
            for process in psutil.process_iter(['pid','name']):
                info = process.info
                if (info['name'] or '').lower() not in ('java.exe','javaw.exe','cddnie-app.exe','dfsvc.exe'):
                    continue
                pid = info['pid']
                if pid in engine.sessions:
                    current.add(pid)
                    continue
                try:
                    role = role_for(info['name'], process.exe())
                except (psutil.NoSuchProcess, psutil.AccessDenied):
                    continue
                if not role:
                    continue
                current.add(pid)
                if pid not in engine.sessions and now >= retries.get(pid, 0):
                    if not engine.instrument(pid, role):
                        retries[pid] = now+30
            retries = {pid:at for pid,at in retries.items() if pid in current}
            time.sleep(.2)
    except Exception as error:
        state['phase'] = 'failed'
        record('failed', failureType=type(error).__name__)
        raise
    finally:
        if engine:
            engine.close()
        if lease:
            lease.close()
        if state['phase'] != 'failed':
            state['phase'] = 'stopped'
        record('stopped')


if __name__ == '__main__':
    main()
