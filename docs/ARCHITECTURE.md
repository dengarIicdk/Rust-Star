# ARCHITECTURE

Date: 2026-11-16 · Status: approved for the core, adapter design pending Stage 3.

## Principle

> **Everything that must behave identically on the server and the client lives
> in Rust, is pure, deterministic, and unit-tested. Everything that must touch
> Minecraft lives in Java. They meet at one narrow, batch-oriented JNI surface.**

The original mod does world edits, damage, cutscenes and rendering. Almost all
of that is Minecraft API work. The part that is *ours* — projectile integration,
impact-volume math, validation, budgeting — is engine-agnostic and belongs in
Rust, where it can be tested without a 300 MB Minecraft toolchain.

## Layers

```
+-------------------------------------------------------------+
| Minecraft client / server (Java)                             |
|   entity, world, networking, rendering, sounds               |
+----------------------------+--------------------------------+
| Java adapter (dev.starport) |  NativeBridge: original thin   |
|   blocks → plans, batching  |  JNI declarations + ABI check  |
+----------------------------+--------------------------------+
| starflight-jni (Rust cdylib)|  the ONLY unsafe code          |
|   panic boundary, registry  |  batch copy, no object cache   |
+-------------------------------------------------------------+
| starflight (Rust, no_std-ish)|  pure math: vec3, limits,     |
|   projectile, beam, plan     |  projectile, planning         |
+-------------------------------------------------------------+
```

### Library: `starflight`
- `#![forbid(unsafe_code)]` — the whole core is safe Rust.
- Deterministic: no randomness, no time, no I/O. Same inputs → same outputs
  across machines (float tie-breaks use an explicit epsilon: `projectile.rs`).
- Validates everything at the boundary (`limits.rs`): NaN/Inf/out-of-range are
  rejected, never clamped silently, so a malicious client cannot tune a strike
  to grief a server.
- Budgets are in the core: `MAX_BLOCKS_PER_TICK` (16 384) hard-caps one server
  tick; `MAX_BLOCKS_PER_STRIKE` (2 000 000) rejects a request that could
  otherwise OOM.

### CDYLIB: `starflight-jni`
- The **only** crate allowed `unsafe`. It exposes `Java_dev_starport_*` symbols.
- Every export wraps the body in `panic_boundary` (`catch_unwind`); a panic
  becomes a negative error code, never a JVM abort.
- All numbers are primitive (`jdouble`/`jlong`/`jint`); the only objects are
  `JString` (version) and `JIntArray` (batches).
- Plans live in a `Mutex<HashMap<i32, Plan>>` registry; the Java side owns the
  slot number and calls `dropPlan` in `finally`. `planCount()` lets tests assert
  no leak.
- No thread-local state, no `AttachCurrentThread`, no cached object references.

### Java adapter
- `NativeBridge` is pure declarations + loading + ABI handshake; it contains no
  logic. `EXPECTED_ABI` must equal `starflight::ABI_VERSION`, checked at load.
- The adapter (not yet built) will:
  - run `takeBatch` on the **server tick** thread, applying at most
    `MAX_BLOCKS_PER_TICK` positions per tick via normal `ServerWorld.setBlockState`,
  - re-use one `int[]` buffer across ticks,
  - call `buildPlan` once per strike and `dropPlan` in `finally`,
  - validate server-side that the requester has permission and the strike is
    cooled down (Stage 6).

## Why batching (the FFI contract)

Exchanging one position per JNI call costs ≈91 ns measured; a batched drain of
the same data costs ≈46 ns/position and ships in a reused array (≈1.9× faster
for the raw copy, and avoids per-block allocation on the Java side). Numbers in
`artifacts/logs/bench.log`.

> **Honesty note.** This measures the boundary, not world editing. Applying
> blocks in Minecraft is dominated by light engine and block-update cost, which
> no Rust could remove; the boundary design only keeps the FFI from being the
> bottleneck. No claim of "faster than the original mod" is made — the original
> was never measured.

## Threading rules (Minecraft adapter)

| Concern | Rule |
|---|---|
| Plan **build** | on the server thread, per strike, once |
| Plan **drain** | on the server tick thread, ≤ `MAX_BLOCKS_PER_TICK`/tick |
| Registry access | `Mutex` in Rust; JNI calls after releasing the lock only |
| Client rendering | never on the render thread; state flows client-side via packets at Stage 6 |
| Native library | loaded once, cached; no reload during play |

## Error handling

- Rust panics → caught at `panic_boundary` → logged to stderr → `ERR_*` code.
- Invalid input → `ERR_INVALID_*` code; the Java side logs and refuses the
  strike (never applies a partial beam from a bad request).
- ABI mismatch → `NativeBridge` refuses to load, with the expected vs actual
  versions in the message.
- The Java adapter must treat every `ERR_*` as "resource owned by Rust is in an
  unknown-but-safe state": drop the slot in `finally` regardless.

## What the adapter will NOT do in Rust

Rendering, shaders, cutscene camera, sounds, chat, networking, entities, block
lookups, chunk loading — all Minecraft API surface that stays in Java. The Rust
side computes *which blocks and where, how fast and how much damage*; the Java
side applies it.

## Rejected alternatives

See `docs/DECISIONS.md` for full reasoning:
- pure-Java port (loses the Rust requirement),
- all-in-Rust "own Minecraft server" (forbidden by the brief: must remain a mod),
- per-block JNI (measured slower, allocates),
- a shared-memory or socket bridge (JNI is the standard, loads no extra
  processes).