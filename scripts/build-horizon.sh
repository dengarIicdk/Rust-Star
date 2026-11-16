#!/usr/bin/env bash
# Builds horizon/starflight/dist/starflight.hmod.
#
# `cargo horizon-mod build` refuses to pack a native mod unless the mod sits
# inside a game checkout (it will not download an SDK, and no signed SDK release
# exists for the pinned build). So this stages a copy inside the checkout, packs
# it, copies the .hmod back, and puts the checkout back exactly as it was.
set -euo pipefail
cd "$(dirname "$0")/.."
ROOT="$PWD"

MINGW="/c/Users/DenGarChiK/AppData/Local/Microsoft/WinGet/Packages/BrechtSanders.WinLibs.POSIX.UCRT_Microsoft.Winget.Source_8wekyb3d8bbwe/mingw64/bin"
export PATH="$MINGW:$ROOT/tools/cargo/bin:$PATH"
export CARGO_HOME="$ROOT/tools/cargo"
export RUSTUP_HOME="$ROOT/tools/rustup"
export CC_x86_64_pc_windows_gnu=x86_64-w64-mingw32-gcc

GAME=$(ls -d "$ROOT"/tools/cargo/git/checkouts/horizon-*/69ab572 2>/dev/null | head -1)
[ -n "$GAME" ] || { echo "FAIL: Horizon checkout not found"; exit 1; }

STAGE="$GAME/mods/starflight-local"
BACKUP=$(mktemp)
cleanup() {
  cp "$BACKUP" "$GAME/Cargo.toml"
  rm -f "$BACKUP"
  rm -rf "$STAGE"
}
trap cleanup EXIT

mkdir -p "$STAGE/src"
cp "$ROOT/horizon/starflight/src/lib.rs" "$ROOT/horizon/starflight/src/strike.rs" "$STAGE/src/"
cp "$ROOT/horizon/starflight/horizon-mod.toml" "$STAGE/"

cat > "$STAGE/Cargo.toml" <<TOML
[package]
name = "mod-starflight"
version = "0.1.0"
edition = "2021"

[dependencies]
starflight = { path = "$ROOT/rust/starflight" }
horizon-mod-api = { path = "../../crates/horizon-mod-api" }

[package.metadata.horizon]
sdk = "$(grep -oE 'horizon = "[0-9a-f]{40}"' "$STAGE/horizon-mod.toml" | cut -d'"' -f2)"
TOML

# Register the stage as a workspace member so it builds with the game's lockfile.
cp "$GAME/Cargo.toml" "$BACKUP"
python - "$GAME/Cargo.toml" <<'PY'
import sys
p = sys.argv[1]
s = open(p, encoding='utf-8').read()
s = s.replace('members = [\n', 'members = [\n    "mods/starflight-local",\n', 1)
open(p, 'w', encoding='utf-8').write(s)
PY

cd "$STAGE"
cargo horizon-mod build

mkdir -p "$ROOT/horizon/starflight/dist"
cp "$STAGE/target/horizon-mod/starflight.hmod" "$ROOT/horizon/starflight/dist/"
echo "built horizon/starflight/dist/starflight.hmod"
cargo horizon-mod check "$ROOT/horizon/starflight/dist/starflight.hmod" --kind native-mod
