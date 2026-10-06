"""Local-only dummy for an already installed Bix/vpcd reader, not a DNI emulator.

Framing follows the public vsmartcard wire protocol; no third-party code imported.
https://frankmorgner.github.io/vsmartcard/virtualsmartcard/api.html
No keys, credentials, PACE, certificates or successful authentication are provided.
"""
import argparse
import json
from pathlib import Path
import socket
import struct
import time

ATR = bytes.fromhex('3B80800101')  # Generic T=1, no RENIEC identity/history.


def response(command):
    if len(command) < 4:
        return bytes.fromhex('6700')
    if command[1] in (0x20, 0x22, 0x24, 0x2A, 0x82, 0x86, 0x88):
        return bytes.fromhex('6985')
    if command[1] in (0xA4, 0xB0, 0xB2, 0xCA):
        return bytes.fromhex('6A82')
    return bytes.fromhex('6D00')


def receive_exact(sock, length):
    data = bytearray()
    while len(data) < length:
        chunk = sock.recv(length - len(data))
        if not chunk:
            raise EOFError()
        data.extend(chunk)
    return bytes(data)


def run(port, directory, seconds):
    directory.mkdir(parents=True, exist_ok=True)
    log = directory / 'dummy-card.jsonl'
    stop = directory / 'stop.txt'

    def event(name, **fields):
        with log.open('a', encoding='utf-8') as output:
            output.write(json.dumps(dict(time=int(time.time() * 1000), event=name, fields=fields)) + '\n')

    until = time.monotonic() + seconds
    event('starting', mode='dummy_generic_T1', port=port, limitSeconds=seconds)
    try:
        # Never listen or connect to a remote host; the existing driver is reused as-is.
        with socket.create_connection(('127.0.0.1', port), timeout=3) as sock:
            sock.settimeout(0.5)
            event('connected')
            buffer = bytearray()
            while time.monotonic() < until and not stop.exists():
                try:
                    chunk = sock.recv(65536)
                except socket.timeout:
                    continue
                if not chunk:
                    break
                buffer.extend(chunk)
                while len(buffer) >= 2:
                    length = struct.unpack('!H', buffer[:2])[0]
                    if length == 0:
                        raise ValueError('Empty frame')
                    if len(buffer) < 2 + length:
                        break
                    command = bytes(buffer[2:2 + length])
                    del buffer[:2 + length]
                    if length == 1:
                        code = command[0]
                        if code == 4:
                            sock.sendall(struct.pack('!H', len(ATR)) + ATR)
                            event('atr_requested')
                        else:
                            event('control', code=code)
                    else:
                        answer = response(command)
                        sock.sendall(struct.pack('!H', len(answer)) + answer)
                        # Data/PIN/CAN and complete commands are intentionally absent.
                        event('apdu_rejected', header=command[:4].hex(), bytes=length, sw=answer.hex())
    except Exception as failure:
        event('connection_ended', kind=type(failure).__name__)
    finally:
        event('stopped')


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--port', type=int, default=35963)
    parser.add_argument('--directory', type=Path, required=True)
    parser.add_argument('--seconds', type=int, default=1800)
    args = parser.parse_args()
    if not (1024 <= args.port <= 65535 and 1 <= args.seconds <= 3600):
        parser.error('Invalid port or lifetime')
    run(args.port, args.directory, args.seconds)
