"""Observe reader enumeration/selection in RENIEC processes without filtering.

Dependencies installed privately in D:; no global Java configuration changes.
Only named RENIEC processes and their own children are eligible for attachment.
"""
import argparse
import json
import pathlib
import sys
import threading
import time

sys.path.insert(0, r'D:\DNIeToolchain\Instrumentation\python')
import frida
import psutil


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--output', required=True)
    parser.add_argument('--seconds', type=int, default=900)
    parser.add_argument('--self-test', action='store_true')
    parser.add_argument('--self-test-child', action='store_true')
    parser.add_argument('--select-bridge', action='store_true',
                        help='Temporary reader-list filter in RENIEC Java only')
    args = parser.parse_args()
    if not 1 <= args.seconds <= 1800:
        parser.error('Invalid duration')
    output = pathlib.Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    lock = threading.RLock()
    sessions = {}
    attempted = set()
    device = frida.get_local_device()
    source = pathlib.Path(__file__).with_name('pcsc-observer.js').read_text(encoding='utf-8')
    captured = []

    def record(event, **fields):
        row = {'time': int(time.time() * 1000), 'event': event, **fields}
        with lock:
            with output.open('a', encoding='utf-8') as target:
                target.write(json.dumps(row) + '\n')
            captured.append(row)

    def instrument(pid, role, gate=False):
        session = None
        try:
            with lock:
                if pid in sessions:
                    return
                attempted.add(pid)
            session = device.attach(pid)
            with lock:
                sessions[pid] = session
            script = session.create_script(source)

            def message(value, data):
                if value.get('type') == 'send':
                    record('pcsc_observation', pid=pid, role=role, observation=value['payload'])
                elif value.get('type') == 'error':
                    record('hook_error', pid=pid, role=role)

            script.on('message', message)
            script.load()
            if args.select_bridge and role in ('reniec_java', 'reniec_java_child', 'synthetic_metadata'):
                selector_source = (pathlib.Path(__file__).parent.parent / 'windows-reader-selector/pcsc-bridge-selector.js').read_text(encoding='utf-8')
                selector = session.create_script(selector_source)
                selector.on('message', message)
                selector.load()
            if gate:
                try:
                    session.enable_child_gating()
                    record('child_observation_ready', pid=pid)
                except Exception as error:
                    record('child_observation_unavailable', pid=pid, failureType=type(error).__name__)
            record('attached', pid=pid, role=role)
        except Exception as error:
            record('attach_failed', pid=pid, role=role, failureType=type(error).__name__)
            if session:
                try:
                    session.detach()
                except Exception:
                    pass

    def observe_child(child):
        # Parent-specific gating, never system-wide spawn gating.
        try:
            with lock:
                parent = child.parent_pid in sessions
            if parent:
                name = pathlib.PureWindowsPath(child.path or '').name.lower()
                if name in ('java.exe', 'javaw.exe'):
                    record('java_child_seen', pid=child.pid, parentPid=child.parent_pid)
                    instrument(child.pid, 'reniec_java_child')
                elif name == 'cddnie-app.exe':
                    record('reniec_child_seen', pid=child.pid, parentPid=child.parent_pid)
                    instrument(child.pid, 'reniec_dotnet_child', gate=True)
        finally:
            try:
                device.resume(child.pid)
            except Exception:
                pass

    def child_added(child):
        # Frida callbacks run on its event loop; synchronous attach here deadlocks.
        threading.Thread(target=observe_child, args=(child,), daemon=True).start()

    device.on('child-added', child_added)
    record('armed', limitSeconds=args.seconds, changesSelection=args.select_bridge)
    probe = None
    try:
        if args.self_test or args.self_test_child:
            runtime = r'C:\Users\LENOVO\.dcdelivery-2.0\files\customjre\bin\java.exe'
            entry = 'ReaderProbeParent' if args.self_test_child else 'ReaderMetadata'
            probe = device.spawn([runtime, '-client', '-cp', r'D:\DNIeToolchain\ReaderSelector', entry])
            instrument(probe, 'synthetic_metadata', gate=args.self_test_child)
            device.resume(probe)
            deadline = time.monotonic() + 10
            while time.monotonic() < deadline:
                if any(row['event'] == 'pcsc_observation' and
                       row['observation'].get('event') == 'reader_list' and
                       row['observation'].get('fields', {}).get('readers') and
                       (not args.self_test_child or row['role'] == 'reniec_java_child') for row in captured):
                    break
                time.sleep(.1)
            observed = [row for row in captured if row['event'] == 'pcsc_observation']
            valid = any(row['observation'].get('event') == 'reader_list' and
                        row['observation'].get('fields', {}).get('readers') and
                        (not args.self_test_child or row['role'] == 'reniec_java_child') for row in observed)
            if args.select_bridge:
                valid = valid and any(row['observation'].get('event') == 'bridge_reader_list_filtered'
                                      and row['observation'].get('fields', {}).get('bridgeVisible')
                                      and (not args.self_test_child or row['role'] == 'reniec_java_child')
                                      for row in observed)
                valid = valid and any(row['observation'].get('event') == 'reader_presence'
                                      and len(row['observation'].get('fields', {}).get('states', [])) == 1
                                      and row['observation']['fields']['states'][0]['reader'] == 'DNIe Bridge Android NFC Private Reader 0'
                                      and (not args.self_test_child or row['role'] == 'reniec_java_child')
                                      for row in observed)
            record('self_test', passed=valid)
            if not valid:
                raise RuntimeError('Reader observation self-test failed')
            return
        until = time.monotonic() + args.seconds
        while (time.monotonic() < until and not output.with_name('stop.txt').exists()
               and not output.with_name('result.json').exists()):
            for process in psutil.process_iter(['pid', 'name', 'exe', 'ppid']):
                info = process.info
                name = (info['name'] or '').lower()
                path = (info['exe'] or '').lower()
                role = None
                if name == 'cddnie-app.exe':
                    role = 'reniec_dotnet'
                elif name == 'dfsvc.exe' and path.startswith('c:\\windows\\microsoft.net\\framework'):
                    role = 'clickonce_launcher'
                elif name in ('java.exe', 'javaw.exe') and '.dcdelivery-2.0\\files\\customjre\\bin\\' in path:
                    role = 'reniec_java'
                if role and info['pid'] not in attempted:
                    attempted.add(info['pid'])
                    instrument(info['pid'], role, gate=role in ('reniec_dotnet', 'clickonce_launcher'))
            time.sleep(.05)
    finally:
        # Explicitly release only children suspended by our own parent sessions.
        try:
            for child in device.enumerate_pending_children():
                if child.parent_pid in sessions:
                    device.resume(child.pid)
        except Exception:
            pass
        for session in list(sessions.values()):
            try:
                session.disable_child_gating()
                session.detach()
            except Exception:
                pass
        record('stopped')


if __name__ == '__main__':
    main()
