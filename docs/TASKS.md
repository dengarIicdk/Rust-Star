# TASKS

Statuses: `TODO` · `IN_PROGRESS` · `DONE` · `BLOCKED`.
A task is `DONE` only when its acceptance criterion is met and recorded.

## Stage 2 (bridge) — all DONE

| # | Task | Status | Acceptance | Evidence |
|---|---|---|---|---|
| T1 | Install Rust toolchain locally | DONE | `cargo --version` works | `rustc 1.100.0` |
| T2 | Install JDK 21 locally | DONE | `javac --version` works | Temurin 21.0.13 |
| T3 | Write `starflight` core | DONE | unit tests green | 36 passed |
| T4 | Write `starflight-jni` cdylib | DONE | unit + cargo build | 7 passed, `.dll` built |
| T5 | Java `NativeBridge` + `SelfTest` | DONE | SelfTest all-pass with real `.dll` | 30/30, exit 0 |
| T6 | Benchmark boundary | DONE | numbers recorded | `bench.log` |
| T7 | `scripts/verify.sh` | DONE | exits 0 | ran, `VERIFY: PASS` |
| T8 | Docs (sources, licence, plan, decisions… ) | DONE | files present, no contradictions | see `docs/` |

## Stage 3 (one end-to-end scenario)

| # | Task | Status | Acceptance |
|---|---|---|---|
| T9 | Fabric dev environment that compiles | BLOCKED | no Minecraft toolchain available; need a JDK+Fabric+Gradle download or user-provided setup |
| T10 | Item obtain → launch → fly → impact | BLOCKED | in-game observation |
| T11 | Server-authoritative impact application | BLOCKED | depends on T10 |

## Stages 4–7

| # | Task | Status | Acceptance |
|---|---|---|---|
| T12 | Remaining upstream features | BLOCKED | depends on T10 |
| T13 | Custom rocket (item, model, texture) | BLOCKED | depends on T10; textures need design |
| T14 | Persistence of rocket state | TODO | survives world reload (Stage 6) |
| T15 | Multiplayer authority, rate limits | TODO | server rejects spoofed strikes |
| T16 | Packaging + install guide | TODO | installable `.jar` |

## Everywhere

- [ ] Keep `HANDOFF.md` current before ending a session.
- [ ] Record any new texture/sound origin in `LICENSE_AUDIT.md`.