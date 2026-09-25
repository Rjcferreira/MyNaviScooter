"""Local-only evidence inspection. Never publishes phone logs or credentials."""
import pathlib
import re
import struct
import sys

root = pathlib.Path(sys.argv[1])
lib = root / 'shu/lib/arm64-v8a/libapp.so'
if lib.exists():
    strings = [s.decode('ascii') for s in re.findall(rb'[ -~]{12,}', lib.read_bytes())]
    matches = [s[:240] for s in strings if re.search(r'press.{0,35}(button|power)|PRE_COMM|pairing|6e40000|setNotifyValue|requestMtu', s, re.I)]
    print('SHU native strings (clues, not proof of execution):')
    for s in matches[:50]:
        print(s)

path = root / 'hci/FS/data/log/bt/btsnoop_hci.log'
data = path.read_bytes()
offset = 16
events = []
while offset + 24 <= len(data):
    original, included, flags, drops, timestamp = struct.unpack_from('>IIIIQ', data, offset)
    offset += 24
    packet = data[offset:offset + included]
    offset += included
    if len(packet) < 9 or packet[0] != 2:
        continue
    handle, size = struct.unpack_from('<HH', packet, 1)
    if handle & 0xfff != 0x41:
        continue
    payload = packet[5:5 + size]
    if len(payload) < 4:
        continue
    length, cid = struct.unpack_from('<HH', payload)
    if cid != 4:
        continue
    att = payload[4:4 + length]
    if not att:
        continue
    label = None
    if att[0] in (2, 3) and len(att) == 3:
        label = f'MTU opcode={att[0]} value={int.from_bytes(att[1:], "little")}'
    elif att[0] == 0x13:
        label = 'ATT write response'
    elif att[0] in (0x12, 0x52, 0x1b) and len(att) >= 3:
        attribute = int.from_bytes(att[1:3], 'little')
        if attribute in (0x2f, 0x31, 0x32):
            value = att[3:]
            label = f'opcode={att[0]:02x} attribute={attribute:04x} bytes={len(value)}'
            if value == bytes.fromhex('5aa500a7e25864000062ff0000'):
                label += ' PRE_COMM matches app request'
    if label:
        events.append((timestamp, label))
    if len(events) == 12:
        break
if events:
    print('Official traffic, relative milliseconds, no encrypted payloads:')
    for timestamp, label in events:
        print(round((timestamp - events[0][0]) / 1000, 1), label)
