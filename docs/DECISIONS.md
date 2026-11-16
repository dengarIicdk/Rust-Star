# DECISIONS

Each entry: what was decided, why, what was rejected, and whether it is
reversible. Decisions marked **reversible** cost little to undo; **hard** ones
shape everything downstream.

---

## D1 — Implement behaviour independently; do not use upstream code · hard

**Decision.** Build a clean-room implementation from the public description.

**Why.** The mod and its library are All Rights Reserved (`docs/LICENSE_AUDIT.md`).
No repository is published. Copying the `.jar` or decompiling it would breach
the licence. The brief explicitly prefers an independent implementation when the
licence does not allow a port.

**Rejected.** Porting decompiled code (illegal here). Waiting for the user to
supply a `.jar` (still would not grant rights). Abandoning the project (the
mechanics are reimplementable).

**Consequence.** We will never claim full parity with the original. Numbers in
the upstream description are treated as intent, not specification.

---

## D2 — Target: Minecraft Java mod, Fabric, Minecraft 1.21.1 · hard

**Decision.** Minecraft Java Edition, **Fabric**, MC **1.21.1**, Windows x64.

**Why.**
- The brief requires staying a Minecraft mod.
- 1.21.1 is the most widely supported long-lived modern version, present in the
  upstream project's own file list, and the version most third-party mods target.
- Fabric's toolchain is the simplest and most permissively licensed; no
  Mojang-patched Forge distribution is needed.
- One loader, one version, one OS: the brief forbids spreading effort.

**Rejected.** Forge/NeoForge (more build complexity, no benefit for a new mod).
26.3 (very new, fewer resources, unstable mappings). Supporting the upstream
mod's whole matrix.

**Reversible?** The Rust core is version-agnostic, so only the adapter would
change. Cheap to retarget.

---

## D3 — Rust owns portable logic; Java keeps the Minecraft-adapter thin · hard

**Decision.** Rust = flight, collision, impact volumes, validation, budgeting.
Java = items, entities, blocks, rendering, networking.

**Why.** The brief requires a Rust-heavy implementation with a minimal Java
shim, and warns against per-block FFI. Putting world edits in Rust is impossible
without reimplementing Minecraft; putting the math in Java discards the point of
the exercise.

**Rejected.**
- *All-Rust*: would be a different game, forbidden.
- *All-Java*: no Rust, fails the task.
- *Rust owns rendering*: render thread + huge API surface; gains nothing.

---

## D4 — Batched JNI with a reused `int[]`, not per-block calls · reversible

**Decision.** One JNI call returns up to `MAX_BLOCKS_PER_TICK` positions as
`(x,y,z)` triplets.

**Why.** Measured: **46 ns/position batched vs 91 ns/position per call** for the
same data (`artifacts/logs/bench.log`). Per-block calls would also allocate a
Java object per block.

**Honesty note.** This is a boundary measurement only. It does not make world
edits faster; Minecraft's own pipeline dominates. No claim of superiority over
the original is made — it was never benchmarked.

**Rejected.** A JNI call per block (measured slower). A socket/shared-memory
side channel (extra process, deployment pain). Storing plans in Java (then
Rust's budgeting could not be authoritative).

---

## D5 — Safety limits in Rust, and reject rather than clamp untrusted input · hard

**Decision.** `limits.rs` holds hard caps (`HARD_MAX_RADIUS = 64`,
`MAX_BLOCKS_PER_TICK = 16384`, `MAX_BLOCKS_PER_STRIKE = 2e6`,
`MAX_DAMAGE = 1000`, etc.). Invalid input produces an error, not a silently
clamped value.

**Why.** The upstream UI advertises radii up to 1500; naively honouring that would
be a lag bomb and a griefing tool. A shared numeric policy prevents the client
and server from disagreeing. Rejecting (not clamping) means a bug surfaces
loudly instead of producing odd gameplay.

**Trade-off.** The 1500-block slider cannot be honoured per tick. It is kept in
`MAX_RADIUS_SLIDER` for documentation, and raising `HARD_MAX_RADIUS` is a code
change on purpose.

---

## D6 — No panic may cross the FFI boundary · hard

**Decision.** `panic_boundary` catches every unwind and returns `ERR_PANIC`.

**Why.** Unwinding into the JVM is undefined behaviour and typically aborts the
process. A mod that crashes a server because of one bad number is worse than one
that refuses a strike.

**Rejected.** `panic = "abort"` only (it still kills the server). Letting
`catch_unwind` be skipped in release (the profile keeps unwind semantics for
the JNI crate).

---

## D7 — Toolchains installed inside the project · reversible

**Decision.** Rust and JDK live in `./tools`, git-ignored.

**Why.** No administrator rights, no wish to modify the user's machine, and
`choco`/`winget` system installs would be an invasive, hard-to-undo change.
A short README explains reinstallation.

**Rejected.** winget/choco machine-wide installs. Assuming the user has them.

---

## D8 — No Minecraft adapter until a toolchain is proven · reversible

**Decision.** Do not write Fabric-specific Java code that has never been
compiled against the real mappings.

**Why.** Writing adapter code blind produces code that *looks* finished and
compiles nowhere. The brief forbids presenting unverified work as ready.

**Rejected.** Scaffolding a Fabric project that cannot build. Faking a JAR.

---

## D9 — Historical-themed skin: neutral default, separate opt-in asset · reversible

**Decision.** The neutral texture is the default. Any historical-themed variant
is a separate, user-supplied or user-chosen resource pack that this repository
does not ship, with no slogans or symbols of hate.

**Why.** Such imagery is illegal or restricted in several jurisdictions
(Germany §86a StGB among others), and it violates most platforms' rules. Keeping
it opt-in, shipped separately and clearly labelled limits harm while satisfying
the user's stated preference.

**Status.** Awaiting the user's concrete visual reference, as the brief requires.
Nothing has been drawn.
