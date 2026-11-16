#!/usr/bin/env bash
# Installs a Rust toolchain and a JDK 21 INSIDE this project folder.
#
# Why local: the target machine has no Rust and no JDK, and installing
# system-wide would need administrator rights and would change the user's
# machine. Everything lands in ./tools, which is git-ignored.
#
# Idempotent: skips anything already present.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
mkdir -p tools artifacts/logs

RUST_VERSION="1.100.0"  # pinned for reproducibility; see docs/DECISIONS.md
JDK_VERSION="21.0.13+9"

# --- Rust --------------------------------------------------------------------
if [[ -x "$ROOT/tools/cargo/bin/cargo.exe" || -x "$ROOT/tools/cargo/bin/cargo" ]]; then
  echo "rust: already installed in tools/cargo"
else
  echo "rust: downloading rustup (x86_64-pc-windows-gnu)"
  curl -fsSL --max-time 120 -o tools/rustup-init.exe \
    https://static.rust-lang.org/rustup/dist/x86_64-pc-windows-gnu/rustup-init.exe
  RUSTUP_HOME="$ROOT/tools/rustup" CARGO_HOME="$ROOT/tools/cargo" \
    ./tools/rustup-init.exe -y --profile minimal \
      --default-host x86_64-pc-windows-gnu --default-toolchain stable \
    > artifacts/logs/rustup-install.log 2>&1
  export RUSTUP_HOME="$ROOT/tools/rustup" CARGO_HOME="$ROOT/tools/cargo"
  export PATH="$ROOT/tools/cargo/bin:$PATH"
  rustup component add rustfmt clippy
fi

# --- JDK ---------------------------------------------------------------------
if find "$ROOT/tools/jdk" -maxdepth 1 -name 'jdk-*' -type d | grep -q .; then
  echo "jdk: already installed in tools/jdk"
else
  echo "jdk: downloading Temurin $JDK_VERSION (zip, no installer)"
  mkdir -p tools/jdk
  curl -fsSL --max-time 600 -o tools/jdk/jdk21.zip \
    "https://github.com/adoptium/temurin21-binaries/releases/download/jdk-${JDK_VERSION}/OpenJDK21U-jdk_x64_windows_hotspot_$(echo "$JDK_VERSION" | tr '+' '_').zip"
  (cd tools/jdk && unzip -q -o jdk21.zip && rm jdk21.zip)
fi

echo
echo "Installed. Verify with: ./scripts/verify.sh"
