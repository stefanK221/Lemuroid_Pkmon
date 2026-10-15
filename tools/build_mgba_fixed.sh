#!/usr/bin/env bash
set -euo pipefail
repo_root="$(cd "$(dirname "$0")/.." && pwd)"
source_dir="$repo_root/work/mgba-native"
build_root="$repo_root/work/mgba-build"
expected_revision=c758314a639aa0066e7b65a8341448181b73c804
test "$(git -C "$source_dir" rev-parse HEAD)" = "$expected_revision"
python3 "$repo_root/tools/patch_mgba_reset.py" "$source_dir"
common=(-DBUILD_QT=OFF -DBUILD_SDL=OFF -DBUILD_GL=OFF -DBUILD_GLES2=OFF -DBUILD_GLES3=OFF
        -DENABLE_DEBUGGERS=OFF -DENABLE_SCRIPTING=OFF -DDISABLE_DEPS=ON
        -DUSE_FFMPEG=OFF -DUSE_LZMA=OFF -DUSE_LIBZIP=OFF -DUSE_MINIZIP=OFF
        -DUSE_ELF=OFF -DUSE_JSON_C=OFF -DUSE_FREETYPE=OFF -DBUILD_LTO=OFF)
cmake -S "$source_dir" -B "$build_root/host" "${common[@]}" -DBUILD_SHARED=OFF -DBUILD_STATIC=ON
cmake --build "$build_root/host" --target mgba --parallel 2
cc -std=c11 -D_GNU_SOURCE -DENABLE_VFS -DENABLE_DIRECTORIES \
   -I "$source_dir/include" -I "$build_root/host/include" \
   "$repo_root/tools/test_mgba_cheat_reset.c" -L "$build_root/host" \
   -lmgba -lm -lpthread -o "$build_root/test-reset"
set +e
"$build_root/test-reset" --legacy
legacy_status=$?
set -e
if test "$legacy_status" -ne 1; then
    echo "Expected the legacy ROM-restoration failure (exit 1), got $legacy_status" >&2
    exit 1
fi
"$build_root/test-reset"
cc -std=c11 -D_GNU_SOURCE -DENABLE_VFS -DENABLE_DIRECTORIES \
   -I "$source_dir/include" -I "$build_root/host/include" \
   "$repo_root/tools/test_mgba_quick_tools.c" -o "$build_root/test-quick-tools"
"$build_root/test-quick-tools"
ndk_dir="${ANDROID_HOME}/ndk/27.2.12479018"
for abi in arm64-v8a armeabi-v7a x86 x86_64; do
    cmake -S "$source_dir" -B "$build_root/$abi" "${common[@]}" \
        -DCMAKE_TOOLCHAIN_FILE="$ndk_dir/build/cmake/android.toolchain.cmake" \
        -DANDROID_ABI="$abi" -DANDROID_PLATFORM=android-23 \
        -DBUILD_LIBRETRO=ON -DBUILD_SHARED=OFF -DBUILD_STATIC=OFF -DSKIP_LIBRARY=ON
    cmake --build "$build_root/$abi" --target mgba_libretro --parallel 2
    target="$repo_root/lemuroid-cores/lemuroid_core_mgba/src/main/jniLibs/$abi/libmgba_libretro_android.so"
    test -f "$target"
    cp "$build_root/$abi/mgba_libretro.so" "$target"
done
