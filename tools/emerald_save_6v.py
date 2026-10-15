"""Offline Gen-III Emerald-style save 6V patcher. Originals are never overwritten."""
import argparse
import itertools
import struct
from pathlib import Path

PERMS = list(itertools.permutations(range(4)))
LENGTHS = [3884, 3968, 3968, 3968, 3848, 3968, 3968, 3968, 3968, 3968, 3968, 3968, 3968, 2000]
SIGNATURE = 0x08012025

def u16(b, off): return struct.unpack_from("<H", b, off)[0]
def u32(b, off): return struct.unpack_from("<I", b, off)[0]
def w16(b, off, value): struct.pack_into("<H", b, off, value)
def w32(b, off, value): struct.pack_into("<I", b, off, value)

def checksum(b):
    length = len(b) & ~3
    value = sum(struct.unpack("<%dI" % (length // 4), b[:length])) & 0xffffffff
    return (value + (value >> 16)) & 0xffff

def active_sections(data):
    candidates = []
    for slot in range(2):
        found, sequences = {}, set()
        for physical in range(14):
            base = slot * 57344 + physical * 4096
            section_id = u16(data, base + 0xff4)
            if section_id >= 14 or section_id in found:
                raise ValueError("Bad section mapping")
            if u32(data, base + 0xff8) != SIGNATURE:
                raise ValueError("Bad save signature")
            if checksum(data[base:base + LENGTHS[section_id]]) != u16(data, base + 0xff6):
                raise ValueError("Bad section checksum")
            found[section_id] = base
            sequences.add(u32(data, base + 0xffc))
        if len(found) != 14 or len(sequences) != 1:
            raise ValueError("Invalid save slot")
        candidates.append((sequences.pop(), found))
    return max(candidates, key=lambda entry: entry[0])

def party_records(data, sections):
    base = sections[1]
    count = u32(data, base + 0x234)
    if not 1 <= count <= 6:
        raise ValueError("Party count must be 1..6")
    results = []
    for index in range(count):
        start = base + 0x238 + 100 * index
        mon = data[start:start + 100]
        pid, otid = struct.unpack_from("<II", mon)
        key = pid ^ otid
        plain = bytearray().join(struct.pack("<I", u32(mon, 32 + i) ^ key) for i in range(0, 48, 4))
        if sum(struct.unpack("<24H", plain)) & 0xffff != u16(mon, 28):
            raise ValueError("Pokemon checksum mismatch")
        permutation = PERMS[pid % 24]
        species = u16(plain, 12 * permutation.index(0))
        if not species:
            raise ValueError("Empty party member")
        iv_off = 12 * permutation.index(3) + 4
        old_field = u32(plain, iv_off)
        old_ivs = [(old_field >> (5 * n)) & 31 for n in range(6)]
        results.append((start, plain, key, iv_off, old_ivs))
    return results

def patch_six_v(raw):
    if len(raw) != 131072:
        raise ValueError("Expected 128 KiB save")
    old = bytearray(raw)
    _, sections = active_sections(old)
    records = party_records(old, sections)
    output = old[:]
    report = []
    for start, plain, key, iv_off, ivs in records:
        value = u32(plain, iv_off)
        w32(plain, iv_off, (value & 0xC0000000) | 0x3FFFFFFF)
        for off in range(0, 48, 4):
            w32(output, start + 32 + off, u32(plain, off) ^ key)
        w16(output, start + 28, sum(struct.unpack("<24H", plain)) & 0xffff)
        report.append(ivs)
    section = sections[1]
    w16(output, section + 0xff6, checksum(output[section:section + LENGTHS[1]]))
    _, verified = active_sections(output)
    if not all(all(iv == 31 for iv in record[-1]) for record in party_records(output, verified)):
        raise AssertionError("6V postcondition failed")
    return bytes(output), report

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("save", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    if args.save.resolve() == args.output.resolve():
        parser.error("Refusing to overwrite the input save")
    result, before = patch_six_v(args.save.read_bytes())
    args.output.write_bytes(result)
    print(f"Patched {len(before)} team members; checksum validation passed.")
