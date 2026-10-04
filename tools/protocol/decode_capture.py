"""Decode saved stock-app serial logs only. No ADB, serial, or network access.

Usage: python decode_capture.py recent-logcat.txt > decoded.jsonl
Supports regular F0/opcode/length/payload/sum8 frames. E0 factory frames
are retained as opaque eight-byte frames. Unknown commands remain raw.
Units are left unknown until an A1 device-info response is observed.
"""
import argparse
import json
import re
from pathlib import Path

NAMES = {
    0xA0: 'heartbeat', 0xA1: 'device_info', 0xA2: 'error_log',
    0xA3: 'limits', 0xA4: 'workout_state_query', 0xA5: 'incline_query',
    0xA6: 'speed_query', 0xA7: 'fan_limit_query', 0xA8: 'fan_level_query',
    0xB0: 'set_workout_state', 0xB1: 'set_incline', 0xB2: 'set_speed',
    0xB3: 'set_fan_level', 0xB4: 'set_machine_units',
    0xD0: 'workout_state_notify', 0xD1: 'workout_status_notify',
    0xD2: 'incline_changed', 0xD3: 'speed_changed', 0xD4: 'physical_key',
    0xD5: 'user_presence', 0xD6: 'machine_lock_state',
}
STATES = {0: 'stop', 1: 'start', 2: 'pause', 10: 'confirm',
          0xAA: 'safety_key_not_plugged', 0xEA: 'emergency_stop'}


def u16(data):
    return int.from_bytes(data, 'big')


def signed8(value):
    return value - 256 if value >= 128 else value


class Decoder:
    def __init__(self):
        self.buffers = {}
        self.units = {}

    def feed(self, data, direction='unknown', process='offline'):
        key = process, direction
        buf = self.buffers.setdefault(key, bytearray())
        buf.extend(data)
        results = []
        while buf:
            if buf[0] != 0xF0:
                del buf[0]
                continue
            if len(buf) < 3:
                break
            size = 8 if buf[1] == 0xE0 else buf[2] + 4
            if len(buf) < size:
                break
            frame = bytes(buf[:size])
            if sum(frame[:-1]) & 255 != frame[-1]:
                results.append({'error': 'checksum_mismatch', 'hex': frame.hex().upper()})
                del buf[0]
                continue
            del buf[:size]
            op, payload = frame[1], frame[3:-1]
            result = {'direction': direction, 'process': process,
                      'opcode': f'{op:02X}', 'name': NAMES.get(op, 'unknown'),
                      'hex': frame.hex().upper(), 'checksum_valid': True,
                      'payload_hex': payload.hex().upper()}
            if op == 0xE0:
                result['name'] = 'opaque_factory_frame'
            else:
                result.update(self.fields(op, payload, direction, process))
            results.append(result)
        return results

    def fields(self, op, p, direction, process):
        if not p:
            return {}
        if op == 0xA1 and len(p) >= 7:
            units_code = p[6]
            unit = 'km' if units_code == 0 else 'mile' if units_code == 1 else None
            if direction == 'rx':
                self.units[process] = unit
            return {'model_id': p[0], 'hardware_version': list(p[1:3]),
                    'firmware_version': list(p[3:6]),
                    'protocol_unit_code': units_code, 'distance_unit': unit}
        unit = self.units.get(process)
        if op == 0xA0:
            return {'counter': p[0]}
        if op in (0xA4, 0xB0, 0xD0):
            return {'state_code': p[0], 'state': STATES.get(p[0], 'unknown')}
        if op in (0xA5, 0xB1, 0xD2):
            return {'incline_level': signed8(p[0])}
        if op in (0xA6, 0xB2, 0xD3) and len(p) >= 2:
            return {'speed_wire_raw': u16(p[:2]), 'speed_wire_scaled': u16(p[:2])/1000,
                    'speed_unit': unit + '/h' if unit else 'unknown',
                    'note': 'Stock setter adds 5 raw; received speed gets +0.001 after unit conversion.'}
        if op == 0xD1 and len(p) >= 9:
            return {'elapsed_time_raw': u16(p[:2]),
                    'distance_wire_raw': int.from_bytes(p[2:6], 'big'),
                    'distance_wire_scaled': int.from_bytes(p[2:6], 'big')/1000,
                    'distance_unit': unit, 'calories_reported': u16(p[6:8]),
                    'heart_rate_reported': p[8]}
        if op == 0xD4 and len(p) >= 2:
            return {'key_code': p[0], 'key_value': p[1],
                    'key_name': {51: 'volume_up', 52: 'volume_down'}.get(p[0], 'unmapped')}
        if op == 0xD5:
            return {'presence_code': p[0], 'presence': {0: 'unknown', 1: 'no', 2: 'yes'}.get(p[0], 'unknown')}
        if op == 0xD6:
            return {'lock_code': p[0], 'lock': {1: 'unlocked', 2: 'locked'}.get(p[0], 'unknown')}
        if op == 0xA3 and len(p) >= 7:
            return {'max_incline': p[0], 'min_incline': signed8(p[1]),
                    'max_speed_wire_scaled': u16(p[2:4])/1000,
                    'min_speed_wire_scaled': u16(p[4:6])/1000,
                    'speed_unit': unit + '/h' if unit else 'unknown', 'countdown': p[6]}
        if op in (0xA7, 0xA8, 0xB3):
            return {'fan_level': p[0]}
        return {}


# Use SNT only, avoiding duplicate SND scheduling logs and SerialCommManager's
# sign-extended hexadecimal formatting. Only this known tag is accepted.
LOG = re.compile(r'^\s*(\d\d-\d\d\s+\d\d:\d\d:\d\d\.\d+)\s+(\d+)\s+\d+\s+\w\s+SearialPortManager\s*:\s*(SNT|RCV)\s+([0-9A-Fa-f]+)\s*$')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('capture', type=Path)
    args = parser.parse_args()
    raw = args.capture.read_bytes()
    encoding = 'utf-16' if raw.startswith((b'\xff\xfe', b'\xfe\xff')) else 'utf-8-sig'
    decoder = Decoder()
    for line in raw.decode(encoding).splitlines():
        match = LOG.match(line)
        if not match:
            continue
        timestamp, pid, label, hex_data = match.groups()
        if len(hex_data) % 2:
            continue
        for result in decoder.feed(bytes.fromhex(hex_data), 'tx' if label == 'SNT' else 'rx', pid):
            print(json.dumps({'timestamp': timestamp, **result}))
    for (pid, direction), pending in decoder.buffers.items():
        if pending:
            print(json.dumps({'process': pid, 'direction': direction,
                              'error': 'incomplete_tail', 'hex': pending.hex().upper()}))


if __name__ == '__main__':
    main()
