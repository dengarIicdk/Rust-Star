//! The engine-independent half of Starflight.
//!
//! Nothing here knows about Horizon or Minecraft: positions, a beam's shape, a
//! per-tick budget and the input limits. That is deliberate — the same code is
//! unit-tested on a desktop with `cargo test`, while the game-facing module in
//! the bundle only calls into it.
//!
//! Derived from `rust/starflight` in the same project (independent
//! reimplementation; no upstream code).

#![forbid(unsafe_code)]

pub mod beam;
pub mod limits;
pub mod plan;
pub mod projectile;
pub mod vec3;

pub use beam::{BeamBlocks, BeamSpec};
pub use limits::{
    HARD_MAX_RADIUS, MAX_BLOCKS_PER_STRIKE, MAX_BLOCKS_PER_TICK, MIN_RADIUS,
};
pub use plan::Plan;
pub use vec3::Vec3;

/// The ABI version of the pure core. Bumped when a signature the game module
/// calls changes, so a version mismatch is a compile error, not a subtle bug.
pub const ENGINE_VERSION: u32 = 1;
