# HANDOFF

> **Start here** when resuming. Updated at session end so a new chat can continue
> without reading the conversation.

## Project

- **Path:** `Z:\WIBECODING\MyProjects\Rust-Star`
- **Goal:** independent Rust implementation of the mechanics of the Minecraft
  mod *The Shooting Star [Demo]*, delivered as a Fabric mod (MC 1.21.1, Windows
  x64) via a thin Java adapter over a Rust core, plus a custom original rocket.
- **Licence constraint:** upstream mod + Rimulib are All Rights Reserved →
  clean-room only; no code/assets reused; behaviour implemented independently.

## Stack

| Item | Value |
|---|---|
| Rust | 1.100.0, x86_64-pc-windows-gnu, in `tools/` |
| JDK | Temurin 21.0.13+9 in `tools/` |
| Gradle | 8.14.3 in `tools/` (no wrapper committed) |
| MC / loader / API | 1.21.1 / 0.20.1 / fabric-api 0.116.18 |
| Loom | 1.11.8 |
| Build | `gradle build` in `mods/starflight` |

## Last completed step

**Stage 7:** installable JAR + client launch check. The full milestone is:

1. Server integration test **PASS** — six checks (registry, flight,
   persistence, rate limit, config, block verify).
2. Game client **launches** with the mod, native core loaded, **0 missing
   textures**.
3. `scripts/verify.sh` still green (Rust 38 tests + JNI 30/30).

## Build & test commands

```bash
./scripts/verify.sh            # Rust fmt/clippy/test + release .dll + JNI self-test
./scripts/server-itest.sh      # REAL dedicated server; all six checks; RESULT: PASS
cd mods/starflight && ../../tools/gradle-8.14.3/bin/gradle build   # the JAR
```

JAR: `mods/starflight/build/libs/starflight-0.1.0.jar`
Evidence: `artifacts/logs/itest-evidence.txt`

## Current state

- **Works (server-verified):** item/entity registration, rocket spawning +
  velocity, block strike via Rust plan (solid→air), persistence round-trip
  (+corrupt rejection), rate limiting, config validation, world-edit kill switch,
  active-strike cap, per-tick 16 384-block budget (trace shows 75 648 blocks over
  5 ticks).
- **Works (runs):** the client launches with no asset errors.
- **Not verified:** a person actually playing (firing by hand, hearing/seeing the
  impact). This is the honest limit of an automated session.

## Decisions in force

`docs/DECISIONS.md` D1–D9. Key ones: clean-room (D1); Fabric/MC 1.21.1 (D2);
Rust=logic/Java=integration (D3); batched JNI, reject-not-clamp,
`HARD_MAX_RADIUS=64` (D5); no panic across FFI (D6); tools inside `tools/` (D7);
no unverified code (D8); historical skin opt-in + neutral default (D9).

## Definite next steps

1. **T27 — human gameplay check.** Open the client, join a world
   (`level-type=flat` is set in `run/`), craft the rocket
   (iron + netherite scrap + redstone block), fire it, watch the column get
   carved. Report feel/UX issues.
2. **T16 — impact VFX + sound** (original assets only).
3. **T23 — two-client multiplayer pass.**

## Gotchas (learned this session)

- Git Bash has **no `pkill`**; kill Java with
  `taskkill //PID <pid> //F`. `pkill` in the itest was replaced with
  `taskkill` + `tasklist //FI ... //FO CSV //NH`.
- **Gradle stdin redirect breaks on Windows FIFOs.** The itest feeds commands
  via `tail -f` on a plain growing file, not a FIFO.
- **Old server processes hold `run/logs` locks** ("Device or resource busy").
  Kill all `java.exe` before re-running the itest.
- `-D` JVM flags must be passed **before** the Gradle task name, and forwarded
  through the Loom run config; `-D after runServer` goes to Gradle, not the JVM.
- MC 1.21.1 API drift vs older tutorials: `EntityRenderer<T>` single type param
  (no render states), `TooltipType` is `net.minecraft.item.tooltip.TooltipType`,
  `World.raycast` returns `BlockHitResult`, `PersistentState.Type` record needed.
- The upstream `.jar` is never downloaded/run (ARR + trust boundary).
- Never report a check as passing without running it. "Verified in game" has
  never meant "a person played it".

## Latest state: Horizon mod published and installable

Install URL (works in the launcher's "Add from GitHub"):

    https://github.com/dengarIicdk/Rust-Star/tree/horizon-mod

The plain repo URL also works, because `main` carries the same mod at
`horizon/starflight/` and the launcher walks a downloaded archive to depth 6
looking for a folder with `horizon-mod.toml` and `Cargo.toml`.

The install error that started this round ("holds no native mod") was caused by
that `Cargo.toml` naming the SDK by an absolute path into a local game checkout
and having a path dependency escaping the mod. Both are refused by the launcher.
The core is now vendored at `src/core` and the SDK is named the launcher-owned
way: `horizon-mod-api = { path = ".horizon/sdk/api/crates/horizon-mod-api" }`.

Proof, reproducible:

    bash scripts/verify.sh --quick     # VERIFY: PASS
    bash scripts/build-horizon.sh      # 47 tests, then packs the .hmod

`verify.sh` step 3b stages the published tree inside the game checkout and runs
the tests before packing, so tests and the shipped bytes are the same code.
Step 3c runs `pkgcheck`, a validator built against the launcher's own
`horizon-mod-package` crate, walking a repo exactly as the GitHub installer does.
It reports one installable native mod.
