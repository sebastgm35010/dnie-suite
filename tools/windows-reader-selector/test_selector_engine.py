"""Scope and ownership tests without Windows, Frida, or a real DNI."""
import types
import unittest
from selector_engine import ReniecSelector, process_role

RUNTIME = r'C:\Users\fixture\.dcdelivery-2.0\files\customjre\bin\java.exe'
CACHE = r'C:\Users\fixture\AppData\Local\Apps\2.0'


class Session:
    def __init__(self):
        self.gated = False
        self.detached = False
        self.loaded = False
    def on(self, *args): pass
    def create_script(self, source): return self
    def load(self): self.loaded = True
    def enable_child_gating(self): self.gated = True
    def disable_child_gating(self): self.gated = False
    def detach(self): self.detached = True


class Device:
    def __init__(self):
        self.attached = {}
        self.resumed = []
        self.pending = []
    def on(self, *args): pass
    def off(self, *args): pass
    def attach(self, pid):
        session = Session()
        self.attached[pid] = session
        return session
    def resume(self, pid): self.resumed.append(pid)
    def enumerate_pending_children(self): return self.pending


class SelectorTests(unittest.TestCase):
    def role(self, name, path):
        return process_role(name,path,RUNTIME,CACHE,r'C:\Windows')

    def test_scope_requires_exact_runtime_and_trusted_roots(self):
        self.assertEqual('java',self.role('JAVA.EXE',RUNTIME.upper()))
        self.assertIsNone(self.role('java.exe',r'C:\Other\java.exe'))
        self.assertIsNone(self.role('java.exe',RUNTIME+'fake'))
        self.assertEqual('reniec',self.role('cddnie-app.exe',CACHE+r'\abc\cddnie-app.exe'))
        self.assertIsNone(self.role('cddnie-app.exe',CACHE+'-other'+r'\cddnie-app.exe'))
        self.assertEqual('launcher',self.role('dfsvc.exe',r'C:\Windows\Microsoft.NET\Framework64\v4.0.30319\dfsvc.exe'))
        self.assertIsNone(self.role('dfsvc.exe',r'C:\Other\Microsoft.NET\Framework\dfsvc.exe'))

    def test_shutdown_releases_only_own_suspended_children_and_detaches(self):
        device = Device()
        engine = ReniecSelector(device,'fixture',self.role,lambda *a,**k:None)
        engine.instrument(10,'launcher')
        engine.instrument(11,'java')
        self.assertTrue(device.attached[10].gated)
        self.assertFalse(device.attached[11].gated)
        self.assertTrue(device.attached[11].loaded)
        device.pending = [types.SimpleNamespace(pid=20,parent_pid=10),types.SimpleNamespace(pid=21,parent_pid=99)]
        engine.close()
        engine.close()
        self.assertEqual([20],device.resumed)
        self.assertTrue(all(s.detached and not s.gated for s in device.attached.values()))
        self.assertFalse(engine.instrument(30,'java'))

    def test_unrelated_child_is_resumed_without_attachment(self):
        device = Device()
        engine = ReniecSelector(device,'fixture',self.role,lambda *a,**k:None)
        engine.instrument(10,'launcher')
        engine._child(types.SimpleNamespace(pid=20,parent_pid=10,path=r'C:\Other\java.exe'))
        self.assertNotIn(20,device.attached)
        self.assertEqual([20],device.resumed)
        engine.close()

    def test_logging_never_forwards_unknown_payload_or_failure_text(self):
        rows=[]
        engine = ReniecSelector(Device(),'fixture',self.role,lambda event,**fields:rows.append((event,fields)))
        engine._message({'type':'send','payload':{'event':'unexpected','secret':'fixture'}},None)
        self.assertEqual([],rows)
        engine._message({'type':'error','description':'fixture-secret'},None)
        self.assertEqual([('selector_error',{})],rows)
        engine.close()


if __name__ == '__main__': unittest.main()
