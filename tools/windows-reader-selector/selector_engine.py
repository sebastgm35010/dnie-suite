"""Own process-scoped selection; no browser, ETW, APDU or command-line capture.

Native dependencies are supplied by the launcher. Importing this module does not
attach to a process or require Frida, allowing lifecycle tests without a card.
"""
import pathlib
import threading
import time


def process_role(name, executable, runtime, clickonce_root, windows_root):
    """Exact Java path and Windows/ClickOnce roots; never match arbitrary Java."""
    path = pathlib.PureWindowsPath(executable or '')
    label = (name or '').lower()
    if label in ('java.exe', 'javaw.exe') and path == pathlib.PureWindowsPath(runtime):
        return 'java'
    if label == 'cddnie-app.exe' and path.is_relative_to(pathlib.PureWindowsPath(clickonce_root)):
        return 'reniec'
    frameworks = pathlib.PureWindowsPath(windows_root) / 'Microsoft.NET'
    if label == 'dfsvc.exe' and path.is_relative_to(frameworks) and any(
            part.lower() in ('framework', 'framework64') for part in path.parts):
        return 'launcher'
    return None


class ReniecSelector:
    """Own attachments and children suspended only by these parent sessions."""
    def __init__(self, device, source, role_for, event):
        self.device, self.source = device, source
        self.role_for, self.event = role_for, event
        self.lock = threading.RLock()
        self.sessions = {}
        self.threads = set()
        self.closed = False
        self.filtered_lists = 0
        self.device.on('child-added', self._child_added)

    def instrument(self, pid, role):
        session = None
        try:
            # Serialize attachment and stop: no session can escape final cleanup.
            with self.lock:
                if self.closed or pid in self.sessions:
                    return False
                session = self.device.attach(pid)
                self.sessions[pid] = (session, None)
                session.on('detached', lambda *args: self._detached(pid))
                if role == 'java':
                    script = session.create_script(self.source)
                    script.on('message', self._message)
                    script.load()
                    self.sessions[pid] = (session, script)
                else:
                    session.enable_child_gating()
                self.event('attached', pid=pid, role=role)
                return True
        except Exception as error:
            self.event('attach_failed', pid=pid, role=role, failureType=type(error).__name__)
            if session:
                try:
                    session.disable_child_gating()
                    session.detach()
                except Exception:
                    pass
            with self.lock:
                self.sessions.pop(pid, None)
            return False

    def _detached(self, pid):
        with self.lock:
            self.sessions.pop(pid, None)

    def _message(self, message, data):
        # Emit only our fixed event vocabulary; no native exception text/payloads.
        if message.get('type') == 'error':
            self.event('selector_error')
        elif message.get('type') == 'send':
            payload = message.get('payload', {})
            if payload.get('event') == 'bridge_reader_list_filtered':
                with self.lock:
                    self.filtered_lists += 1
                self.event('reader_filter_applied', bridgeVisible=bool(payload.get('fields', {}).get('bridgeVisible')))

    def _child_added(self, child):
        with self.lock:
            if self.closed or child.parent_pid not in self.sessions:
                return
            # Callbacks must not synchronously attach on Frida's event thread.
            worker = threading.Thread(target=self._child, args=(child,), daemon=True)
            self.threads.add(worker)
            worker.start()

    def _child(self, child):
        try:
            path = child.path or ''
            role = self.role_for(pathlib.PureWindowsPath(path).name, path)
            if role:
                self.instrument(child.pid, role)
        finally:
            try:
                self.device.resume(child.pid)
            except Exception:
                pass
            with self.lock:
                self.threads.discard(threading.current_thread())

    def close(self):
        with self.lock:
            if self.closed:
                return
            self.closed = True
            owned = dict(self.sessions)
        self.device.off('child-added', self._child_added)
        try:
            for child in self.device.enumerate_pending_children():
                if child.parent_pid in owned:
                    try:
                        self.device.resume(child.pid)
                    except Exception:
                        pass
        except Exception:
            pass
        for session, script in owned.values():
            try:
                session.disable_child_gating()
            except Exception:
                pass
            try:
                session.detach()
            except Exception:
                pass
        until = time.monotonic() + 3
        for worker in list(self.threads):
            worker.join(max(0, until-time.monotonic()))
        with self.lock:
            self.sessions.clear()
        self.event('selector_detached')
