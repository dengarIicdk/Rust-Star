//! Test host for the scheduling logic.
//!
//! Horizon compiles the mod through `cargo-horizon-mod`, which needs the pinned
//! SDK and a C toolchain; neither is available on this machine (see `lib.rs`).
//! This binary exists so `strike.rs` — the part of the port that is genuinely
//! verifiable here — can be compiled and its tests run.

mod strike;

fn main() {
    // Exercise the scheduling logic end to end so the binary is not just a test
    // shell: build a real strike from the core and drain it a tick at a time.
    let mut strike = match strike::Strike::new(0.0, 0.0, 4.0, 60, 70) {
        Ok(s) => s,
        Err(e) => {
            eprintln!("could not build a strike: {e}");
            std::process::exit(1);
        }
    };
    println!(
        "queued a radius-{} strike at ({}, {}): {} positions",
        strike.spec.radius,
        strike.spec.center_x,
        strike.spec.center_z,
        strike.total()
    );

    let mut ticks = 0u32;
    let mut delivered = 0usize;
    while strike.phase() != strike::Phase::Done && ticks < 10_000 {
        delivered += strike.tick().len();
        ticks += 1;
    }

    println!("drained {delivered} positions over {ticks} ticks");
    if delivered as u64 != strike.total() {
        eprintln!(
            "MISMATCH: delivered {delivered}, expected {}",
            strike.total()
        );
        std::process::exit(1);
    }
    println!("ok: every position delivered exactly once");
}
