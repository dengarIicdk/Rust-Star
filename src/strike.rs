//! Strike scheduling over the game-independent core.
//!
//! Everything here is plain Rust with no Horizon or Minecraft types, so it
//! compiles and is tested on this machine even though the SDK-based hooks cannot
//! be built here (see `lib.rs`). When the SDK becomes available the hooks only
//! have to *call* this: the behaviour is already proven.
//!
//! The block positions come from `starflight::beam` / `starflight::plan`, which
//! enforce `MAX_BLOCKS_PER_TICK` themselves. This module decides when to ask.

use crate::core::beam::{BeamBlocks, BeamSpec};
use crate::core::limits::MAX_BLOCKS_PER_TICK;
use crate::core::plan::{BlockPos, Plan};

/// How far the beam falls before the ground is carved.
///
/// The Fabric build descends for real, 180 blocks above the world at 6 blocks a
/// tick. Horizon has no per-tick entity movement hook in the surface this port
/// targets, so the descent is a scheduled delay of the same *duration* instead
/// of a simulated fall: the player sees the effect begin after the same wait.
pub const DESCENT_TICKS: u32 = 30;

/// Where a strike is in its life.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Phase {
    /// Waiting out the fall before the carve begins.
    Descending,
    /// Handing the beam's positions to the world, a budget per tick.
    Carving,
    /// Every position has been handed over.
    Done,
}

/// One strike.
#[derive(Debug)]
pub struct Strike {
    plan: Plan,
    /// The beam's request, kept so the strike can be described and resumed.
    pub spec: BeamSpec,
    phase: Phase,
    ticks_to_impact: u32,
    /// Positions handed to the world so far.
    pub applied: u64,
}

impl Strike {
    /// Queues a strike over the column at `center_x, center_z`.
    ///
    /// # Errors
    /// Returns the core's rejection reason: a radius outside
    /// `[MIN_RADIUS, HARD_MAX_RADIUS]`, a non-finite centre, an inverted height
    /// range, or a request larger than `MAX_BLOCKS_PER_STRIKE`. Out-of-range
    /// input is refused rather than quietly clamped (decision D5).
    pub fn new(
        center_x: f64,
        center_z: f64,
        radius: f64,
        y_min: i32,
        y_max: i32,
    ) -> Result<Self, String> {
        let spec = BeamSpec {
            center_x,
            center_z,
            radius,
            y_min,
            y_max,
        }
        .validate()
        .map_err(|e| e.to_string())?;

        let plan = Plan::build(BeamBlocks::new(spec)).map_err(|e| e.to_string())?;

        Ok(Self {
            plan,
            spec,
            phase: Phase::Descending,
            ticks_to_impact: DESCENT_TICKS,
            applied: 0,
        })
    }

    #[must_use]
    pub const fn phase(&self) -> Phase {
        self.phase
    }

    /// Total positions this strike will ever produce.
    #[must_use]
    pub const fn total(&self) -> u64 {
        self.plan.total_blocks
    }

    /// Advances one tick and returns the positions to write now.
    ///
    /// The batch is copied out of the plan because `take_batch` borrows it
    /// mutably while the completion flag has to be read afterwards. One tick's
    /// budget is at most `MAX_BLOCKS_PER_TICK` positions (65 536), so the copy is
    /// bounded and small next to the work of writing those blocks.
    ///
    /// The result is empty while the beam is descending and after the plan is
    /// drained, and never longer than [`MAX_BLOCKS_PER_TICK`].
    #[must_use]
    pub fn tick(&mut self) -> Vec<BlockPos> {
        if self.phase == Phase::Descending {
            if self.ticks_to_impact > 0 {
                self.ticks_to_impact -= 1;
            }
            if self.ticks_to_impact > 0 {
                return Vec::new();
            }
            self.phase = Phase::Carving;
        }

        if self.phase != Phase::Carving {
            return Vec::new();
        }

        let batch = self.plan.take_batch(MAX_BLOCKS_PER_TICK as usize).to_vec();
        self.applied += batch.len() as u64;
        if self.plan.is_complete() {
            self.phase = Phase::Done;
        }
        batch
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn a_strike_waits_for_the_descent_then_carves() {
        let mut s = Strike::new(0.0, 0.0, 3.0, 60, 64).expect("valid beam");

        // Nothing is written during the descent.
        for _ in 0..DESCENT_TICKS - 1 {
            assert!(s.tick().is_empty(), "must not carve before impact");
        }
        assert_eq!(s.phase(), Phase::Descending);

        // The first carve tick happens exactly at the end of the descent.
        //
        // A small beam can finish inside that single tick, so the observable
        // guarantee is "it carved", not "the phase is still Carving": asserting
        // the phase here would make the test depend on the beam's size.
        let first = s.tick();
        assert!(!first.is_empty(), "must carve once the descent ends");
        assert!(
            matches!(s.phase(), Phase::Carving | Phase::Done),
            "carving must have begun, got {:?}",
            s.phase()
        );
    }

    #[test]
    fn the_whole_beam_is_delivered_exactly_once() {
        let mut s = Strike::new(0.0, 0.0, 2.0, 60, 63).expect("valid beam");
        let total = s.total();
        assert!(total > 0);

        let mut seen = std::collections::HashSet::new();
        let mut delivered = 0u64;
        for _ in 0..2_000 {
            for p in s.tick() {
                assert!(
                    seen.insert((p.x, p.y, p.z)),
                    "position {:?} was written twice",
                    p
                );
                delivered += 1;
            }
            if s.phase() == Phase::Done {
                break;
            }
        }

        assert_eq!(s.phase(), Phase::Done, "the strike must finish");
        assert_eq!(delivered, total, "every position is delivered exactly once");
        assert_eq!(s.applied, total);
        assert!(s.tick().is_empty(), "a finished strike writes nothing");
    }

    #[test]
    fn never_exceeds_the_per_tick_budget() {
        // A tall, wide beam needs far more than one tick's budget.
        let mut s = Strike::new(0.0, 0.0, 16.0, -64, 320).expect("valid beam");
        assert!(s.total() > u64::from(MAX_BLOCKS_PER_TICK));

        for _ in 0..40 {
            let batch = s.tick();
            assert!(
                batch.len() <= MAX_BLOCKS_PER_TICK as usize,
                "budget exceeded: {} > {}",
                batch.len(),
                MAX_BLOCKS_PER_TICK
            );
        }
    }

    #[test]
    fn an_absurd_radius_is_refused_not_clamped() {
        // Above the core's hard cap: the caller is told no, and no plan exists.
        let err = Strike::new(0.0, 0.0, 10_000.0, 0, 10).expect_err("must refuse");
        assert!(
            err.contains("radius"),
            "reason should name the radius: {err}"
        );
    }

    #[test]
    fn a_non_finite_centre_is_refused() {
        assert!(Strike::new(f64::NAN, 0.0, 4.0, 0, 10).is_err());
        assert!(Strike::new(0.0, f64::INFINITY, 4.0, 0, 10).is_err());
    }

    #[test]
    fn an_inverted_height_range_is_refused() {
        assert!(Strike::new(0.0, 0.0, 4.0, 100, 50).is_err());
    }
}
