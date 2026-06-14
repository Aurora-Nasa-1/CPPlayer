#!/bin/bash
# Build script for Android cross-compilation

set -e

echo "Building ALAC converter for Android..."

# Check if cargo-ndk is installed
if ! command -v cargo-ndk &> /dev/null; then
    echo "cargo-ndk not found. Installing..."
    cargo install cargo-ndk
fi

# Android targets
TARGETS=(
    "aarch64-linux-android"  # ARM64
)

# Add targets if not already added
for target in "${TARGETS[@]}"; do
    if ! rustup target list | grep -q "$target (installed)"; then
        echo "Adding target: $target"
        rustup target add "$target"
    fi
done

# Build for each target
echo "Building for Android targets..."
cargo ndk \
    -t arm64-v8a \
    build --release

echo "Build complete!"
echo ""
echo "Libraries location:"
echo "  ARM64:   target/aarch64-linux-android/release/librust_lib_flick_player.so"
echo ""
echo "Copy these to: android/app/src/main/jniLibs/<abi>/"
