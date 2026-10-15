# Verified Emerald-format save research (local analysis, 2026-10-15)

The user's local 128 KiB save was parsed offline; **do not commit the user's ROM or save**.

- Valid Emerald-style dual save slots, active sequence 134.
- The active party count is 6 at section 1 offset 0x234, party records begin at 0x238.
- Individual Gen III party data checksums verified before making a separate modified save.
- The six party Pokémon have IVs (HP/Atk/Def/Spe/SpA/SpD, stored bit order):
  1. [31,31,31,31,5,31]
  2. [31,31,31,31,31,31]
  3. [31,31,31,31,31,31]
  4. [31,31,31,31,0,31]
  5. [31,9,31,31,31,31]
  6. [31,20,31,31,31,31]
- The modified copy sets the six five-bit IV slots of each team member to 31, preserving the upper two metadata bits, recalculating Pokémon checksums and the containing save section checksum.
- Modified file was reparsed and confirmed to contain six 6V party records with valid checksums. **In-game testing on the real ROM is still needed.**
- No claim of support for nicknames, nature, moves, abilities, boxed Pokémon or post-edit recalculation yet.
- The original user save was not overwritten.

Work remains to provide a production-safe UI, save import/export, isolated backup, nature editing, and native live cheats.
