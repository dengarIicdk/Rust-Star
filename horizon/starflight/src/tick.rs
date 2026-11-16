//! `native_tick` injection body.
//!
//! NOT part of the standalone crate (`src/lib.rs`): this file only compiles
//! inside Horizon's patched client, where `crate::game` and `log` exist. It is
//! listed here for review, and `horizon-mixin.toml` names it as the helper for
//! the `tick` injection.
//!
//! Horizon calls the hook once per game tick. This helper runs inside the
//! patched client, where a panic would take the game down through game code
//! instead of a `catch_unwind` boundary, so nothing here unwraps or indexes.

use crate::game::starflight::{State, TickReport};

/// Called from `Game::native_tick` once per tick.
///
/// Returns the report rather than `()` so the hook is unit-testable without a
/// running game, and so a future caller can apply the batches it describes.
///
/// The block write is intentionally missing: Horizon exposes no hook for
/// editing blocks yet, and inventing one would not compile. What this does
/// today is advance every strike and keep the per-tick budget honest.
pub fn tick(state: &mut State) -> TickReport {
    let report = state.tick();

    // A strike runs for many ticks; a line per tick would flood the log. Report
    // only the transition to idle.
    if report.finished > 0 {
        log::info!(
            "starflight: {} strike(s) finished, {} still active",
            report.finished,
            report.active
        );
    }
    if let Some(err) = state.take_error() {
        log::warn!("starflight: strike rejected: {err}");
    }
    report
}
