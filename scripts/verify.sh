#!/usr/bin/env bash
# Local verification for the Rust core + JNI bridge.
#
# Runs everything that can be checked WITHOUT Minecraft: formatting, lints,
# unit tests, a release build of the native library, and the Java self-test that
# actually loads that library. Exit code is non-zero if any step fails.
#
# Usage:
#   ./scripts/verify.sh          # full run
#   ./scripts/verify.sh --quick  # skip the benchmark
#
# Toolchains are expected in ./tools (installed by scripts/install-toolchain.sh)
# or already on PATH. Nothing here needs administrator rights.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

# --- locate toolchains -------------------------------------------------------
if [[ -d "$ROOT/tools/cargo/bin" ]]; then
  export CARGO_HOME="$ROOT/tools/cargo"
  export RUSTUP_HOME="$ROOT/tools/rustup"
  export PATH="$ROOT/tools/cargo/bin:$PATH"
fi

if [[ -z "${JAVA_HOME:-}" ]]; then
  if [[ -d "$ROOT/tools/jdk" ]]; then
    JAVA_HOME="$(find "$ROOT/tools/jdk" -maxdepth 1 -type d -name 'jdk-*' | head -1)"
    export JAVA_HOME
  fi
fi

mkdir -p artifacts/logs
STAMP="$(date +%Y%m%d-%H%M%S)"

require() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "MISSING TOOL: $1 — run scripts/install-toolchain.sh" >&2
    exit 127
  fi
}
require cargo
require "$JAVA_HOME/bin/javac"

echo "== 1/6 rustfmt =="
(cd rust && cargo fmt --all -- --check)

echo "== 2/6 clippy (deny warnings) =="
(cd rust && cargo clippy --workspace --all-targets -- -D warnings 2>&1 | tee "$ROOT/artifacts/logs/clippy-$STAMP.log" | tail -3)

echo "== 3/6 cargo test =="
(cd rust && cargo test --workspace 2>&1 | tee "$ROOT/artifacts/logs/cargo-test-$STAMP.log" | grep -E 'test result|FAILED')

echo "== 4/6 release build =="
(cd rust && cargo build --release 2>&1 | tail -2)

NATIVE_DIR="$ROOT/rust/target/release"
if [[ ! -f "$NATIVE_DIR/starflight_jni.dll" && ! -f "$NATIVE_DIR/libstarflight_jni.so" && ! -f "$NATIVE_DIR/libstarflight_jni.dylib" ]]; then
  echo "FAIL: native library not produced in $NATIVE_DIR" >&2
  exit 1
fi

echo "== 5/6 java compile + self-test =="
mkdir -p java/out
"$JAVA_HOME/bin/javac" -Xlint:all -d java/out java/src/main/java/dev/starport/*.java
"$JAVA_HOME/bin/java" -Djava.library.path="$NATIVE_DIR" -cp java/out dev.starport.SelfTest \
  | tee "$ROOT/artifacts/logs/selftest-$STAMP.log" | tail -4

if [[ "${1:-}" != "--quick" ]]; then
  echo "== 6/6 benchmark (informational) =="
  "$JAVA_HOME/bin/java" -Djava.library.path="$NATIVE_DIR" -cp java/out dev.starport.Bench \
    | tee "$ROOT/artifacts/logs/bench-$STAMP.log" | head -6
else
  echo "== 6/6 benchmark skipped (--quick) =="
fi

echo
echo "VERIFY: PASS"
