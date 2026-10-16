# 5.0EX+BW exact-ROM audit (2026-10-16)

The user supplied `口袋妖怪 漆黑的魅影 5.0EX+BW.gba` for local inspection.
**The copyrighted ROM is not checked into this repository.**

## SHA-gated facts (from local original ROM)

- SHA-256: `389363bb2c87b8f197f23d67c11e459cc9f46befec75dc57a9e6fdac898eccc2`.
- ROM byte length: `33,554,188` (not an assumed full 32 MiB).
- Game header at `0xA0`: `POKEMON EMERBPEE`.
- Base-stat table pointer header `0x1BC`: `0x083203CC`; file offset `0x3203CC`.
- Species name table pointer header `0x144`: `0x083185C8`; file offset `0x3185C8`.
- Observed Charizard species 6 base stats `78/84/78/100/109/85`, growth-rate ID `3`.
- Species 303 base HP is 1; special Shedinja HP handling is needed.
- **Important mismatch:** 32-bit value at header `0x1CC` is `0x09900000`. This points outside the supplied ROM; it **must not** be trusted as a move-table pointer. `QuickToolsProfile.tacklePp()` currently relies on this pointer, so a safe fix requires locating/verifying the actual move table (or explicitly pinning vetted move data) before claiming all gift flows work.
- Emerald upstream battle-script opcode `0x23` corresponds to `GETEXP`; byte `0x23` alone is common and does **not** locate this specific ROM hack's actual battle script or execution routine.

## Experience-sharing delivery blocker

No verified native battle-award callback address, event identity, nor ROM-specific XP gain routine has been located yet. The existing C/Kotlin EXP allocation and dispatch code is **unit-test-only scaffolding** and must not be presented as an enabled gameplay feature.

Next: disassemble the exact ROM using a Thumb/ARM-aware tool, trace the battle command dispatch table and battle-script `GETEXP` handler, identify its actual addresses and arguments, validate with emulator breakpoints and battle RAM traces, and only then connect the already-tested allocator to the native experience-award path. Preserve stock party EXP, level-up, moves and evolution logic.

Run `python tools/analyze_gba_plus_rom.py <user-local-ROM-file>` to reproduce the SHA-gated table inspection. Never upload/commit the ROM.
