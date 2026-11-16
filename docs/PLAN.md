# PLAN

Stages and exit criteria. A stage is done only when its criterion was met and
recorded. Legend: ✅ met · ⚠️ partial · ⛔ blocked · ⬜ not started

## Stage 0 — Sources, licence, audit ✅
- Primary source (CurseForge) read: author **rimuru_dev**, **All Rights
  Reserved**, dependency Rimulib also ARR, no repository/contacts.
- `docs/SOURCES.md` + `docs/LICENSE_AUDIT.md` written.
- Decision: clean-room implementation; no `.jar` needed or requested.

## Stage 1 — Architecture ✅
- Rust owns portable logic; a thin Java adapter owns Minecraft integration;
  batched JNI in between. `docs/ARCHITECTURE.md`, `docs/DECISIONS.md`.

## Stage 2 — Minimal Java ↔ Rust bridge ✅
- Rust core (38 tests), JNI crate (panic boundary, plan registry), Java
  `NativeBridge`/`SelfTest` (30/30), benchmark (46 vs 91 ns/position).
- `scripts/verify.sh` exits 0; server-itest proves the real `.dll` loads.

## Stage 3 — One end-to-end gameplay scenario ✅ (server-verified)
- Fabric 1.21.1 project → JAR → dedicated server.
- Obtained (recipe) → launch (item/command) → flight (entity) → impact
  (block mutation, verified by world read-back: solid→air).
- **Criterion met** — the only unverified part is human input, which needs a
  person at the keyboard (Task T27, honest limitation).

## Stage 4 — Remaining original features ⚠️
- The original's other skills (Gungnir, Seven Stars, Tesseract, Gravitia) and
  cutscenes are prose descriptions of an ARR mod. Rather than fabricate lookalikes
  under borrowed names, this project implements the *core strike mechanic* fully
  and a distinct custom rocket. Decided in `docs/DECISIONS.md`.
- To implement the full set later: the planner already supports multi-beam
  strikes (one plan per beam), so a constellation is a loop over `queueBeam`.

## Stage 5 — Custom rocket ✅ (mechanics) / ⚠️ (art)
- `starflight:orbital_rocket`: own ID, tuneable radius/damage/speed/lifetime,
  recipe, neutral original textures.
- Historical-themed optional skin: **not shipped**, pending the user's visual
  reference; neutral default; distribution caveat in README + DECISIONS.

## Stage 6 — Persistence, multiplayer, hardening ✅
- Strike requests persist and resume; corrupt saves rejected on load.
- Per-player server-side rate limit; active-strike cap; world-edit kill switch.
- Confirmed by six in-server checks (registry/flight/persistence/rate/config/verify).

## Stage 7 — Optimisation, packaging, install guide ✅
- Single installable JAR with the native library bundled; no special
  `java.library.path`. Install guide in `README.md` / `RESULTS.md`.
- Boundary is batched (measured); no claim of "faster than the original" — the
  original was never benchmarked.

## Outstanding (next session)
| Task | Why it remains |
|---|---|
| Human gameplay check (T27) | needs a human at keyboard + mouse |
| Particles + sound for impact (T16) | polish, not required for correctness |
| Two-client multiplayer (T23) | needs two real clients |
| Historical skin (T17) | blocked on the user's visual reference |
