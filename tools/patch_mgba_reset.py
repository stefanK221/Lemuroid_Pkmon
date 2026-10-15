"""Patch the exact mGBA revision bundled by Lemuroid; fail on source drift."""
from pathlib import Path
import shutil
import sys

def patch(root):
    target = Path(root) / "src/platform/libretro/libretro.c"
    old = "void retro_cheat_reset(void) {\n\tmCheatDeviceClear(core->cheatDevice(core));\n}"
    new = ('#include "gba_plus_cheat_reset.h"\n\n'
           'void retro_cheat_reset(void) {\n'
           '\tgbaPlusResetCheats(core->cheatDevice(core));\n}')
    source = target.read_text(encoding="utf-8")
    if source.count(old) != 1:
        raise RuntimeError("Unexpected mGBA reset implementation; refusing to patch")
    target.write_text(source.replace(old, new), encoding="utf-8")
    shutil.copyfile(Path(__file__).with_name("mgba_cheat_reset.h"),
                    target.with_name("gba_plus_cheat_reset.h"))

if __name__ == "__main__":
    patch(sys.argv[1])

