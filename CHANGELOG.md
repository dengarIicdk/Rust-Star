# CHANGELOG

All notable changes. Format follows [Keep a Changelog](https://keepachangelog.com/).

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