"""Unit tests for GBA Plus's offline save editor; no copyrighted ROM required."""
import struct
import unittest

from emerald_save_6v import (
    LENGTHS, PERMS, SIGNATURE, active_sections, checksum, patch_six_v, party_records,
)

def write32(buf, offset, value):
    struct.pack_into("<I", buf, offset, value)

def write16(buf, offset, value):
    struct.pack_into("<H", buf, offset, value)

def make_save(party_count=1):
    data = bytearray(131072)
    for slot, sequence in ((0, 10), (1, 11)):
        offsets = {}
        for index in range(14):
            off = slot * 57344 + index * 4096
            offsets[index] = off
            write16(data, off + 0xFF4, index)
            write32(data, off + 0xFF8, SIGNATURE)
            write32(data, off + 0xFFC, sequence)

        if slot == 1:
            section = offsets[1]
            write32(data, section + 0x234, party_count)
            for member in range(party_count):
                pokemon = section + 0x238 + member * 100
                personality = 0
                trainer_id = 0x12345678 + member
                key = personality ^ trainer_id
                write32(data, pokemon, personality)
                write32(data, pokemon + 4, trainer_id)
                decrypted = bytearray(48)
                write16(decrypted, 0, 25 + member)
                write32(decrypted, 40, 0x40000000 | member)
                write16(data, pokemon + 28, sum(struct.unpack("<24H", decrypted)) & 0xFFFF)
                for index in range(12):
                    write32(data, pokemon + 32 + index * 4,
                            struct.unpack_from("<I", decrypted, index * 4)[0] ^ key)

        for index, off in offsets.items():
            write16(data, off + 0xFF6, checksum(data[off:off + LENGTHS[index]]))
    return bytes(data)

class SavePatcherTests(unittest.TestCase):
    def test_round_trip_and_preserve_input(self):
        data = make_save()
        edited, before = patch_six_v(data)
        self.assertEqual(before, [[0, 0, 0, 0, 0, 0]])
        self.assertEqual(len(edited), len(data))
        self.assertNotEqual(edited, data)
        self.assertEqual(data, make_save())
        sequence, sections = active_sections(edited)
        self.assertEqual(sequence, 11)
        self.assertEqual(party_records(edited, sections)[0][-1], [31] * 6)
        self.assertEqual(edited[:57344], data[:57344])

    def test_select_first_party_member(self):
        data = make_save()
        edited, before = patch_six_v(data, [1])
        self.assertEqual(len(before), 1)
        _, sections = active_sections(edited)
        self.assertEqual(party_records(edited, sections)[0][-1], [31] * 6)

    def test_six_member_noncontiguous_selection_preserves_others(self):
        original = make_save(party_count=6)
        edited, before = patch_six_v(original, [2, 4, 6])
        self.assertEqual(len(before), 3)
        _, old_sections = active_sections(original)
        _, new_sections = active_sections(edited)
        old_records = party_records(original, old_sections)
        new_records = party_records(edited, new_sections)
        self.assertEqual(len(new_records), 6)
        for index in range(6):
            if index in (1, 3, 5):
                self.assertEqual(new_records[index][-1], [31] * 6)
            else:
                self.assertEqual(new_records[index][-1], old_records[index][-1])
                start = old_records[index][0]
                self.assertEqual(edited[start:start + 100], original[start:start + 100])
        self.assertEqual(edited[:57344], original[:57344])

    def test_six_member_patch_all(self):
        original = make_save(party_count=6)
        edited, before = patch_six_v(original)
        self.assertEqual(len(before), 6)
        _, sections = active_sections(edited)
        self.assertTrue(all(record[-1] == [31] * 6
                            for record in party_records(edited, sections)))

    def test_invalid_party_slot_rejected(self):
        for invalid in ([0], [2], [], [-1]):
            with self.subTest(invalid=invalid), self.assertRaises(ValueError):
                patch_six_v(make_save(), invalid)

    def test_bad_signature_rejected(self):
        raw = bytearray(make_save())
        raw[0xFF8:0xFFC] = b"FAIL"
        with self.assertRaises(ValueError):
            patch_six_v(raw)

    def test_bad_length_rejected(self):
        with self.assertRaises(ValueError):
            patch_six_v(b"x" * 64)

if __name__ == "__main__":
    unittest.main()
