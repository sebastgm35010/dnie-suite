"""Isolated PKCS#11 calls to the explicit synthetic reader; never selects Windows Hello."""
import ctypes as C
import json
import sys
import time
from pathlib import Path

root=Path(sys.argv[1])
def event(name, **fields):
    line=json.dumps(dict(time=int(time.time()*1000),syntheticOnly=True,event=name,fields=fields))
    with (root/'pkcs11-calls.jsonl').open('a',encoding='utf-8') as f:f.write(line+'\n')
    print(line,flush=True)

class Version(C.Structure):_fields_=[('major',C.c_ubyte),('minor',C.c_ubyte)]
class SlotInfo(C.Structure):
    _fields_=[('description',C.c_char*64),('manufacturer',C.c_char*32),('flags',C.c_ulong),('hardware',Version),('software',Version)]

if not (root/'ready.json').exists() or (root/'result.json').exists():raise RuntimeError('No live synthetic fixture')
lib=C.WinDLL(r'D:/DNIeToolchain/OfficialMiddleware/IDPlugClassic-x64/DLLs/idplug-pkcs11.dll')
session=C.c_ulong()
opened=False
try:
    r=lib.C_Initialize(None);event('C_Initialize',status=hex(r))
    if r:raise RuntimeError('Initialization failed')
    count=C.c_ulong();r=lib.C_GetSlotList(0,None,C.byref(count))
    if r or not 0<count.value<32:raise RuntimeError('Slot count unavailable')
    slots=(C.c_ulong*count.value)();r=lib.C_GetSlotList(0,slots,C.byref(count))
    if r:raise RuntimeError('Slot enumeration failed')
    own=None
    for slot in slots:
        info=SlotInfo();r=lib.C_GetSlotInfo(slot,C.byref(info))
        if r==0 and bytes(info.description).decode('ascii','replace').strip().startswith('DNIe Bridge Android NFC'):
            own=slot
    if own is None:raise RuntimeError('Own synthetic reader not found')
    event('selected_explicit_synthetic_slot',slot=own)
    token=C.create_string_buffer(1024)
    event('C_GetTokenInfo_started');r=lib.C_GetTokenInfo(own,token);event('C_GetTokenInfo',status=hex(r))
    event('C_OpenSession_started');r=lib.C_OpenSession(own,4,None,None,C.byref(session));event('C_OpenSession',status=hex(r))
    if r==0:
        opened=True
        event('C_Login_started',input='synthetic_test_value')
        # Deliberate demo value, never the real DNI's PIN or CAN.
        r=lib.C_Login(session,1,C.c_char_p(b'000000'),6);event('C_Login',status=hex(r))
finally:
    if opened:event('C_CloseSession',status=hex(lib.C_CloseSession(session)))
    event('C_Finalize',status=hex(lib.C_Finalize(None)))
