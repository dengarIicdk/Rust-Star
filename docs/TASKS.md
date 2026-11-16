# TASKS

Statuses: `TODO` · `IN_PROGRESS` · `DONE` · `BLOCKED`.
`DONE` requires the acceptance criterion to be met and recorded.

## Stage 2 — bridge

| # | Task | Status | Acceptance | Evidence |
|---|---|---|---|---|
| T1 | Rust toolchain locally | DONE | `cargo --version` | 1.100.0 |
| T2 | JDK 21 locally | DONE | `javac --version` | 21.0.13 |
| T3 | `starflight` core | DONE | tests green | 38 passed |
| T4 | `starflight-jni` cdylib | DONE | build + tests | `.dll` produced |
| T5 | `NativeBridge` + `SelfTest` | DONE | all-pass with real `.dll` | 30/30 |
| T6 | Boundary benchmark | DONE | numbers recorded | 46 vs 91 ns |
| T7 | `scripts/verify.sh` | DONE | exits 0 | VERIFY: PASS |

## Stage 3 — end-to-end

| # | Task | Status | Acceptance | Evidence |
|---|---|---|---|---|
| T8 | Fabric dev environment compiles | DONE | `gradle build` | JAR built |
| T9 | Items/entities registered | DONE | registry contains IDs | REGISTRY PASS |
| T10 | Rocket entity spawns and flies | DONE | spawns with sane velocity | FLIGHT PASS |
| T11 | Strike mutates blocks | DONE | block solid→air | VERIFY PASS |

## Stage 5 — custom rocket

| # | Task | Status | Acceptance | Evidence |
|---|---|---|---|---|
| T12 | Own item ID | DONE | `starflight:orbital_rocket` | REGISTRY PASS |
| T13 | Own textures/model | DONE | files exist, 0 missing-texture errors | client log |
| T14 | Obtainable in survival | DONE | crafting recipe present | recipe JSON |
| T15 | Tunable speed/lifetime/damage | DONE | config validated by core | CONFIG PASS |
| T16 | Trail / sound / impact VFX | TODO | audible+visible effect | — |
| T17 | Historical-themed optional skin | BLOCKED | needs the user's reference | — |

## Stage 6 — persistence / multiplayer

| # | Task | Status | Acceptance | Evidence |
|---|---|---|---|---|
| T18 | Strike survives restart | DONE | NBT round-trip + resume | PERSISTENCE PASS |
| T19 | Corrupt save rejected | DONE | invalid entry dropped | `rejectsCorrupt=true` |
| T20 | Server-side rate limit | DONE | 4th request in window blocked | RATELIMIT PASS |
| T21 | Active-strike cap | DONE | queue refuses above cap | CONFIG PASS |
| T22 | World-edit kill switch | DONE | config disables strikes | CONFIG PASS |
| T23 | Two real clients play together | TODO | manual multiplayer test | — |

## Stage 7 — packaging

| # | Task | Status | Acceptance | Evidence |
|---|---|---|---|---|
| T24 | Installable JAR with natives | DONE | jar contains `natives/win-x64` | `jar tf` |
| T25 | Install instructions | DONE | documented | README / RESULTS |
| T26 | Client launches with mod | DONE | 0 missing textures | runClient log |
| T27 | Human gameplay check | TODO | a person plays it | — |

## Recommended next

1. **T27** — a person launches a world, crafts the rocket, fires it, watches the
   strike. This is the only thing automation cannot cover.
2. **T16** — add particles and a sound so the impact reads clearly.
3. **T23** — two-client multiplayer pass.
