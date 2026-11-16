# CHANGELOG

All notable changes. Format follows [Keep a Changelog](https://keepachangelog.com/).

## [0.2.0] — 2026-11-16

### Added — Fabric adapter (`mods/starflight`)

- Fabric 1.21.1 project producing `starflight-0.1.0.jar` (Loom 1.11.8,
  Loader 0.20.1, API 0.116.18, Yarn 1.21.1+build.3, Java 21).
- `starflight:orbital_rocket` — custom rocket: right-click launches a
  server-side projectile that carves a vertical cylinder on impact.
- `starflight:strike_scope` — selects a beam target.
- Crafting recipes for both items (survival obtainable).
- `BlockApplier` — drains the Rust plan in ≤16 384-position batches per tick.
- `StrikeManager` — queue, budget accounting, tracing under
  `-Dstarflight.debug=true`.
- Client: entity renderer, item models, original neutral textures, mod icon.
- Native library bundled at `natives/win-x64/` and extracted at runtime, so no
  `java.library.path` setup is needed.

### Added — persistence & multiplayer

- `Persistence` — resumable strike *requests* survive a restart; geometry is
  re-validated on load and corrupt/unknown-format entries are dropped.
- `StrikeManager.resumePending` re-queues unfinished strikes on
  `SERVER_STARTED`.
- `RateLimiter` — 3 launches / 30 s per player, enforced server-side (the item
  cooldown is client-visible and bypassable).

### Added — configuration

- `config/starflight.properties`: radius, damage, speed, lifetimeTicks,
  allowWorldEdit, maxActiveStrikes.
- Every value is validated by the Rust core; an invalid combination falls back
  to defaults with a warning. `maxActiveStrikes` is additionally clamped to
  `[1, 8]` in code.
- `allowWorldEdit=false` rejects strikes before a plan is built.

### Verified on this machine

- Dedicated server: REGISTRY, FLIGHT, PERSISTENCE, RATELIMIT, CONFIG, VERIFY
  all PASS (`artifacts/logs/itest-evidence.txt`).
- Client: launches with the mod, native core loaded, 0 missing textures.
- Rust: 38 tests; JNI self-test 30/30.

### Known gaps

- No human gameplay check yet (see `docs/KNOWN_ISSUES.md` #1).
- No impact particles/sound; Windows x64 only; rocket art is simple.

## [0.1.0] — 2026-11-16

### Added — Rust core (`starflight`)

- `vec3` — NaN-safe vector math with sanitization.
- `limits` — hard safety limits; rejects (not clamps) NaN/Inf/out-of-range
  tuning; `HARD_MAX_RADIUS=64`, `MAX_BLOCKS_PER_TICK=16384`,
  `MAX_BLOCKS_PER_STRIKE=2_000_000`, damage/lifetime/speed caps.
- `projectile` — straight-flight stepped simulation with swept-AABB collision,
  deterministic epsilon tie-breaks, lifetime and world-bounds expiry.
- `beam` — vertical cylindrical volume: exact analytic footprint, streaming
  block source, no duplicates, `contains`.
- `plan` — chunk-batched mutation planner with a hard per-tick budget and a
  per-strike rejection cap.

### Added — JNI bridge (`starflight-jni`)

- `panic_boundary` — no Rust panic can cross into the JVM.
- Plan registry (`Mutex<HashMap<i32, Plan>>`) with explicit slot lifetime and
  leak-count diagnostics.
- Batched position hand-off via `set_int_array_region` (no per-block calls).
- ABI version handshake (`1`).

### Added — Java

- `NativeBridge` — typed native surface, library loading (JVM path then
  bundled `/<platform>/` subdir), ABI check, shared result codes.
- `SelfTest` — 30 checks, passes **against a real `.dll`**: validation,
  lifecycle, drain uniqueness, buffer safety, 8-thread hammering.
- `Bench` — honest boundary measurement (median of 9 rounds).

### Added — tooling

- `scripts/install-toolchain.sh` — local, idempotent Rust + JDK install (no
  admin).
- `scripts/verify.sh` — fmt → clippy → test → release build → Java self-test →
  bench; `--quick` skips the bench.

### Documented

- Sources, licence audit, architecture, decisions, assumptions, plan,
  parity, testing, results, known issues, handoff, changelog.

### Honest limitations

- No Minecraft adapter yet; nothing has run inside Minecraft (see
  `docs/KNOWN_ISSUES.md`).
- The upstream mod is All Rights Reserved; this is an independent
  reimplementation, not a source port.
- No assets (textures/sounds) yet.