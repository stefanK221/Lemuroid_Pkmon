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
    # This pinned upstream revision accidentally includes scripting.c in the
    # unconditional core list as well as CORE_SCRIPT. Libretro disables
    # scripting and directories, so compiling it there is invalid.
    core_cmake = Path(root) / "src/core/CMakeLists.txt"
    source = core_cmake.read_text(encoding="utf-8")
    entry = "\n\tscripting.c\n"
    if source.count(entry) != 1:
        raise RuntimeError("Unexpected mGBA core source list")
    core_cmake.write_text(source.replace(entry, "\n"), encoding="utf-8")

if __name__ == "__main__":
    patch(sys.argv[1])

