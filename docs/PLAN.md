# PLAN

Stages follow the project brief. Each has an **exit criterion**: the observable
thing that must be true before moving on. A stage is not "done" because code was
written; it is done when its criterion is met and the result is recorded.

Legend: ✅ met · ⚠️ partially met · ⛔ blocked · ⬜ not started

---

## Stage 0 — Sources, licence, audit ⚠️

**Goal:** know what may legally be built and what the original actually does.

| Task | Result |
|---|---|
| Verify authorship on the primary source | ✅ `rimuru_dev` on CurseForge |
| Determine licence | ✅ **All Rights Reserved** → no code reuse |
| Find repository / contacts | ✅ none published |
| Gather feature description | ✅ CurseForge description (behavioural only) |
| Obtain/decompile the `.jar` | ⛔ **not done and not needed** — licence forbids reuse |
| Provide the `.jar` | User has not supplied one; not requested further because it would not change the plan |

**Exit criterion:** `docs/SOURCES.md` and `docs/LICENSE_AUDIT.md` exist and state
what is confirmed, assumed, and unavailable. ✅

---

## Stage 1 — Architecture and effort estimate ✅

**Goal:** a defensible split between Rust and Java, and an honest size estimate.

Decision: portable logic in Rust, Minecraft integration in a thin Java adapter,
batched JNI boundary. Rationale and alternatives in `docs/DECISIONS.md` and
`docs/ARCHITECTURE.md`.

**Exit criterion:** architecture document names every layer, the FFI contract,
error handling, threading rules and batching strategy. ✅

---

## Stage 2 — Minimal working Java ↔ Rust bridge ✅

**Goal:** prove the bridge on this machine before building anything on it.

| Check | Result |
|---|---|
| Rust toolchain available | ✅ installed locally in `tools/` |
| JDK available | ✅ Temurin 21 in `tools/` |
| Rust unit tests | ✅ 36 passed |
| `cargo clippy -D warnings` | ✅ clean |
| `cargo fmt --check` | ✅ clean |
| Native library builds | ✅ `starflight_jni.dll` |
| Java loads it and calls it | ✅ 30/30 self-test checks |
| NaN/Inf/oversized input rejected | ✅ tests cover each |
| Panic cannot cross the boundary | ✅ 16 000 concurrent calls, no abort |
| Boundary cost measured | ✅ ~9 ns bare call, 46 vs 91 ns/position |
| No plan leak after use | ✅ asserted in self-test |

**Exit criterion:** `scripts/verify.sh` exits 0 and the self-test loads a **real**
`.dll` rather than a mock. ✅

---

## Stage 3 — One end-to-end gameplay scenario ⛔

**Goal:** obtain item → launch → fly → impact, inside Minecraft.

Requires a Minecraft + Fabric toolchain (Gradle, mappings, Fabric API) and a game
client to observe the result. Neither exists in this environment and both need a
long download that was not proven possible here.

Delivered instead: every engine-independent part of that scenario is implemented
and tested in isolation — projectile flight with swept collision
(`projectile.rs`), impact volume generation (`beam.rs`), and tick-budgeted
application (`plan.rs`).

**Exit criterion:** a player launches a projectile in game and a block is
destroyed. ⛔ not met.

---

## Stage 4 — Remaining original features ⬜

Blocked on Stage 3 for the integration half. Note also that the upstream
features are described only at a marketing level (see `FEATURE_PARITY.md`); the
numeric details would have to be invented, and are marked as such.

---

## Stage 5 — Custom rocket ⬜

Blocked on Stage 3. The mechanics core (flight, collision, impact, tuning
validation) already exists and is reusable.

---

## Stage 6 — Persistence, multiplayer, hardening ⚠️

Hardening that does not need Minecraft is already in place:
- untrusted-input validation (`limits.rs`)
- per-tick and per-strike budgets (`plan.rs`)
- panic containment (`panic_boundary`)
- thread-safe plan registry with explicit lifetime

Not done: world state persistence, server-side authority checks against a real
`ServerPlayerEntity`, rate limiting per player.

---

## Stage 7 — Optimisation, packaging, install guide ⬜

Packaging requires a JAR, which requires Stage 3.

---

## Sequencing rule

The brief says: **do not start the full port until the minimal JNI bridge is
proven.** That bridge is proven (Stage 2 ✅). The remaining blocker is not
architectural but environmental — there is no Minecraft runtime here, and the
adapter cannot be honestly reported as working without one.
