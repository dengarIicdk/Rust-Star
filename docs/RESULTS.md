# RESULTS

Date: 2026-11-16. What is built, what is proven, what is not.

## Verified on this machine (real runs, not claims)

| Layer | Check | Result |
|---|---|---|
| Rust unit tests | `cargo test --workspace` | **38 passed** |
| Rust lints | `cargo clippy --all-targets -D warnings` | clean |
| Rust format | `cargo fmt --check` | clean |
| Native build | `cargo build --release` | `starflight_jni.dll` |
| JNI self-test (real `.dll`) | `dev.starport.SelfTest` | **30/30** |
| Mod build | `gradle build` | `starflight-0.1.0.jar` (with natives) |
| **Dedicated server** | `scripts/server-itest.sh` | **RESULT: PASS** |
| **Game client** | `gradle runClient` | launches, 0 missing textures |

### Server integration evidence

```
Native core 0.1.0 (ABI 1)
STARFLIGHT-REGISTRY:    PASS rocketItem=true scopeItem=true rocketEntity=true
STARFLIGHT-FLIGHT:      PASS spawned=true speed=2.500 id=1
STARFLIGHT-PERSISTENCE: PASS wrote=true read=true matches=true rejectsCorrupt=true
STARFLIGHT-RATELIMIT:   PASS burst3=true blocked4=true blockedNearEnd=true
                             resetAfterWindow=true perPlayer=true forget=true
STARFLIGHT-CONFIG:      PASS radius=8.0 damage=60.0 speed=2.5 lifetime=400
                             worldEdit=true maxActive=4 nativeValid=true
STARFLIGHT-SELFTEST:    QUEUED slot=1001 total=232 probe=8,64,0 solidBefore=true
STARFLIGHT-VERIFY:      PASS airNow=true
```

Strike trace from a radius-3 beam, showing the per-tick budget in action:
```
strike slot=1000 applied=16384/75648 examined=16384 complete=false
strike slot=1000 applied=32768/75648 examined=16384 complete=false
strike slot=1000 applied=49152/75648 examined=16384 complete=false
strike slot=1000 applied=65536/75648 examined=16384 complete=false
strike slot=1000 applied=75648/75648 examined=10112 complete=true
```

Hostile config test (`radius=99999 damage=NaN speed=-5 lifetimeTicks=0`):
```
Config tuning rejected by the native core (...); using defaults
Config: radius=8.0 damage=60.0 speed=2.5 lifetime=400 worldEdit=true maxActive=8
```
The server kept running.

## Actually built

- `rust/target/release/starflight_jni.dll` (~1.28 MB, x86-64 Windows)
- `mods/starflight/build/libs/starflight-0.1.0.jar` — installable, bundles the
  native library under `natives/win-x64/`
- Java classes for the standalone bridge test in `java/out/`

## Measured (not assumed)

| Metric | Value |
|---|---|
| single JNI round-trip | ~9 ns |
| batched position copy | ~46 ns/position |
| per-call position copy | ~91 ns/position |
| beam r=32, y=-64..320 | 1 242 780 positions |
| block budget enforced | 16 384 positions/tick |

## In game — what "verified in game" means here

- ✅ The client **launches** with the mod and loads all assets (0 missing
  textures or models).
- ✅ A **dedicated server** loads the mod, builds plans and **mutates real
  blocks**, verified by reading the world back.
- ❌ **Not** verified: flying a rocket by hand from a player's hand, seeing the
  explosion, hearing it, or a screenshot of the item in an inventory. That needs
  a human at the keyboard.

## Not tested

- Rocket launch by an actual player with mouse input.
- Visual appearance of the rocket in flight.
- Multiplayer with two real clients.
- Non-Windows platforms.

## Blocked

| Blocker | Reason |
|---|---|
| Human-visible gameplay check | needs a person to play; cannot be automated here |
| Historical skin | needs the user's visual reference (per brief) |

## How to run

```bash
./scripts/verify.sh          # Rust + JNI bridge
./scripts/server-itest.sh    # real dedicated server, all six checks
cd mods/starflight && ../../tools/gradle-8.14.3/bin/gradle build   # the JAR
```

Install: drop `starflight-0.1.0.jar` into `.minecraft/mods` with Fabric Loader
0.16+ and Fabric API for 1.21.1. No `java.library.path` setup needed — the
native library is extracted from the jar automatically.
