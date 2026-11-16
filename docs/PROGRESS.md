# PROGRESS

Stage-by-stage. Companion to `PLAN.md` (exit criteria) and `WORKLOG.md`
(chronological actions).

## 2026-11-16 session

### Stage 0 — sources & licence ✅
- CurseForge: author `rimuru_dev`, licence **All Rights Reserved**, dependency
  **Rimulib** (also ARR). Versions 26.3 / 1.21.11 / 1.21.1 / 1.20.1.
- No repository, no source link, no extra files.
- Conclusion: independent implementation only. No `.jar` obtained or opened.

### Stage 1 — architecture ✅
- Portable logic → Rust (`starflight`), integration → Java adapter, batched JNI.
- `docs/ARCHITECTURE.md`, `docs/DECISIONS.md` (D1–D9).

### Stage 2 — minimal Java ↔ Rust bridge ✅
- Local Rust 1.100.0 + Temurin JDK 21 in `tools/` (no admin rights).
- Core: `vec3, limits, projectile, beam, plan` — **38 tests**.
- JNI crate: panic boundary, plan registry, batch copy.
- `SelfTest` against the real `.dll`: **30/30**, 16 000 concurrent calls, no
  panic, no leak.

### Stage 3 — end-to-end gameplay ✅ (server-verified)
- Fabric 1.21.1 project builds: `starflight-0.1.0.jar`.
- Item → launch → flight → impact → block destruction, all verified on a **real
  dedicated server** by reading the world back:
  `solidBefore=true → airNow=true`.
- Two real API mismatches found and fixed against the actual 1.21.1 mappings
  (single-parameter `EntityRenderer`, `TooltipType` package, `BlockHitResult`
  from `World.raycast`, `PersistentState.Type`).

### Stage 4 — remaining original features ⚠️ partial
- Core mechanics for a beam strike are implemented and tested.
- Upstream features (Gungnir/Seven Stars/Tesseract/Gravitia, cutscenes) are
  described only in marketing prose and are All Rights Reserved; they are
  catalogued in `FEATURE_PARITY.md` and intentionally **not** reimplemented as
  lookalikes.

### Stage 5 — custom rocket ✅ (mechanics) / ⚠️ (art)
- `starflight:orbital_rocket` with its own namespace and ID.
- Configurable radius / damage / speed / lifetime, validated by the core.
- Crafting recipe for survival obtainability.
- Original neutral textures (item, entity, mod icon), authored here.
- Art is simple and presented as such, not as finished artwork.

### Stage 6 — persistence, multiplayer, hardening ✅
- `Persistence`: resumable strike requests survive a restart; corrupt entries
  are dropped on load (verified: `rejectsCorrupt=true`).
- `RateLimiter`: 3 launches / 30 s per player, server-side, independent of the
  bypassable client cooldown (verified with a fake clock).
- `maxActiveStrikes` cap and `allowWorldEdit` kill switch.
- 16 000-call concurrency and no-leak checks.

### Stage 7 — packaging & install ✅
- Single installable JAR with the native library bundled; no `java.library.path`
  configuration needed.
- Install instructions in `README.md` and `RESULTS.md`.

## Client verification

`gradle runClient` launches Minecraft 1.21.1 with the mod:
```
[Render thread/INFO] (starflight) Native core 0.1.0 (ABI 1)
[Render thread/INFO] (starflight) Starflight ready (native core 0.1.0)
```
0 missing textures/models; sound engine and all atlases initialised.

## Honest summary table

| Item | Written | Compiled | Automated tests | Observed in game |
|---|---|---|---|---|
| Rust core | ✅ | ✅ | ✅ 38 | ✅ (via server) |
| JNI bridge | ✅ | ✅ | ✅ 30 | ✅ |
| Mod items/entities | ✅ | ✅ | ✅ registry check | ⚠️ server only |
| Block strike | ✅ | ✅ | ✅ world read-back | ✅ |
| Rocket flight | ✅ | ✅ | ✅ spawn+velocity | ⚠️ no player input |
| Persistence | ✅ | ✅ | ✅ NBT round-trip | ✅ |
| Rate limiting | ✅ | ✅ | ✅ fake clock | ✅ |
| Textures | ✅ | ✅ | no missing-texture errors | ⚠️ not eyeballed |
| Client launch | ✅ | ✅ | log evidence | ✅ |
