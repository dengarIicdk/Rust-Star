//! Starflight runtime, built into Horizon as `crate::game::starflight`.
//!
//! Holds the state of every strike in flight. Deliberately contains no Minecraft
//! types: the simulation maths lives in the `starflight` crate, which is plain
//! Rust, has 38 tests and no game dependency.
//!
//! Nothing here writes blocks yet. Horizon has no hook for editing blocks as of
//! the check date (`native_set_block` and friends are "Planned in 0.3/0.5"), so
//! the runtime advances plans and returns the positions it *wants* written. The
//! write itself is the one piece waiting on the platform.

use starflight::beam::{BeamBlocks, BeamSpec};
use starflight::limits::MAX_BLOCKS_PER_TICK;
use starflight::plan::Plan;
use starflight::vec3::Vec3;

/// A strike in progress.
pub struct Strike {
    /// The plan owns the block arithmetic, chunk batching and budget.
    pub plan: Plan,
    /// Where the beam was aimed, kept for logging while there is no way to draw
    /// or apply it.
    pub origin: (f64, f64),
}

/// What one tick produced.
#[derive(Debug, Default, Clone, PartialEq, Eq)]
pub struct TickReport {
    /// Positions the plan wants written this tick, per strike, capped by the
    /// core at [`MAX_BLOCKS_PER_TICK`].
    pub batches: Vec<Vec<(i32, i32, i32)>>,
    /// Strikes still running after this tick.
    pub active: usize,
    /// Strikes that finished during this tick.
    pub finished: usize,
}

/// Everything the mod owns at runtime.
#[derive(Default)]
pub struct State {
    strikes: Vec<Strike>,
    /// Set when a request was refused, so the reason is visible in the log
    /// instead of the mod silently doing nothing.
    last_error: Option<String>,
}

impl State {
    #[must_use]
    pub const fn new() -> Self {
        Self {
            strikes: Vec::new(),
            last_error: None,
        }
    }

    #[must_use]
    pub fn active(&self) -> usize {
        self.strikes.len()
    }

    /// Queues a vertical cylinder centred on `x, z`.
    ///
    /// Out-of-range input is rejected, not clamped: a caller asking for a
    /// 10 000-block radius must be told no rather than handed a smaller crater
    /// it did not ask for (decision D5).
    ///
    /// # Errors
    /// Returns the core's rejection reason, which is already human-readable.
    pub fn queue_strike(
        &mut self,
        x: f64,
        z: f64,
        radius: f64,
        y_min: i32,
        y_max: i32,
    ) -> Result<(), String> {
        let spec = BeamSpec {
            center_x: x,
            center_z: z,
            radius,
            y_min,
            y_max,
        }
        .validate()
        .map_err(|e| {
            let msg = e.to_string();
            self.last_error = Some(msg.clone());
            msg
        })?;

        let plan = Plan::build(BeamBlocks::new(spec)).map_err(|e| {
            let msg = e.to_string();
            self.last_error = Some(msg.clone());
            msg
        })?;

        self.strikes.push(Strike {
            plan,
            origin: (x, z),
        });
        Ok(())
    }

    /// Declares a projectile impact at `at` and queues the beam it produces.
    ///
    /// Present so the flight and the strike share one entry point once a
    /// projectile entity can actually exist in Horizon.
    ///
    /// # Errors
    /// As [`State::queue_strike`].
    pub fn impact(&mut self, at: Vec3, radius: f64, y_min: i32, y_max: i32) -> Result<(), String> {
        self.queue_strike(at.x, at.z, radius, y_min, y_max)
    }

    /// Advances every strike by one tick.
    ///
    /// The batch limit is applied here as well as in the core so a future caller
    /// cannot exceed it by passing a larger number.
    pub fn tick(&mut self) -> TickReport {
        let mut report = TickReport::default();
        let limit = MAX_BLOCKS_PER_TICK as usize;

        for strike in &mut self.strikes {
            let batch: Vec<(i32, i32, i32)> = strike
                .plan
                .take_batch(limit)
                .iter()
                .map(|p| (p.x, p.y, p.z))
                .collect();
            // Keep one entry per strike, even when empty, so the caller can
            // align batches with strikes.
            report.batches.push(batch);
            if strike.plan.is_complete() {
                report.finished += 1;
            }
        }

        // Drop finished strikes so the vector cannot grow over a long session.
        self.strikes.retain(|s| !s.plan.is_complete());
        report.active = self.strikes.len();
        report
    }

    /// Takes the last error, if any, leaving the slot empty.
    pub fn take_error(&mut self) -> Option<String> {
        self.last_error.take()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn queues_and_completes_a_small_beam() {
        let mut s = State::new();
        s.queue_strike(0.0, 0.0, 2.0, 60, 64).expect("valid beam");
        assert_eq!(s.active(), 1);

        let mut total = 0;
        for _ in 0..64 {
            let r = s.tick();
            total += r.batches.iter().map(Vec::len).sum::<usize>();
        }
        assert!(
            total > 0,
            "a 5-block-tall radius-2 beam must carve something"
        );
        assert_eq!(s.active(), 0, "the strike must retire when complete");
    }

    #[test]
    fn rejects_an_absurd_radius_instead_of_clamping() {
        let mut s = State::new();
        assert!(s.queue_strike(0.0, 0.0, 10_000.0, 0, 10).is_err());
        assert_eq!(s.active(), 0);
        assert!(s.take_error().is_some(), "the refusal must be reportable");
    }

    #[test]
    fn never_asks_for_more_than_the_per_tick_budget() {
        let mut s = State::new();
        s.queue_strike(0.0, 0.0, 16.0, -64, 320)
            .expect("valid beam");
        for _ in 0..8 {
            let r = s.tick();
            for batch in &r.batches {
                assert!(
                    batch.len() <= MAX_BLOCKS_PER_TICK as usize,
                    "budget exceeded: {}",
                    batch.len()
                );
            }
        }
    }

    #[test]
    fn each_position_is_requested_exactly_once_across_ticks() {
        let mut s = State::new();
        s.queue_strike(0.0, 0.0, 2.0, 60, 63).expect("valid beam");
        let mut seen = std::collections::HashSet::new();
        for _ in 0..64 {
            for batch in s.tick().batches {
                for p in batch {
                    assert!(seen.insert(p), "position {p:?} was requested twice");
                }
            }
        }
        assert!(!seen.is_empty());
    }
}
