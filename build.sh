#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")" && pwd)"
export ANDROID_HOME="${ANDROID_HOME:-/root/Android/Sdk}"
export ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-$ANDROID_HOME}"
export ANDROID_NDK_ROOT="${ANDROID_NDK_ROOT:-$ANDROID_HOME/ndk/27.3.13750724}"
NDK_BIN="$ANDROID_NDK_ROOT/toolchains/llvm/prebuilt/linux-x86_64/bin"

export CC_aarch64_linux_android="$NDK_BIN/aarch64-linux-android26-clang"
export CXX_aarch64_linux_android="$NDK_BIN/aarch64-linux-android26-clang++"
export AR_aarch64_linux_android="$NDK_BIN/llvm-ar"
export CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER="$NDK_BIN/aarch64-linux-android26-clang"

export CARGO_TARGET_DIR="${CARGO_TARGET_DIR:-/tmp/wc-target}"

echo "==> Building Rust native library & sonoro-deb (aarch64-linux-android release)"
cd "$ROOT_DIR/wuwa-connect"
cargo build --release --target aarch64-linux-android

JNI_DIR="$ROOT_DIR/android/app/src/main/jniLibs/arm64-v8a"
ASSETS_DIR="$ROOT_DIR/android/app/src/main/assets"
mkdir -p "$JNI_DIR" "$ASSETS_DIR"
rm -f "$JNI_DIR/libsingbox.so"
cp "$CARGO_TARGET_DIR/aarch64-linux-android/release/libwuwa_connect.so" "$JNI_DIR/libwuwa_connect.so"
cp "$CARGO_TARGET_DIR/aarch64-linux-android/release/sonoro-deb" "$ASSETS_DIR/sonoro-deb"
chmod 755 "$ASSETS_DIR/sonoro-deb"

SINGBOX_BIN="$ROOT_DIR/cache/sing-box-1.14.4-android-arm64/sing-box"
if [ -f "$SINGBOX_BIN" ]; then
    cp "$SINGBOX_BIN" "$ASSETS_DIR/sing-box"
    chmod 755 "$ASSETS_DIR/sing-box"
fi

GRADLE_BIN="$(command -v gradle || echo /root/.gradle/wrapper/dists/gradle-9.6.1-bin/4ticwg1pgcbps2hj28r8so764/gradle-9.6.1/bin/gradle)"
echo "==> Building Android Release APK with Gradle"
cd "$ROOT_DIR/android"
"$GRADLE_BIN" assembleRelease --no-daemon

APK_SRC="$ROOT_DIR/android/app/build/outputs/apk/release/app-release.apk"
cp "$APK_SRC" "$ROOT_DIR/wuwa-connect.apk"
echo "==> Done: $ROOT_DIR/wuwa-connect.apk"
