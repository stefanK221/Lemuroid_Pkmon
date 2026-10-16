#!/usr/bin/env python3
"""Analyze the exact user-provided Emerald 5.0EX+BW ROM without redistributing it.

Run: python tools/analyze_gba_plus_rom.py /path/to/game.gba
This validates candidate ROM data tables; it does NOT assert a battle hook.
"""
import argparse
import hashlib
import struct
from pathlib import Path

EXPECTED = "389363bb2c87b8f197f23d67c11e459cc9f46befec75dc57a9e6fdac898eccc2"
HEADER = b"POKEMON EMERBPEE"


def analyze(path: Path) -> dict:
    rom = path.read_bytes()
    digest = hashlib.sha256(rom).hexdigest()
    if digest != EXPECTED:
        raise ValueError("Unsupported ROM hash; refusing to infer battle offsets")
    if rom[0xA0:0xB0] != HEADER:
        raise ValueError("Unexpected cartridge header")
    def ptr(at):
        address = struct.unpack_from("<I", rom, at)[0]
        offset = address - 0x08000000
        return {"header": hex(at), "address": hex(address),
                "offset": hex(offset) if 0 <= offset < len(rom) else None,
                "valid": 0 <= offset < len(rom)}
    stats = ptr(0x1BC)
    names = ptr(0x144)
    moves = ptr(0x1CC)
    move_table = 0x31C898
    assert rom[move_table + 12:move_table + 17] == bytes.fromhex('00 28 00 64 23')
    assert rom[move_table + 33 * 12:move_table + 33 * 12 + 5] == bytes.fromhex('00 23 00 5F 23')
    if not stats["valid"] or not names["valid"]:
        raise ValueError("Verified ROM data pointers missing")
    base = int(stats["offset"], 16)
    samples = {}
    for species in (1, 6, 25, 277, 303, 404):
        record = rom[base + species * 28:base + (species + 1) * 28]
        samples[str(species)] = {"base_stats": list(record[:6]), "growth_rate": record[19]}
    # Emerald's battle-script opcode 0x23 is GETEXP upstream. The byte
    # alone is NOT a unique signature; it is deliberately not used as a hook.
    return {"sha256": digest, "bytes": len(rom), "header": HEADER.decode(),
            "species_name_table": names, "base_stats_table": stats,
            "move_table_pointer_at_0x1cc": moves, "verified_move_table_file_offset": hex(move_table),
            "sample_species": samples,
            "battle_experience_hook_verified": False,
            "note": "GETEXP opcode 0x23 alone cannot locate a safe ROM-specific award routine."}


if __name__ == "__main__":
    import json
    parser = argparse.ArgumentParser()
    parser.add_argument("rom", type=Path)
    args = parser.parse_args()
    print(json.dumps(analyze(args.rom), ensure_ascii=False, indent=2))
