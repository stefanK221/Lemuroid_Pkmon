#!/usr/bin/env python3
"""Dump GETEXP's exact Thumb instructions, keeping ROM bytes private.

Requires clang and llvm-objdump (or ARM-capable llvm-objdump). This uses a
temporary ELF object so llvm-objdump sees Thumb machine code, not raw data.
"""
import argparse
import hashlib
from pathlib import Path
import subprocess
import tempfile

ROM_HASH = "389363bb2c87b8f197f23d67c11e459cc9f46befec75dc57a9e6fdac898eccc2"
START, END = 0x4A32C, 0x4ACD0


def disassemble(path: Path) -> str:
    data = path.read_bytes()
    if hashlib.sha256(data).hexdigest() != ROM_HASH:
        raise ValueError("Not the verified 5.0EX+BW ROM")
    if data[START:START + 2] != bytes.fromhex("f0 b5"):
        raise ValueError("Unexpected GETEXP Thumb function")
    with tempfile.TemporaryDirectory(prefix="gba-exp-") as name:
        temp = Path(name)
        (temp / "getexp.bin").write_bytes(data[START:END])
        (temp / "getexp.S").write_text(
            '.syntax unified\n.thumb\n.section .text\n'
            '.global gba_getexp\n.type gba_getexp,%function\n'
            'gba_getexp:\n.incbin "getexp.bin"\n', encoding="ascii")
        subprocess.run(["clang", "--target=armv4t-none-eabi", "-c",
                        "getexp.S", "-o", "getexp.o"], cwd=temp, check=True)
        result = subprocess.run(["llvm-objdump", "-d", "--triple=thumbv4t",
                                 "getexp.o"], cwd=temp, text=True, check=True,
                                stdout=subprocess.PIPE)
        return result.stdout


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("rom", type=Path)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    output = disassemble(args.rom)
    if args.output:
        args.output.write_text(output, encoding="utf-8")
        print(f"Disassembly written to {args.output}")
    else:
        print(output)
