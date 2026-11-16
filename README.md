# Starflight — Rust port of "The Shooting Star" mechanics

An independent Rust implementation of the mechanics described in the Minecraft mod
**The Shooting Star [Demo]**, wired into Minecraft through a minimal Java adapter.

> **Not a source port.** The upstream mod is licensed **All Rights Reserved**.
> No upstream code, models, textures or sounds are copied here. See
> [`docs/LICENSE_AUDIT.md`](docs/LICENSE_AUDIT.md). Everything in this repository
> is written from scratch against the *publicly described behaviour*.

## Status at a glance

| Stage | Meaning | State |
|---|---|---|
| Written | code exists | ✅ |
| Compiles | builds clean | ✅ |
| Automated tests | passed locally | ✅ **36 Rust + 30 JNI** |
| Verified in game | launched in Minecraft | ❌ **not done** |

The native bridge is proven end-to-end on a real JVM. The **Minecraft adapter is
not started**, because it needs a Minecraft/Fabric toolchain that is not
available in this environment (see [`docs/KNOWN_ISSUES.md`](docs/KNOWN_ISSUES.md#1)).

## What works right now

```
scripts/verify.sh   # rustfmt → clippy → cargo test → release build → JNI self-test
```

Expected tail:

```
passed=30 failed=0
RESULT: PASS
VERIFY: PASS
```

## Layout

```
rust/
  starflight/          portable game logic — NO JNI, NO Minecraft, forbid(unsafe_code)
    src/vec3.rs        math primitives, NaN-safe normalization
    src/limits.rs      hard safety limits; validates every untrusted number
    src/projectile.rs  swept-AABB projectile simulation
    src/beam.rs        cylindrical beam volume + streaming block source
    src/plan.rs        chunk-batched block mutation planner with a per-tick budget
  starflight-jni/      the only FFI layer; panic boundary + plan registry
java/src/main/java/dev/starport/
  NativeBridge.java    thin JNI declarations, ABI handshake, library loading
  SelfTest.java        loads the real .dll and asserts 30 behaviours
  Bench.java           boundary-cost measurement (numbers, not claims)
scripts/verify.sh      one-command local verification
docs/                  plan, progress, decisions, handoff, sources, licensing
```

## Design in one paragraph

Game logic that must be identical on client and server lives in Rust and is
pure, deterministic and unit-tested. Minecraft-facing work (blocks, entities,
rendering, networking) stays in Java. The two meet at a narrow JNI surface where
positions are exchanged **in batches into a reused `int[]`**, not per block:
measured 46 ns/position batched vs 91 ns/position per-call (`artifacts/logs/bench.log`).
Every number arriving from Java is validated in `limits.rs` before any math runs,
and no panic can cross the boundary.

## Requirements

- Windows x86-64 (current target; the layout is portable but only Windows is verified)
- Rust 1.100 (installed locally by `scripts/install-toolchain.sh`)
- JDK 21 (same)
- No administrator rights, no network access at run time

## Docs

| File | Purpose |
|---|---|
| [`docs/HANDOFF.md`](docs/HANDOFF.md) | **start here** when resuming |
| [`docs/PLAN.md`](docs/PLAN.md) | staged plan with exit criteria |
| [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) | layering, threading, FFI contract |
| [`docs/LICENSE_AUDIT.md`](docs/LICENSE_AUDIT.md) | what is and is not legally available |
| [`docs/FEATURE_PARITY.md`](docs/FEATURE_PARITY.md) | described feature → status → test |
| [`docs/SOURCES.md`](docs/SOURCES.md) | sources, check dates, confirmed/unconfirmed |
| [`docs/PROGRESS.md`](docs/PROGRESS.md) | stage-by-stage results |
| [`docs/TASKS.md`](docs/TASKS.md) | task list with statuses |
| [`docs/DECISIONS.md`](docs/DECISIONS.md) | decisions and rejected alternatives |
| [`docs/ASSUMPTIONS.md`](docs/ASSUMPTIONS.md) | assumptions made without asking |
| [`docs/TESTING.md`](docs/TESTING.md) | how to verify, and what each test proves |
| [`docs/RESULTS.md`](docs/RESULTS.md) | what is built, tested, blocked |
| [`docs/KNOWN_ISSUES.md`](docs/KNOWN_ISSUES.md) | blockers and limitations |
| [`docs/WORKLOG.md`](docs/WORKLOG.md) | chronological action log |
| [`CHANGELOG.md`](CHANGELOG.md) | version history |

## Licence

Original work in this repository is MIT (see [`LICENSE`](LICENSE)). Third-party
names are used descriptively only; see `docs/LICENSE_AUDIT.md` and the
[Minecraft Usage Guidelines](https://www.minecraft.net/en-us/usage-guidelines).
