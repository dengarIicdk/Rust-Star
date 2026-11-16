# TESTING

## One command

```bash
./scripts/verify.sh
```

Runs, in order, and fails on the first problem:

1. `cargo fmt --all -- --check`
2. `cargo clippy --workspace --all-targets -- -D warnings`
3. `cargo test --workspace`
4. `cargo build --release` (produces the native library)
5. `javac -Xlint:all` + `java dev.starport.SelfTest` (loads the **real** library)
6. `java dev.starport.Bench` (informational; `--quick` skips it)

Expect at the end:

```
passed=30 failed=0
RESULT: PASS
VERIFY: PASS
```

Logs are written to `artifacts/logs/`.

## What each layer proves

### Rust unit tests (36)
| Module | Proves |
|---|---|
| `vec3` | unit normalization; zero/NaN rejected; sanitization clamps and kills NaN; arithmetic |
| `limits` | defaults valid; 1500-radius rejected; NaN/Inf rejected; zero lifetime rejected; clamping repairs garbage and stays valid |
| `projectile` | straight flight; nearest hit; non-hostile ignored; lifetime expiry; world-bounds expiry; zero direction rejected; ray/AABB hit, miss, inside; deterministic tie-break by id |
| `beam` | radius bounds; NaN center; inverted height; analytic count matches streamed count **exactly**; **no duplicate positions**; `contains`; 64-radius count does not overflow |
| `plan` | total equals footprint×height; chunk count; first batch is the top layer; **every position handed out exactly once**; source above the cap is rejected; empty source; batch never exceeds the requested size |
| `starflight-jni` | error codes distinct and negative; tuning validation matches core; beam parsing rejects junk; panic boundary returns fallback and passes values through; registry round-trip |

### Java SelfTest (30)
Proves the boundary, not just the math:
- library loads over JNI and reports a semver version;
- ABI versions agree (`1 == 1`);
- NaN / infinite / oversized / negative values are **rejected**, not clamped;
- beam validation rejects NaN center, inverted height, zero radius;
- block count is positive and errors on junk;
- plan lifecycle: build → count up → not complete → drop → count down;
- **drain**: every position delivered exactly once, total equals the plan,
  completion flag set;
- **buffer safety**: an undersized buffer is clamped to `len/3`, not overrun;
- unknown slot returns `ERR_NO_SUCH_SLOT`;
- error codes are distinct and negative;
- **16 000 concurrent calls from 8 threads** finish with no panic and no plan leak.

### Benchmark (`artifacts/logs/bench.log`)
Median of 9 rounds. On this machine:
```
single JNI round-trip : ~9 ns/call
batched drain         : ~46 ns/position  (batch = 16384)
one-position call     : ~91 ns/position  (one JNI call per block)
```
**Interpretation limits:** this measures the FFI boundary only. Minecraft's own
block-application cost dominates real world edits and is *not* measured here.

## Commands by hand

```bash
export CARGO_HOME="$PWD/tools/cargo" RUSTUP_HOME="$PWD/tools/rustup"
export PATH="$PWD/tools/cargo/bin:$PATH"
export JAVA_HOME="$PWD/tools/jdk/jdk-21.0.13+9"

cd rust && cargo test --workspace
cargo clippy --workspace --all-targets -- -D warnings
cargo build --release

cd ../java
"$JAVA_HOME/bin/javac" -d out src/main/java/dev/starport/*.java
"$JAVA_HOME/bin/java" -Djava.library.path="../rust/target/release" -cp out dev.starport.SelfTest
```

On Windows, `java.library.path` needs a forward-slash or escaped path; the
example above works from Git Bash.

## Negative tests that matter

These are the ones that would catch a security regression:

| Attack | Expected | Test |
|---|---|---|
| Tune damage to `NaN` | rejected | `SelfTest`, `limits` |
| Tune speed to `Infinity` | rejected | `SelfTest`, `limits` |
| Request a 1500-block radius | rejected | `SelfTest`, `limits` |
| Ask for a beam of absurd height | rejected | `beam`, `SelfTest` |
| Pass a slot that does not exist | `ERR_NO_SUCH_SLOT`, no crash | `SelfTest` |
| Pass a 6-int buffer while claiming 1000 | writes ≤2, no overrun | `SelfTest` |
| Hammer from 8 threads | no panic, no leak | `SelfTest` |
| Force a Rust panic | error code, JVM survives | `panic_boundary` unit test |

## Not tested (and cannot be here)

- Any behaviour inside Minecraft — no runtime.
- Rendering, sounds, cutscenes.
- Multiplayer, persistence.
- Windows path edge cases beyond the ones exercised above.