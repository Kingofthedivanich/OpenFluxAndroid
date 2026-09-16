#!/bin/bash
# Builds the OpenFlux core (openflux CLI) from a sibling checkout and installs
# the resulting binaries into app/src/main/jniLibs/<abi>/libp1npplydtransport.so,
# the name NativeProcessSupervisor.kt execs at runtime.
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CORE_DIR="${CORE_DIR:-$SCRIPT_DIR/../core}"
JNI_NAME="libp1npplydtransport.so"

if [ ! -f "$CORE_DIR/build_android.sh" ]; then
    echo "core repo not found at $CORE_DIR (set CORE_DIR=/path/to/OpenFlux)" >&2
    exit 1
fi

pinned="$(git -C "$CORE_DIR" describe --tags --exact-match 2>/dev/null || git -C "$CORE_DIR" rev-parse --short HEAD)"
echo "building core @ $pinned"

( cd "$CORE_DIR" && ./build_android.sh )

for abi_dir in "$CORE_DIR"/output/android/*/; do
    abi="$(basename "$abi_dir")"
    src="$abi_dir/openflux"
    dst_dir="$SCRIPT_DIR/app/src/main/jniLibs/$abi"
    if [ ! -f "$src" ]; then
        continue
    fi
    mkdir -p "$dst_dir"
    cp "$src" "$dst_dir/$JNI_NAME"
    echo "installed: $dst_dir/$JNI_NAME"
done
