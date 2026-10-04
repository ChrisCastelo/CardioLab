"""Offline tests. Synthetic frames are not treadmill observations."""
import unittest
from decode_capture import Decoder, LOG, read_lines


def fixture(op, payload):
    data = bytes([0xF0, op, len(payload), *payload])
    return data + bytes([sum(data) & 255])


class CaptureTests(unittest.TestCase):
    def test_actual_logged_heartbeat(self):
        result = Decoder().feed(bytes.fromhex('F0A0010192'), 'tx')[0]
        self.assertEqual(result['name'], 'heartbeat')
        self.assertEqual(result['counter'], 1)
        self.assertTrue(result['checksum_valid'])

    def test_fragmentation_and_multiple_frames(self):
        decoder = Decoder()
        frame = fixture(0xD0, [0xAA])
        self.assertEqual(decoder.feed(frame[:2], 'rx'), [])
        result = decoder.feed(frame[2:] + fixture(0xD2, [255]), 'rx')
        self.assertEqual(result[0]['state'], 'safety_key_not_plugged')
        self.assertEqual(result[1]['incline_level'], -1)

    def test_checksum_recovery(self):
        result = Decoder().feed(bytes.fromhex('F0A0010193F0A0010293'), 'tx')
        self.assertEqual(result[0]['error'], 'checksum_mismatch')
        self.assertEqual(result[1]['counter'], 2)

    def test_workout_layout_and_units(self):
        decoder = Decoder()
        decoder.feed(fixture(0xA1, [32, 1, 2, 3, 4, 5, 0]), 'rx')
        result = decoder.feed(fixture(0xD1, [0, 60, 0, 0, 3, 232, 0, 12, 90]), 'rx')[0]
        self.assertEqual(result['elapsed_time_raw'], 60)
        self.assertEqual(result['distance_wire_scaled'], 1)
        self.assertEqual(result['distance_unit'], 'km')
        self.assertEqual(result['calories_reported'], 12)
        self.assertEqual(result['heart_rate_reported'], 90)

    def test_unknown_units_and_direction_isolation(self):
        decoder = Decoder()
        self.assertEqual(decoder.feed(bytes.fromhex('F0D3'), 'rx'), [])
        self.assertEqual(decoder.feed(bytes.fromhex('F0A0010192'), 'tx')[0]['counter'], 1)
        speed = fixture(0xD3, [0x27, 0x10])
        result = decoder.feed(speed[2:], 'rx')[0]
        self.assertEqual(result['speed_wire_scaled'], 10)
        self.assertEqual(result['speed_unit'], 'unknown')


    def test_log_dump_with_invalid_utf8(self):
        raw = (b'10-04 15:08:07.000  100  100 I Other: \xb8\n'
               b'10-04 15:08:07.664   954  1436 I SearialPortManager: SNT F0A0010192\n')
        matches = [LOG.match(line) for line in read_lines(raw)]
        self.assertEqual([m.group(4) for m in matches if m], ['F0A0010192'])

    def test_observed_start_sequence(self):
        decoder = Decoder()
        states = [decoder.feed(bytes.fromhex(h), 'rx')[0]['state']
                  for h in ('F0D00111D2', 'F0D00101C2', 'F0D00102C3')]
        self.assertEqual(states, ['start_countdown', 'start', 'pause'])

if __name__ == '__main__':
    unittest.main()
