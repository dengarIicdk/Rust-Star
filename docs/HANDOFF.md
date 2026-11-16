# HANDOFF

> **Start here** when resuming work. It is updated at the end of every session
> so a new chat can continue without reading the full conversation.

## Project

- **Path (absolute):** `Z:\WIBECODING\MyProjects\Rust-Star`
- **Goal:** an independent Rust implementation of the mechanics of the Minecraft
  mod *The Shooting Star [Demo]*, delivered as a Minecraft Java mod (Fabric,
  MC 1.21.1, Windows x64) with a thin Java adapter over a Rust core, plus a
  custom original rocket.
- **Licence constraint:** upstream mod + its dependency Rimulib are **All Rights
  Reserved** → clean-room implementation only; no code/assets from them;
  described behaviour implemented independently. `docs/LICENSE_AUDIT.md`.

## Stack and tools

| Item | Choice | Where |
|---|---|---|
| Rust | 1.100.0, `x86_64-pc-windows-gnu` | `tools/rustup`, `tools/cargo` |
| JDK | Temurin 21.0.13+9 (zip) | `tools/jdk/jdk-21.0.13+9` |
| Build | `cargo` + `javac` (no Gradle yet) | — |
| Minecraft | Fabric, MC 1.21.1 (planned; **not set up**) | — |

## Last completed step

Stage 2 checkpoint: **minimal Java ↔ Rust bridge proven end-to-end.**

```
scripts/verify.sh → rustfmt ✅ clippy ✅ cargo test (36+7) ✅ release .dll ✅
                    javac ✅ SelfTest 30/30 ✅ benchmark recorded ✅
                    VERIFY: PASS
```

Runs live on this machine. Logs: `artifacts/logs/`.

## Build & test commands

```bash
cd /z/WIBECODING/MyProjects/Rust-Star
./scripts/verify.sh            # everything (add --quick to skip benchmark)
./scripts/install-toolchain.sh # idempotent; only needed on a new machine
```

Hand-run of the bridge:

```bash
export CARGO_HOME="$PWD/tools/cargo" RUSTUP_HOME="$PWD/tools/rustup"
export PATH="$PWD/tools/cargo/bin:$PATH"
export JAVA_HOME="$PWD/tools/jdk/jdk-21.0.13+9"
cd rust && cargo build --release
cd ../java && "$JAVA_HOME/bin/javac" -d out src/main/java/dev/starport/*.java
"$JAVA_HOME/bin/java" -Djava.library.path=../rust/target/release -cp out dev.starport.SelfTest
```

## Current state

- Working: Rust core (`vec3, limits, projectile, beam, plan`), JNI crate, Java
  `NativeBridge` + `SelfTest` + `Bench`, `scripts/`.
- **Not working / not started:** any Fabric/Minecraft code, JAR, items, assets
  (textures/sounds), in-game test, multiplayer, persistence.
- There is **no installable mod yet**.

## Blocking problem (exact)

The Minecraft adapter (Stage 3) cannot be built or honestly verified in this
environment: no Gradle + Minecraft toolchain is available, and downloading it
was not proven possible here. This is an environment limitation, not a design
one — the design (D2, D3, D4 in `docs/DECISIONS.md`) is settled.

## Decisions in force

See `docs/DECISIONS.md` (D1–D9). Highlights: clean-room (D1); Fabric/MC 1.21.1
(D2); Rust=logic, Java=integration (D3); batched JNI with reused `int[]` (D4);
input rejected not clamped, `HARD_MAX_RADIUS=64` (D5); no panic across FFI (D6);
tools inside `tools/` (D7); no unverified adapter code (D8); historical skin is
opt-in and separate with neutral default (D9).

## Definite next step

1. Set up a Minecraft 1.21.1 + Fabric (Loader 0.16.x, API 0.115.x) dev
   environment where it is available, or ask the user to provide one.
2. Write `fabric.mod.json` + a minimal `StarportMod` entrypoint that compiles
   against real mappings, then register:
   - the custom rocket item `starflight:orbital_rocket` (launch on right-click);
   - a `ServerWorld.setBlockState` applier that drains a Rust plan via
     `takeBatch` at ≤ 16 384 positions/tick;
   - server-side permission/cooldown checks (Stage 6).
3. First acceptance test: obtain item → launch → projectile flies → block
   destroyed, observed in a real client.

## Important constraints & gotchas

- Do **not** modify the user's global git config; a project-local identity is
  already set.
- `tools/` and `target/` are large and git-ignored; do not commit them.
- Do **not** download or run the upstream `.jar`.
- New textures/sounds must have licences recorded in `docs/LICENSE_AUDIT.md`.
- Never report a check as passing without running it. "Verified in game" has
  never been true here.
- If continuing in a new chat: re-read this file, then `docs/PROGRESS.md`,
  `docs/TASKS.md`, and reconcile against `git log`/`git status` before acting.