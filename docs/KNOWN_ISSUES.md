# KNOWN ISSUES

Ordered by severity. Nothing hidden behind a "TODO" that pretends to be done.

## 1. No human gameplay check — the honest limit of automation

**Symptom.** Everything important is verified server-side, but nobody has fired a
rocket by hand in a real client and watched the impact.
**Why.** That demands a human at the keyboard; it cannot be automated. The
client launches and loads assets cleanly, and a server-verifiable block mutation
passes, but "it feels good to play" is unverified.
**Impact.** Possible undiscovered feel/latency/UX issues.
**Tracked as.** T27.

## 2. Historical-themed skin not implemented

**Symptom.** Only the neutral default textures exist.
**Why.** The brief requires a concrete visual reference before drawing; the user
has not given one, and such imagery is legally restricted in several
jurisdictions and on most platforms.
**Remedy.** When the user supplies a reference, add a *separate* opt-in texture
pack. Repository ships neutral only.

## 3. No impact VFX or sound yet

**Symptom.** The strike works (blocks are removed) but is silent and has no
particles.
**Why.** Polish was prioritised below correctness. Requires original audio (ARR
forbids copying the original's).
**Impact.** Aesthetic only.

## 4. Only Windows x64 built and tested

**Symptom.** Only `starflight_jni.dll` is produced.
**Why.** The available machine is Windows. The Rust core is portable; untested
elsewhere.
**Remedy.** `cargo build --release` on the target OS; the loader already looks
for `linux-x64` / `mac-arm64` subdirectories.

## 5. Hard radius cap is below the upstream slider

**Symptom.** `HARD_MAX_RADIUS = 64` while the upstream UI advertised up to 1500.
**Why.** Deliberate (D5): a 1500-radius cylinder is ~1e9 blocks, an instant
server-killer. Requests above 64 are rejected, not clamped.
**Remedy.** Raise the constant deliberately; `MAX_BLOCKS_PER_STRIKE = 2e6` also
caps the worst case (~500 MB for a single plan).

## 6. Benchmark numbers are machine-specific

**Symptom.** 46 vs 91 ns/position from one machine.
**Why.** Single-CPU JIT/clock variation.
**Impact.** Use the ratio, not the absolutes; this does not predict in-game
block-edit speed (Minecraft's own pipeline dominates).

## 7. Rocket art is simple

**Symptom.** The rocket renders as a basic silhouette/billboard, not a detailed
model.
**Why.** Original art takes time and the visual check needs the human pass of
#1. Presented as a placeholder, not as finished art.
**Remedy.** After #1 confirms handling, commission/replace the model and
textures. Keep them original (licence).

## 8. No non-ASCII strings cross JNI yet

**Symptom.** `env.new_string` is used only for the version string.
**Why.** No user-facing non-ASCII text crosses the boundary.
**Remedy.** Revisit when item names/localisation do.

## 9. Git identity is project-local

**Symptom.** Commits use a local `user.name`/`user.email`.
**Why.** Global config must not be touched. Commits exist locally with a generic
identity; rewrite when the real identity is known.
