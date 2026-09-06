#!/usr/bin/env bash
# Sandbox toolchain for building EditorEs (JDK 17 + Android SDK 34 + NDK 26 for termux/emulator ndkBuild).
# Idempotent: every step is skipped when its artifact already exists.
set -euo pipefail

export ANDROID_HOME="${ANDROID_HOME:-/opt/android-sdk}"
SDKMANAGER="$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager"
SUDO=""
if [ "$(id -u)" -ne 0 ] && command -v sudo >/dev/null 2>&1; then SUDO="sudo"; fi

if ! command -v java >/dev/null 2>&1 || ! java -version 2>&1 | grep -q '"17\.'; then
  $SUDO apt-get update -qq
  $SUDO apt-get install -y -qq openjdk-17-jdk-headless unzip >/dev/null
fi

if [ ! -x "$SDKMANAGER" ]; then
  $SUDO mkdir -p "$ANDROID_HOME/cmdline-tools"
  $SUDO chown -R "$(id -u):$(id -g)" "$ANDROID_HOME"
  tmp="$(mktemp -d)"
  curl -sSL -o "$tmp/cmdline-tools.zip" https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip
  unzip -q -o "$tmp/cmdline-tools.zip" -d "$ANDROID_HOME/cmdline-tools"
  mv -f "$ANDROID_HOME/cmdline-tools/cmdline-tools" "$ANDROID_HOME/cmdline-tools/latest"
  rm -rf "$tmp"
fi

yes | "$SDKMANAGER" --licenses >/dev/null 2>&1 || true
# Versions come from composite-builds/build-logic/common/.../BuildConfig.kt (compileSdk, ndkVersion).
"$SDKMANAGER" --install "platform-tools" "platforms;android-34" "build-tools;34.0.0" "ndk;26.1.10909125" >/dev/null

# AGP's validateSigningDebug fails on this JDK when it has to auto-generate the debug keystore, so pre-create it.
# AGP resolves the location from Java's user.home, which can differ from $HOME in the sandbox.
JAVA_USER_HOME="$(java -XshowSettings:properties -version 2>&1 | sed -n 's/^ *user\.home = //p')"
for home in "$HOME" "${JAVA_USER_HOME:-$HOME}"; do
  if [ ! -f "$home/.android/debug.keystore" ]; then
    mkdir -p "$home/.android"
    keytool -genkey -v -keystore "$home/.android/debug.keystore" -alias androiddebugkey \
      -storepass android -keypass android -keyalg RSA -keysize 2048 -validity 10950 \
      -dname "CN=Android Debug,O=Android,C=US" >/dev/null 2>&1
  fi
done

cd "$(dirname "$0")/.."
printf 'sdk.dir=%s\n' "$ANDROID_HOME" > local.properties
chmod +x gradlew
