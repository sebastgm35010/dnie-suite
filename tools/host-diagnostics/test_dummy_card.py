import unittest
import importlib.util
from pathlib import Path

spec = importlib.util.spec_from_file_location('dummy_card', Path(__file__).with_name('dummy-card.py'))
dummy = importlib.util.module_from_spec(spec)
spec.loader.exec_module(dummy)
ATR, response = dummy.ATR, dummy.response


class DummyCardTests(unittest.TestCase):
    def test_all_instructions_fail_closed_and_atr_checksum_is_valid(self):
        self.assertEqual(0, __import__('functools').reduce(lambda a, b: a ^ b, ATR[1:], 0))
        for instruction in range(256):
            answer = response(bytes([0, instruction, 0, 0]))
            self.assertEqual(2, len(answer))
            self.assertNotEqual(bytes.fromhex('9000'), answer)
        for instruction in (0x20, 0x24, 0x2A, 0x86):
            self.assertEqual(bytes.fromhex('6985'), response(bytes([0, instruction, 0, 0]) + b'private'))

    def test_empty_and_truncated_commands_are_rejected(self):
        for length in range(4):
            self.assertEqual(bytes.fromhex('6700'), response(b'\0' * length))
        self.assertEqual(bytes.fromhex('6A82'), response(bytes.fromhex('00a40000')))


if __name__ == '__main__':
    unittest.main()
