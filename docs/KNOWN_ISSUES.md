# KNOWN ISSUES

Ordered by severity. Nothing here is hidden behind a "TODO" that pretends to be
finished functionality.

## 1. No Minecraft adapter exists — **blocker for the gameplay goal**

**Symptom.** There is no `fabric.mod.json`, no item, no entity, no JAR.
**Why.** Building a Fabric mod requires Gradle to download Minecraft, its mappings
and the Fabric API. That toolchain was not obtained or proven in this
environment, so adapter code would be uncompiled and unverifiable.
**Impact.** The project cannot yet be loaded into Minecraft at all.
**Workaround.** None. Stage 3 must be done where a Fabric toolchain is available.
**Tracked as.** `TASKS.md` T9/T10.

## 2. No in-game verification of any kind

**Symptom.** Nothing reported here was observed inside Minecraft.
**Why.** Same as #1.
**Consequence.** The phrase "works" in this repo always means "passes its
automated tests", never "works in game".

## 3. The original mod cannot be matched exactly

**Symptom.** Numeric parity (200-block radius, 1500 slider, 400-block crater) is
not attempted.
**Why.** All Rights Reserved + description-only source. Those figures are prose.
**Impact.** This is a reimplementation, not a drop-in replacement; numbers are
our own and configurable.

## 4. Hard radius cap below the advertised slider

**Symptom.** `HARD_MAX_RADIUS = 64`, while the upstream UI offers up to 1500.
**Why.** Deliberate: a 1500-block-radius vertical cylinder is ~1e9 blocks and
would be a trivial server-killer. See `DECISIONS.md` D5.
**Impact.** Requests above 64 are **rejected**, not clamped.
**Remedy.** Raise the constant deliberately if a server owner accepts the cost.

## 5. `MAX_BLOCKS_PER_STRIKE = 2 000 000` may be too low for very tall worlds

**Symptom.** A radius-64 beam over a full 384-block world is ~3.9 M blocks and is
therefore rejected by the planner.
**Why.** Chosen as a memory/DoS ceiling.
**Remedy.** Constant in `limits.rs`; raising it also raises worst-case memory
(128 bytes per position ≈ 500 MB at 4 M).

## 6. Only Windows x64 was built and tested

**Symptom.** Only `starflight_jni.dll` exists.
**Why.** The available machine is Windows. The Rust core is portable and tests
would pass elsewhere, but that is untested here.
**Remedy.** `cargo build --release` on the target OS; the `NativeBridge` loader
already looks for `linux-x64`/`mac-arm64`/etc. subdirectories.

## 7. Benchmark numbers are machine-specific

**Symptom.** 46 vs 91 ns/position comes from one machine/JVM.
**Why.** Single-run timing; CPU frequency scaling and JIT state vary.
**Impact.** Use the ratio, not the absolutes; the design choice does not hinge on
a factor of 2, and none of this predicts in-game block-edit speed.

## 8. No assets (textures, models, sounds)

**Symptom.** `assets/` does not exist.
**Why.** ARR forbids taking upstream assets; writing real ones was out of scope
for the core stage, and shipping placeholders as final art is forbidden by the
brief.
**Impact.** Even with an adapter, the mod would be untextured.

## 9. Probe `Vec<char>` encoding for non-ASCII

**Symptom.** `env.new_string` is only used for the version string.
**Why.** No user-facing non-ASCII strings cross JNI yet.
**Remedy.** Revisit when item names/localisation cross the boundary.

## 10. Git identity is project-local

**Symptom.** Commits use a local `user.name`/`user.email` because none was
configured.
**Why.** The brief forbids changing global settings.
**Impact.** Commits exist locally with a generic identity; rewrite when the real
identity is known.