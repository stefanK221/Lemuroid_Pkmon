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
- **Important mismatch:** 32-bit value at header `0x1CC` is `0x09900000`. This points outside the supplied ROM; it **must not** be trusted as a move-table pointer. `QuickToolsProfile.tacklePp()` currently relies on this pointer, so **Resolved in commit `4b382ce7`:** exact-ROM move table starts at file offset `0x31C898`; 12-byte records verify Pound #1 (`00 28 00 64 23`) and Tackle #33 (`00 23 00 5F 23`). The app now SHA-gates and checks both records, rather than following the invalid header pointer. Phone verification of gift Pokémon remains outstanding.
- Emerald upstream battle-script opcode `0x23` corresponds to `GETEXP`; byte `0x23` alone is common and does **not** locate this specific ROM hack's actual battle script or execution routine.

## Battle GETEXP entry now located (ROM checked)

The exact user-provided SHA-matched file contains a contiguous **250-entry Thumb battle-command dispatcher** at **ROM offset `0x310500`**. Every table entry resolves to a valid odd Thumb address in the ROM. The table's opcode `0x00` points to `0x08045FFD`; **opcode `0x23` (GETEXP)** points to **Thumb address `0x0804A32D`** (code offset **`0x4A32C`**); the following command `0x24` points to `0x0804ACD1`. These pointers were read and checked against the original uploaded ROM, not inferred from the generic Emerald address map.

Disassembly of the actual handler at `0x0804A32C` confirms this is a **seven-state Thumb state machine**: a per-battle state byte at an offset `+0x1c` is compared with `6` and used to index seven **even-address Thumb `mov pc`** jump targets in the table at file offset `0x4A388` (entry + `0x5C`). The targets are `0x0804A38C`, `0x0804A3A8`, `0x0804A414`, `0x0804A52E`, `0x0804A850`, `0x0804A954`, and `0x0804AC40`. They are even addresses because the switch uses `mov pc, r0`, **not** an interworking `bx` instruction. The audit script verifies the Thumb prologue, switch bytes and all seven targets against the exact hash-matched ROM.

**Remaining work:** inspect the seven phases to find the precise call that invokes the native level-up/move-learning path and how EXP points are calculated. Avoid jumping directly into an arbitrary phase without preserving the handler's state machine and original battle-script semantics.

This is a concrete discovery of the experience-award **command entry point**, NOT yet a verified code patch. Need Thumb disassembly of the roughly `0x9A4`-byte command region, trace the normal per-party distribution and call sites, and capture in-game behavior before changing command logic. Never replace GETEXP with a guessed per-frame XP write.

## Experience-sharing delivery blocker

The script command GETEXP address is located, but there is still no verified *per-recipient native award callback*, battle event identity, or safe patch/ABI. No game runtime experience-sharing hook has been installed. The existing C/Kotlin EXP allocation and dispatch code is **unit-test-only scaffolding** and must not be presented as an enabled gameplay feature.

Next: disassemble the exact ROM using a Thumb/ARM-aware tool, trace the battle command dispatch table and battle-script `GETEXP` handler, identify its actual addresses and arguments, validate with emulator breakpoints and battle RAM traces, and only then connect the already-tested allocator to the native experience-award path. Preserve stock party EXP, level-up, moves and evolution logic.

Run `python tools/analyze_gba_plus_rom.py <user-local-ROM-file>` to reproduce the SHA-gated table inspection. Never upload/commit the ROM.
