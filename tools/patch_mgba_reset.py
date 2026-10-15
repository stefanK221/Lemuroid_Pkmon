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
    # CMake already supplies version.c; the standalone libretro Makefile does
    # not. Avoid defining these globals twice when building through CMake.
    version_globals = ('const char* const projectVersion = "0.11-dev" GIT_VERSION;\n'
                       'const char* const projectName = "mGBA";\n')
    if source.count(version_globals) != 1:
        raise RuntimeError("Unexpected libretro version globals")
    source = source.replace(version_globals, "")
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
    # CORE_SRC already contains the platform memory implementation. The
    # standalone wrapper includes that same file and must not be globbed in.
    cmake = Path(root) / "CMakeLists.txt"
    source = cmake.read_text(encoding="utf-8")
    old_glob = "file(GLOB RETRO_SRC ${CMAKE_CURRENT_SOURCE_DIR}/src/platform/libretro/*.c)"
    if source.count(old_glob) != 1:
        raise RuntimeError("Unexpected libretro CMake source list")
    source = source.replace(old_glob,
        "set(RETRO_SRC ${CMAKE_CURRENT_SOURCE_DIR}/src/platform/libretro/libretro.c)")
    # The libretro target enables file-backed VFS but omits VFS_SRC; include
    # its FD implementation without enabling directory-dependent features.
    old_target = "add_library(${BINARY_NAME}_libretro SHARED ${CORE_SRC} ${RETRO_SRC} ${CORE_VFS_SRC})"
    if source.count(old_target) != 1:
        raise RuntimeError("Unexpected libretro target sources")
    source = source.replace(old_target, old_target[:-1] +
        " ${CMAKE_CURRENT_SOURCE_DIR}/src/util/vfs/vfs-fd.c)")
    cmake.write_text(source, encoding="utf-8")

if __name__ == "__main__":
    patch(sys.argv[1])

