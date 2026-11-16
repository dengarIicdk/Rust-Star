# WORKLOG

Actions, decisions and observed results only. No internal reasoning.

## 2026-11-16

**Read the brief.** `Z:\WIBECODING\MyProjects\README.md`. Target folder
`Z:\WIBECODING\MyProjects\Rust-Star` was empty.

**Environment check.**
- `rustc`, `cargo`, `java`, `javac`, `gradle`: **absent**
- `git 2.56.1`, `curl`, `unzip`, `winget 1.29`: present
- OS: Windows 10.0.26200, MSYS/Git Bash, x86_64

**Sources.** Read the CurseForge project page and file listing.
- Author **rimuru_dev**, licence **All Rights Reserved**, dependency **Rimulib**
  (also ARR), versions 26.3 / 1.21.11 / 1.21.1 / 1.20.1, Fabric/Forge/NeoForge.
- No repository, no contacts, no extra files published.
→ Wrote `docs/SOURCES.md`, `docs/LICENSE_AUDIT.md`. Decision: clean-room only.

**Toolchain install (local).**
- Downloaded `rustup-init.exe`; installed minimal, host
  `x86_64-pc-windows-gnu`, into `tools/{rustup,cargo}`. Exit 0. `rustc 1.100.0`.
- `rustup default stable` → error "no default configured" → fixed by setting it.
- Downloaded Temurin 21.0.13+9 **zip** (the MSI needs elevation, so skipped).
- `java -version` → `21.0.13 LTS`. Added `rustfmt`, `clippy`.

**Created the Rust workspace.** `rust/Cargo.toml` (workspace, release profile
with `lto`, `panic` left to unwind for the JNI crate), `starflight` and
`starflight-jni`.

**First `cargo test` failed:**
- `error[E0599]: no method named get_int_array_elements` — not in `jni` 0.21.
  Read the crate source, found `set_int_array_region`. Switched to that.
- 3 beam/plan tests failed: streamed 85 positions vs analytic 80.
  Root cause: the iterator started on the bounding-box corner, which can lie
  outside the circle, emitting spurious columns. Fixed by advancing to the
  first in-radius column in `new()`.
- Then 152 vs 160 in the plan test: the test built a batch **before** creating
  its `seen` set, so the first batch was never counted. Test bug, fixed.
- `clippy -D warnings`: `float_cmp` on tie-breaking (`f == bf`). Replaced with an
  explicit 1e-9 epsilon + id tie-break — this also makes results reproducible
  across machines. `assertions_on_constants` on two const checks → moved into
  `const {}` blocks.

**JNI symbol mismatch.** Wrote Java natives as `nativeVersion()` etc., which
would need `RegisterNatives`. Renamed both sides to match mangled names exactly
(`versionNative`, `vtNative`, …) — simplest, no registration step.

**First SelfTest run:** 30/30 PASS, including 8×2000 concurrent calls.

**Benchmark iterations.**
1. First version: batched drain measured 0 ms — the JIT eliminated an
   effect-free loop. Added a checksum data dependency.
2. Then 0 positions — `time()` re-runs the body and the drain is destructive.
   Moved `buildPlan` inside the timed body.
3. Then "one-position call" was 58 µs/position because it rebuilt the plan per
   call. Rewrote to build once and drain one position per call.
4. Final, honest numbers: **9 ns** bare call, **46 ns/position** batched,
   **91 ns/position** per call.

**`scripts/verify.sh`**: full run green, `VERIFY: PASS`. Logs in `artifacts/logs/`.

**Docs.** Wrote `README`, `PLAN`, `ARCHITECTURE`, `LICENSE_AUDIT`,
`FEATURE_PARITY`, `DECISIONS`, `ASSUMPTIONS`, `PROGRESS`, `TASKS`, `TESTING`,
`RESULTS`, `KNOWN_ISSUES`, `LICENSE`.

**Git.** `git init` in `Rust-Star`. No global identity was configured, so a
project-local `user.name`/`user.email` was set (global config untouched).
`.gitignore` excludes `tools/`, `target/`, `java/out/`, `artifacts/logs/*.log`.
