//! JNI bridge exposing [`starflight`] to a Minecraft Java adapter.
//!
//! # Safety rules enforced at this boundary
//!
//! * No Rust panic may cross into Java. Every exported function wraps its body
//!   in [`panic_boundary`], which catches the unwind and returns a sentinel
//!   error value instead.
//! * All primitive arguments are sanitized in [`starflight::limits`] before any
//!   math runs. NaN/Inf and out-of-range values are rejected, never trusted.
//! * The only objects exchanged are strings and `jintArray`s. No Java object
//!   references are cached, so there is nothing to leak or garbage-collect
//!   incorrectly.
//! * No thread-local or `AttachCurrentThread` state: each call is independent
//!   and runs on the caller's thread.
//!
//! # Why batches instead of per-block calls
//!
//! A beam can cover hundreds of thousands of blocks. One JNI call per block
//! would cost far more than the block write itself (JNI transition ≈ tens of
//! ns, plus Java-side overhead). Instead one call returns a whole tick's worth
//! of positions into a pre-allocated `int[]` that Java reuses, so steady-state
//! batching has zero allocation on either side. Measurements are in
//! `docs/TESTING.md`; the number is not asserted to be "fast" without data.
//!
//! The Java side is still responsible for *applying* blocks through Minecraft's
//! API — Rust never touches the world.

use jni::objects::{JClass, JIntArray};
use jni::sys::{jdouble, jint, jlong, jstring};
use jni::JNIEnv;

use std::collections::HashMap;
use std::sync::Mutex;
use std::sync::OnceLock;

use starflight::beam::{BeamBlocks, BeamSpec};
use starflight::limits::{Tuning, MAX_BLOCKS_PER_TICK};
use starflight::plan::Plan;

/// ---------------------------------------------------------------------------
/// Result codes shared with `NativeBridge.java`. Keep in sync — there is a test
/// on the Java side that asserts these exact numbers.
/// ---------------------------------------------------------------------------
pub const OK: i32 = 0;
pub const ERR_INVALID_TUNING: i32 = -1;
pub const ERR_PANIC: i32 = -2;
pub const ERR_INVALID_BEAM: i32 = -3;
pub const ERR_NO_SUCH_SLOT: i32 = -4;
pub const ERR_NULL_BUFFER: i32 = -5;

/// Returned by the `jlong`-returning functions on failure, chosen so it can
/// never be confused with a legitimate positive count.
pub const JLONG_ERR: i64 = i64::MIN;

/// Runs `body` and converts any panic into `fallback`, so JNI never unwinds
/// into Java (which would abort the JVM).
fn panic_boundary<T>(fallback: T, body: impl FnOnce() -> T) -> T {
    match std::panic::catch_unwind(std::panic::AssertUnwindSafe(body)) {
        Ok(v) => v,
        Err(payload) => {
            // Log once per panic; never propagate the message to Java as an
            // exception because throwing from here can itself unwind.
            let msg = payload
                .downcast_ref::<&str>()
                .map(|s| (*s).to_string())
                .or_else(|| payload.downcast_ref::<String>().cloned())
                .unwrap_or_else(|| "<non-string panic payload>".to_string());
            eprintln!("[starflight-jni] panic caught at FFI boundary: {msg}");
            fallback
        }
    }
}

/// Active plans, keyed by a caller-supplied slot id.
///
/// The Java adapter allocates a small integer slot per in-flight strike and
/// must call `dropPlan` when done. `Mutex` (not `RwLock`) because every
/// operation mutates cursors; contention is negligible (one server tick at a
/// time per strike).
type Registry = Mutex<HashMap<i32, Plan>>;

fn registry() -> &'static Registry {
    static REG: OnceLock<Registry> = OnceLock::new();
    REG.get_or_init(|| Mutex::new(HashMap::new()))
}

/// Validates a tuning quadruple. Public for the Java `selftest` path.
fn check_tuning(radius: f64, damage: f64, speed: f64, lifetime: i64) -> i32 {
    if !(0..=i64::from(u32::MAX)).contains(&lifetime) {
        return ERR_INVALID_TUNING;
    }
    let t = Tuning {
        radius,
        damage,
        speed,
        lifetime_ticks: lifetime as u32,
    };
    match t.validate() {
        Ok(_) => OK,
        Err(_) => ERR_INVALID_TUNING,
    }
}

fn parse_beam(cx: f64, cz: f64, radius: f64, y_min: i32, y_max: i32) -> Option<BeamSpec> {
    BeamSpec {
        center_x: cx,
        center_z: cz,
        radius,
        y_min,
        y_max,
    }
    .validate()
    .ok()
}

//
// ---------------------------------------------------------------------------
// Exported functions. Names follow JNI mangling for class
// `dev.starport.NativeBridge` (package + class are fixed by the Java adapter).
// ---------------------------------------------------------------------------
//

/// Returns the Rust crate version (`0.1.0`) or null on failure.
#[no_mangle]
pub extern "system" fn Java_dev_starport_NativeBridge_versionNative(
    env: JNIEnv,
    _class: JClass,
) -> jstring {
    panic_boundary(std::ptr::null_mut(), || {
        match env.new_string(starflight::version()) {
            Ok(s) => s.into_raw(),
            Err(_) => std::ptr::null_mut(),
        }
    })
}

/// Compile-time ABI version. The Java adapter refuses to run if this is not the
/// value it was built against, so a stale `.dll` next to a new `.jar` fails
/// loudly instead of silently misbehaving.
#[no_mangle]
pub extern "system" fn Java_dev_starport_NativeBridge_abiVersionNative(
    _env: JNIEnv,
    _class: JClass,
) -> jint {
    starflight::ABI_VERSION as jint
}

/// Validates tuning. Returns [`OK`] or [`ERR_INVALID_TUNING`].
#[no_mangle]
pub extern "system" fn Java_dev_starport_NativeBridge_vtNative(
    _env: JNIEnv,
    _class: JClass,
    radius: jdouble,
    damage: jdouble,
    speed: jdouble,
    lifetime_ticks: jlong,
) -> jint {
    panic_boundary(ERR_PANIC, || {
        check_tuning(radius, damage, speed, lifetime_ticks)
    })
}

/// Validates a beam geometry. Returns [`OK`] or [`ERR_INVALID_BEAM`].
#[no_mangle]
pub extern "system" fn Java_dev_starport_NativeBridge_vbNative(
    _env: JNIEnv,
    _class: JClass,
    center_x: jdouble,
    center_z: jdouble,
    radius: jdouble,
    y_min: jint,
    y_max: jint,
) -> jint {
    panic_boundary(ERR_PANIC, || {
        match parse_beam(center_x, center_z, radius, y_min, y_max) {
            Some(_) => OK,
            None => ERR_INVALID_BEAM,
        }
    })
}

/// Number of blocks a beam would cover, without building a plan. Useful for a
/// confirmation UI ("this will erase N blocks"). Returns `<= 0` on error.
#[no_mangle]
pub extern "system" fn Java_dev_starport_NativeBridge_bbcNative(
    _env: JNIEnv,
    _class: JClass,
    center_x: jdouble,
    center_z: jdouble,
    radius: jdouble,
    y_min: jint,
    y_max: jint,
) -> jlong {
    panic_boundary(JLONG_ERR, || {
        match parse_beam(center_x, center_z, radius, y_min, y_max) {
            None => JLONG_ERR,
            Some(spec) => {
                let (count, _saturated) = spec.covered_block_count();
                // Clamp to i64::MAX-1 so it stays distinguishable from error.
                count.min(i64::MAX as u64 - 1) as jlong
            }
        }
    })
}

/// Builds a plan for the beam and stores it in `slot`, replacing any existing
/// plan there. Returns the total block count, or `JLONG_ERR` on failure.
#[no_mangle]
pub extern "system" fn Java_dev_starport_NativeBridge_bpNative(
    _env: JNIEnv,
    _class: JClass,
    center_x: jdouble,
    center_z: jdouble,
    radius: jdouble,
    y_min: jint,
    y_max: jint,
    slot: jint,
) -> jlong {
    panic_boundary(JLONG_ERR, || {
        if slot < 0 {
            return JLONG_ERR;
        }
        let spec = match parse_beam(center_x, center_z, radius, y_min, y_max) {
            Some(s) => s,
            None => return JLONG_ERR,
        };
        let plan = match Plan::build(BeamBlocks::new(spec)) {
            Ok(p) => p,
            Err(_) => return JLONG_ERR,
        };
        let total = plan.total_blocks as jlong;
        let mut reg = match registry().lock() {
            Ok(g) => g,
            Err(poisoned) => poisoned.into_inner(),
        };
        reg.insert(slot, plan);
        total
    })
}

/// True when the plan in `slot` has handed out every position. Unknown slots
/// report `true` so a lost plan can never wedge a caller's tick loop.
#[no_mangle]
pub extern "system" fn Java_dev_starport_NativeBridge_pcNative(
    _env: JNIEnv,
    _class: JClass,
    slot: jint,
) -> jint {
    panic_boundary(1, || {
        let reg = match registry().lock() {
            Ok(g) => g,
            Err(p) => p.into_inner(),
        };
        match reg.get(&slot) {
            Some(p) if !p.is_complete() => 0,
            _ => 1,
        }
    })
}

/// Plans currently registered. Lets Java assert cleanup after a strike, and
/// lets the test suite detect leaks.
#[no_mangle]
pub extern "system" fn Java_dev_starport_NativeBridge_plcNative(
    _env: JNIEnv,
    _class: JClass,
) -> jint {
    panic_boundary(-1, || {
        let reg = match registry().lock() {
            Ok(g) => g,
            Err(p) => p.into_inner(),
        };
        reg.len() as jint
    })
}

/// Drops the plan in `slot`. Returns [`OK`] even if absent (idempotent), so
/// cleanup in a `finally` block cannot fail.
#[no_mangle]
pub extern "system" fn Java_dev_starport_NativeBridge_dpNative(
    _env: JNIEnv,
    _class: JClass,
    slot: jint,
) -> jint {
    panic_boundary(ERR_PANIC, || {
        let mut reg = match registry().lock() {
            Ok(g) => g,
            Err(p) => p.into_inner(),
        };
        reg.remove(&slot);
        OK
    })
}

/// Hands the next batch of positions from `slot` into `out`, which must hold at
/// least `out.len()` ints. Writes `x, y, z` triplets and returns the number of
/// **triplets** written (0 when finished).
///
/// `max_triplets` is clamped to [`MAX_BLOCKS_PER_TICK`] and to
/// `out.len() / 3`, so an undersized buffer can never be overrun.
#[no_mangle]
pub extern "system" fn Java_dev_starport_NativeBridge_tbNative(
    env: JNIEnv,
    _class: JClass,
    slot: jint,
    out: JIntArray,
    max_triplets: jint,
) -> jint {
    panic_boundary(ERR_PANIC, || {
        if out.is_null() || max_triplets < 0 {
            return ERR_NULL_BUFFER;
        }
        let out_len = match env.get_array_length(&out) {
            Ok(n) if n >= 0 => n as usize,
            _ => return ERR_NULL_BUFFER,
        };
        let capacity = out_len / 3;
        let want = (max_triplets as usize)
            .min(capacity)
            .min(MAX_BLOCKS_PER_TICK as usize);
        if want == 0 {
            return 0;
        }

        // Flatten while holding the lock, then release before touching Java:
        // JNI calls can allocate Java objects (which may trigger GC) and we do
        // not want to hold our own lock across that.
        //
        // The flattened buffer is allocated fresh per call on purpose: keeping
        // one in a global would need a second lock or `thread_local!`, and at
        // ~48 bytes per position a 16k batch is under 800 kB — cheap enough
        // that the simpler ownership wins. Measured in artifacts/logs/bench.log.
        let mut flat: Vec<jint> = Vec::with_capacity(want * 3);
        {
            let mut reg = match registry().lock() {
                Ok(g) => g,
                Err(p) => p.into_inner(),
            };
            let plan = match reg.get_mut(&slot) {
                Some(p) => p,
                None => return ERR_NO_SUCH_SLOT,
            };
            for p in plan.take_batch(want) {
                flat.push(p.x);
                flat.push(p.y);
                flat.push(p.z);
            }
        }
        let count = flat.len() / 3;
        if count == 0 {
            return 0;
        }
        match env.set_int_array_region(&out, 0, &flat) {
            Ok(()) => count as jint,
            Err(_) => ERR_NULL_BUFFER,
        }
    })
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn codes_are_distinct_and_negative() {
        let errs = [
            ERR_INVALID_TUNING,
            ERR_PANIC,
            ERR_INVALID_BEAM,
            ERR_NO_SUCH_SLOT,
            ERR_NULL_BUFFER,
        ];
        assert_eq!(OK, 0);
        let mut seen = std::collections::HashSet::new();
        for e in errs {
            assert!(e < 0, "error code must be negative: {e}");
            assert!(seen.insert(e), "duplicate error code: {e}");
        }
    }

    #[test]
    fn jlong_error_is_negative() {
        const { assert!(JLONG_ERR < 0) };
    }

    #[test]
    fn tuning_checks_match_core() {
        assert_eq!(check_tuning(8.0, 60.0, 2.5, 400), OK);
        assert_eq!(check_tuning(f64::NAN, 60.0, 2.5, 400), ERR_INVALID_TUNING);
        assert_eq!(check_tuning(999.0, 60.0, 2.5, 400), ERR_INVALID_TUNING);
        assert_eq!(check_tuning(8.0, 60.0, 2.5, -1), ERR_INVALID_TUNING);
        assert_eq!(check_tuning(8.0, 60.0, 2.5, i64::MAX), ERR_INVALID_TUNING);
    }

    #[test]
    fn beam_parse_rejects_junk() {
        assert!(parse_beam(0.0, 0.0, 4.0, 0, 10).is_some());
        assert!(parse_beam(f64::INFINITY, 0.0, 4.0, 0, 10).is_none());
        assert!(parse_beam(0.0, 0.0, 0.0, 0, 10).is_none());
        assert!(parse_beam(0.0, 0.0, 4.0, 10, 0).is_none());
    }

    #[test]
    fn panic_boundary_returns_fallback() {
        let v = panic_boundary(-7, || panic!("boom"));
        assert_eq!(v, -7);
    }

    #[test]
    fn panic_boundary_passes_value_through() {
        assert_eq!(panic_boundary(-7, || 42), 42);
    }

    #[test]
    fn registry_roundtrip() {
        let slot = 10_001;
        let total = plan_for_test(slot);
        assert!(total > 0);
        {
            let reg = registry().lock().unwrap();
            assert!(reg.contains_key(&slot));
        }
        {
            let mut reg = registry().lock().unwrap();
            reg.remove(&slot);
        }
        let reg = registry().lock().unwrap();
        assert!(!reg.contains_key(&slot));
    }

    fn plan_for_test(slot: i32) -> i64 {
        let spec = BeamSpec {
            center_x: 0.0,
            center_z: 0.0,
            radius: 2.0,
            y_min: 0,
            y_max: 3,
        }
        .validate()
        .expect("valid");
        let plan = Plan::build(BeamBlocks::new(spec)).expect("under cap");
        let total = plan.total_blocks as i64;
        registry().lock().unwrap().insert(slot, plan);
        total
    }
}
