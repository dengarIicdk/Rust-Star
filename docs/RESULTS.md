# RESULTS

Date: 2026-11-16. What is built, what is proven, what is not.

## Implemented

| Area | Files | Lines (approx) |
|---|---|---|
| Rust core | `rust/starflight/src/{lib,vec3,limits,projectile,beam,plan}.rs` | ~1 100 |
| JNI bridge | `rust/starflight-jni/src/lib.rs` | ~430 |
| Java adapter surface | `java/src/main/java/dev/starport/{NativeBridge,SelfTest,Bench}.java` | ~700 |
| Scripts | `scripts/{install-toolchain,verify}.sh` | ~120 |
| Docs | `docs/*.md`, `README.md`, `CHANGELOG.md` | ~2 000 |

## Actually built

- ✅ `rust/target/release/starflight_jni.dll` (≈1.28 MB, x86-64 Windows)
- ✅ Java classes in `java/out/`
- Toolchains: Rust 1.100.0, Temurin JDK 21.0.13 in `tools/` (git-ignored)

## Actually tested — 3 green runs

```
cargo fmt --check                  PASS
cargo clippy -D warnings           PASS
cargo test --workspace             36 passed, 0 failed
                                  7 passed, 0 failed
cargo build --release              PASS
javac -Xlint:all                   PASS
java dev.starport.SelfTest         30 passed, 0 failed  → RESULT: PASS
```

Evidence: `artifacts/logs/`.

## Measured (not assumed)

| Metric | Value |
|---|---|
| single JNI round-trip | ~9 ns |
| batched position copy | ~46 ns/position |
| per-call position copy | ~91 ns/position |
| beam r=32, y=-64..320 | 1 242 780 positions |
| concurrent calls (8×2000) | 0 panics, 0 leaks |

## Not tested

- ❌ Anything inside Minecraft.
- ❌ Any rendering, sound, cutscene.
- ❌ Multiplayer, persistence.
- ❌ Non-Windows platforms.

## Blocked

| Blocker | Reason | Unblock by |
|---|---|---|
| Minecraft adapter | no Fabric/Gradle toolchain here | run Stage 3 on a machine with one, or provide a dev environment |
| Assets | ARR upstream; no originals drawn yet | design textures/sounds; record licences |
| Historical skin | needs the user's visual reference (per brief) | user supplies a reference |

## How to run what exists

```bash
# install (idempotent)
./scripts/install-toolchain.sh

# verify everything
./scripts/verify.sh

# or just the bridge
export JAVA_HOME="$PWD/tools/jdk/jdk-21.0.13+9"
"$JAVA_HOME/bin/java" -Djava.library.path=rust/target/release \
  -cp java/out dev.starport.SelfTest
```

There is **no installable mod yet**. There is a proven native core and bridge.