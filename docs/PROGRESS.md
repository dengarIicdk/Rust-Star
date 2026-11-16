# PROGRESS

Walkthrough of what actually happened, stage by stage. Companion to
`PLAN.md` (criteria) and `WORKLOG.md` (chronological actions).

## 2026-11-16 session

### Stage 0 — sources & licence ✅
- CurseForge page read: author `rimuru_dev`, licence **All Rights Reserved**,
  versions 26.3 / 1.21.11 / 1.21.1 / 1.20.1, loaders Fabric/Forge/NeoForge.
- Dependency: **Rimulib** (also ARR). No repository, no source link, no extra
  files.
- Conclusion: independent implementation; no `.jar` needed or requested.

### Stage 1 — architecture ✅
- Portable logic → Rust (`starflight`), integration → Java adapter, batch JNI.
- Documents: `ARCHITECTURE.md`, `DECISIONS.md`.

### Stage 2 — minimal Java ↔ Rust bridge ✅ **fully working**
- Local Rust toolchain (1.100.0, gnu) + Temurin JDK 21 installed in `tools/`.
- Rust core: `vec3`, `limits`, `projectile`, `beam`, `plan`. 36 unit tests,
  clippy `-D warnings` clean, rustfmt clean.
- JNI crate: panic boundary, plan registry, batch copy. 7 unit tests.
- `starflight_jni.dll` builds; Java `SelfTest` loads it: **30/30 pass**,
  including 16 000 concurrent calls with zero panics and zero plan leaks.
- Benchmark (median of 9): batched 46 ns/position vs per-call 91 ns/position.
- `scripts/verify.sh` green end-to-end.

### Stage 3 — end-to-end gameplay ⛔ blocked
Blocked on the absence of a Minecraft/Fabric runtime. Everything
engine-independent (flight, collision, impact volume, tick budget) exists and
is tested.

### Stages 4–7 — not started
Blocked by (or following from) Stage 3. See `PLAN.md`.

## What is genuinely done vs what is written only

| Item | Written | Compiled | Tested | Truth |
|---|---|---|---|---|
| Rust core (`starflight`) | ✅ | ✅ | ✅ 36 tests | run twice, all green |
| JNI crate | ✅ | ✅ | ✅ 7 tests | run, green |
| Native `.dll` | ✅ | ✅ | ✅ loaded by JVM | SelfTest green |
| Java `SelfTest` | ✅ | ✅ | ✅ | 30/30 |
| Java `Bench` | ✅ | ✅ | ✅ | numbers recorded |
| Minecraft adapter | ⬜ | ⬜ | ⬜ | **not attempted** (no toolchain) |
| Textures / sounds | ⬜ | — | — | none exist |
| In-game verification | ⬜ | — | — | **not done** |