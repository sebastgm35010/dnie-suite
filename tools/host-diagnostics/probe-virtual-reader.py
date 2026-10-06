"""WinSCard smoke test: only the existing Virtual Smart Card Architecture reader."""
import ctypes as c
from ctypes import wintypes as w
import json


def check(rc, stage):
    if rc:
        raise RuntimeError(f'{stage}: 0x{rc & 0xffffffff:08X}')


def main():
    dll = c.WinDLL('winscard.dll')
    handle = c.c_size_t
    dll.SCardEstablishContext.argtypes = [w.DWORD, c.c_void_p, c.c_void_p, c.POINTER(handle)]
    dll.SCardListReadersW.argtypes = [handle, w.LPCWSTR, w.LPWSTR, c.POINTER(w.DWORD)]
    dll.SCardConnectW.argtypes = [handle, w.LPCWSTR, w.DWORD, w.DWORD, c.POINTER(handle), c.POINTER(w.DWORD)]
    dll.SCardDisconnect.argtypes = [handle, w.DWORD]
    dll.SCardReleaseContext.argtypes = [handle]
    dll.SCardTransmit.argtypes = [handle, c.c_void_p, c.c_void_p, w.DWORD, c.c_void_p, c.c_void_p, c.POINTER(w.DWORD)]
    for name in ('SCardEstablishContext', 'SCardListReadersW', 'SCardConnectW', 'SCardDisconnect', 'SCardReleaseContext', 'SCardTransmit'):
        getattr(dll, name).restype = c.c_long
    context = handle()
    check(dll.SCardEstablishContext(0, None, None, c.byref(context)), 'context')
    card = handle()
    try:
        size = w.DWORD()
        check(dll.SCardListReadersW(context, None, None, c.byref(size)), 'reader list')
        buf = c.create_unicode_buffer(size.value)
        check(dll.SCardListReadersW(context, None, buf, c.byref(size)), 'reader list')
        readers = buf[:size.value].rstrip('\0').split('\0')
        virtual = next((r for r in readers if r.startswith('Virtual Smart Card Architecture ')), None)
        if not virtual:
            raise RuntimeError('Expected virtual reader absent; no physical reader touched')
        protocol = w.DWORD()
        check(dll.SCardConnectW(context, virtual, 2, 3, c.byref(card), c.byref(protocol)), 'dummy connect')
        class IO(c.Structure):
            _fields_ = [('protocol', w.DWORD), ('length', w.DWORD)]
        pci = IO(protocol.value, c.sizeof(IO))
        command = (c.c_ubyte * 4)(0, 0xA4, 0, 0)
        answer = (c.c_ubyte * 258)()
        length = w.DWORD(len(answer))
        check(dll.SCardTransmit(card, c.byref(pci), command, len(command), None, answer, c.byref(length)), 'dummy transmit')
        result = bytes(answer[:length.value]).hex()
        if result != '6a82':
            raise RuntimeError('Unexpected response: not the expected dummy; no further commands sent')
        print(json.dumps(dict(reader=virtual, connected=True, protocol=protocol.value, selectResponse=result)))
    finally:
        if card.value:
            dll.SCardDisconnect(card, 0)
        dll.SCardReleaseContext(context)


if __name__ == '__main__':
    main()
