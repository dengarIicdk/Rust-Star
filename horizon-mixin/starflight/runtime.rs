//! Starflight's game-facing half.
//!
//! Injected into `crates/horizon-client/src/game.rs` as the module
//! `crate::game::starflight`, so it may name the server's own types. The
//! geometry lives in the `starflight-engine` crate, which knows nothing about
//! the game and is unit-tested on a desktop.
//!
//! This file is also a standalone Cargo library: `cargo test` compiles it here
//! without Horizon. Nothing in it is allowed to need the game to be *tested* —
//! the parts that do are the three `native_*` entry points, kept thin.

use std::sync::{Mutex, OnceLock};

use starflight_engine::{BeamSpec, Plan, Vec3, HARD_MAX_RADIUS, MIN_RADIUS};

/// The item that calls a strike. Declared by the content bundle beside this one
/// (`data/starflight/...`); a right click with it queues one strike.
pub const STRIKE_ITEM: &str = "starflight:orbital_rocket";

/// How many ticks the beam takes to fall before it starts carving.
pub const DESCENT_TICKS: u32 = 30;

/// Blocks carved above and below the clicked point.
pub const CARVE_ABOVE: i32 = 24;
pub const CARVE_BELOW: i32 = 16;

/// The default radius of a strike.
pub const STRIKE_RADIUS: f64 = 16.0;

/// One strike being carved.
#[derive(Debug)]
pub struct Strike {
    plan: Plan,
    /// The level's id (`minecraft:overworld`), as the game names it.
    pub dimension: String,
    pub center: Vec3,
    descent: u32,
    pub applied: u64,
}

impl Strike {
    /// Queues a strike over the column at `center`.
    ///
    /// Out-of-range input is refused, never quietly clamped (the same decision
    /// the Fabric build makes): a radius outside `[MIN_RADIUS,
    /// HARD_MAX_RADIUS]` is a caller's mistake, and shrinking it silently would
    /// carve a different thing than was asked for.
    pub fn new(dimension: &str, center: Vec3, radius: f64, y_min: i32, y_max: i32) -> Result<Self, String> {
        let spec = BeamSpec {
            center_x: center.x,
            center_z: center.z,
            radius,
            y_min,
            y_max,
        }
        .validate()
        .map_err(|e| e.to_string())?;
        let plan = Plan::build(starflight_engine::BeamBlocks::new(spec)).map_err(|e| e.to_string())?;
        Ok(Self {
            plan,
            dimension: dimension.to_owned(),
            center,
            descent: DESCENT_TICKS,
            applied: 0,
        })
    }

    /// Total positions the strike will ever produce.
    #[must_use]
    pub const fn total(&self) -> u64 {
        self.plan.total_blocks
    }

    /// Advances one tick: `None` while the beam is still falling, otherwise the
    /// positions to write now (never more than `MAX_BLOCKS_PER_TICK`).
    #[must_use]
    pub fn tick(&mut self) -> Option<Vec<[i32; 3]>> {
        if self.descent > 0 {
            self.descent -= 1;
            return None;
        }
        let batch = self.plan.take_batch(starflight_engine::MAX_BLOCKS_PER_TICK as usize);
        if batch.is_empty() {
            return Some(Vec::new());
        }
        self.applied += batch.len() as u64;
        Some(batch.iter().map(|p| [p.x, p.y, p.z]).collect())
    }

    /// Whether every position has been handed over.
    #[must_use]
    pub fn is_complete(&self) -> bool {
        self.descent == 0 && self.plan.is_complete()
    }
}

/// The strikes in flight. A `Mutex` because the game calls hooks through `&self`
/// and a native mod must stay `Sync`.
fn active() -> &'static Mutex<Vec<Strike>> {
    static ACTIVE: OnceLock<Mutex<Vec<Strike>>> = OnceLock::new();
    ACTIVE.get_or_init(|| Mutex::new(Vec::new()))
}

/// Queues a strike, or returns why it was refused.
pub fn queue(dimension: &str, center: Vec3, radius: f64, y_min: i32, y_max: i32) -> Result<u64, String> {
    let strike = Strike::new(dimension, center, radius, y_min, y_max)?;
    let total = strike.total();
    let mut strikes = active().lock().map_err(|_| "strike list is poisoned")?;
    strikes.push(strike);
    Ok(total)
}

/// Number of strikes in flight, for the command's reply.
pub fn pending() -> usize {
    active().lock().map_or(0, |s| s.len())
}

/// Radius from a command argument: a number, or the default.
pub fn radius_or_default(argument: Option<f64>) -> Result<f64, String> {
    let radius = argument.unwrap_or(STRIKE_RADIUS);
    if !radius.is_finite() || radius < MIN_RADIUS || radius > HARD_MAX_RADIUS {
        return Err(format!(
            "radius must be between {MIN_RADIUS} and {HARD_MAX_RADIUS}, got {radius}"
        ));
    }
    Ok(radius)
}

/// The block every carve writes. One palette entry for the whole strike, so the
/// edit never carries more state names than it needs.
pub const CARVE_BLOCK: &str = "minecraft:air";

#[cfg(horizon_game)]
/// Writes one tick's worth of blocks for every strike in flight.
///
/// Each strike gets at most `MAX_BLOCKS_PER_TICK` positions from the engine, so
/// a huge strike cannot stall a tick. A refused edit drops that strike rather
/// than retrying forever: the edit is all-or-nothing, so the world is untouched.
pub fn apply_tick(game: &mut crate::game::Game<'_>) {
    let Ok(mut strikes) = active().lock() else {
        return;
    };
    if strikes.is_empty() {
        return;
    }
    let mut finished: Vec<usize> = Vec::new();
    for (index, strike) in strikes.iter_mut().enumerate() {
        let Some(positions) = strike.tick() else {
            continue;
        };
        if positions.is_empty() {
            if strike.is_complete() {
                finished.push(index);
            }
            continue;
        }
        let edit = crate::game::NativeBlockEdit {
            palette: vec![CARVE_BLOCK.to_owned()],
            filters: Vec::new(),
            runs: positions
                .iter()
                .map(|[x, y, z]| crate::game::NativeBlockRun {
                    x: *x,
                    z: *z,
                    y: *y,
                    height: 1,
                    surface: None,
                    block: 0,
                    only: None,
                })
                .collect(),
            neighbour_updates: false,
            block_entities: Vec::new(),
        };
        if let Err(reason) = game.native_edit_blocks(&strike.dimension, &edit) {
            eprintln!("starflight: block edit refused: {reason}");
            finished.push(index);
        } else if strike.is_complete() {
            finished.push(index);
        }
    }
    for index in finished.into_iter().rev() {
        strikes.swap_remove(index);
    }
}

#[cfg(horizon_game)]
/// Where the acting player is: the level's id and the block under them.
///
/// `native_player` is the game's own view of the player (`NativePlayer`), so
/// this needs no entity lookup and works for the guest and the local player
/// alike.
pub fn player_place(game: &crate::game::Game<'_>) -> Option<(&'static str, [i32; 3])> {
    let player = game.native_player()?;
    let pos = [
        player.x.floor() as i32,
        player.y.floor() as i32,
        player.z.floor() as i32,
    ];
    Some((player.dimension, pos))
}

#[cfg(horizon_game)]
/// The command a player types to call a strike. Operator-only: a radius can
/// carve a large volume.
pub fn strike_command() -> crate::game::NativeCommand {
    crate::game::NativeCommand::op("starflight strike [radius]", 2)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn a_strike_descends_before_it_carves() {
        let mut s = Strike::new("minecraft:overworld", Vec3::new(0.0, 64.0, 0.0), 3.0, 60, 70)
            .expect("valid beam");
        for tick in 0..DESCENT_TICKS {
            assert!(s.tick().is_none(), "tick {tick} must still be descending");
        }
        assert!(s.tick().is_some(), "the beam carves once it has landed");
    }

    #[test]
    fn every_position_is_handed_over_exactly_once() {
        let mut s = Strike::new("minecraft:overworld", Vec3::new(0.0, 64.0, 0.0), 3.0, 60, 70)
            .expect("valid beam");
        let total = s.total();
        let mut seen = 0u64;
        for _ in 0..DESCENT_TICKS {
            assert!(s.tick().is_none());
        }
        while !s.is_complete() {
            let batch = s.tick().expect("not descending");
            seen += batch.len() as u64;
        }
        assert_eq!(seen, total, "the batches must add up to the plan");
        assert_eq!(s.applied, total);
    }

    #[test]
    fn a_batch_never_exceeds_the_per_tick_budget() {
        let mut s = Strike::new("minecraft:overworld", Vec3::new(0.0, 64.0, 0.0), 64.0, -64, 320)
            .expect("valid beam");
        for _ in 0..DESCENT_TICKS {
            assert!(s.tick().is_none());
        }
        while !s.is_complete() {
            let batch = s.tick().expect("not descending");
            assert!(
                (batch.len() as u64) <= u64::from(starflight_engine::MAX_BLOCKS_PER_TICK),
                "one tick wrote {} blocks",
                batch.len()
            );
        }
    }

    #[test]
    fn an_absurd_radius_is_refused_not_clamped() {
        let err = Strike::new("minecraft:overworld", Vec3::new(0.0, 64.0, 0.0), 10_000.0, 0, 10)
            .expect_err("must refuse");
        assert!(err.contains("radius"), "the reason should name the radius: {err}");
    }

    #[test]
    fn a_non_finite_centre_is_refused() {
        assert!(Strike::new("minecraft:overworld", Vec3::new(f64::NAN, 64.0, 0.0), 4.0, 0, 10).is_err());
        assert!(Strike::new("minecraft:overworld", Vec3::new(0.0, 64.0, f64::INFINITY), 4.0, 0, 10).is_err());
    }

    #[test]
    fn the_radius_argument_is_validated_the_same_way() {
        assert_eq!(radius_or_default(None).expect("default"), STRIKE_RADIUS);
        assert_eq!(radius_or_default(Some(8.0)).expect("valid"), 8.0);
        assert!(radius_or_default(Some(0.0)).is_err(), "below the minimum");
        assert!(radius_or_default(Some(999.0)).is_err(), "above the hard maximum");
        assert!(radius_or_default(Some(f64::NAN)).is_err(), "not a number");
    }
}
