//! Hard safety limits enforced in the shared core.
//!
//! Rationale: every number that reaches Rust from a client (skill sliders,
//! packet payloads, commands) is untrusted. The upstream description mentions
//! radii up to 1500 blocks; a naive implementation of that is a trivially
//! exploitable lag/ griefing vector. All limits live here so the Java adapter
//! and the regression tests agree on exactly one source of truth.

/// Upstream-style slider maximum for a beam radius. We keep the *option* but
/// never let a single request exceed what the server budget allows; see
/// [`Tuning::validate`].
pub const MAX_RADIUS_SLIDER: f64 = 1500.0;

/// Smallest useful radius.
pub const MIN_RADIUS: f64 = 1.0;

/// Absolute ceiling on beam radius accepted by the core, regardless of config.
/// Deliberately far below the 1500 slider so a malicious or misconfigured
/// client cannot OOM the server. Raising this is a code change, not a packet.
/// Raised from 64 to 128 to match the scale of an orbital strike: at radius
/// 128 a full-height beam is ~19.8 M blocks, which takes about a minute of
/// server time at [`MAX_BLOCKS_PER_TICK`] and roughly 240 MB in the plan. That
/// is the documented ceiling; going higher needs a streaming plan, not a
/// bigger constant (see docs/KNOWN_ISSUES.md).
pub const HARD_MAX_RADIUS: f64 = 128.0;

/// Maximum blocks a single tick may mutate through the planner.
///
/// Raised from 16 384 to 65 536 so a radius-128 strike (~19.8 M blocks) lands in
/// roughly 30 s of server time instead of ~2 min. 65 536 block writes in one
/// tick is a large but survivable burst on a modern machine; Minecraft's own
/// `/fill` is capped at 32 768 per command, so this stays in the same order.
pub const MAX_BLOCKS_PER_TICK: u32 = 65_536;

/// Maximum total blocks a single queued strike may request.
/// Raised alongside `HARD_MAX_RADIUS`: a radius-128 beam through the full world
/// height is ~19.8 M positions, so the old 2 M cap rejected the very strike the
/// radius increase was meant to allow. 24 M leaves headroom for a tall world
/// without permitting an unbounded request.
pub const MAX_BLOCKS_PER_STRIKE: u32 = 24_000_000;

/// Maximum projectile speed in blocks per tick.
pub const MAX_PROJECTILE_SPEED: f64 = 10.0;

/// Maximum projectile lifetime in ticks (10 minutes at 20 TPS).
pub const MAX_PROJECTILE_LIFETIME: u32 = 12_000;

/// Maximum damage a single hit may apply.
pub const MAX_DAMAGE: f64 = 1000.0;

/// The tuning a caller may supply. Validated once, then treated as immutable.
#[derive(Debug, Clone, Copy, PartialEq)]
pub struct Tuning {
    pub radius: f64,
    pub damage: f64,
    pub speed: f64,
    pub lifetime_ticks: u32,
}

impl Default for Tuning {
    fn default() -> Self {
        Self {
            radius: 8.0,
            damage: 60.0,
            speed: 2.5,
            lifetime_ticks: 400,
        }
    }
}

/// Why a tuning value was rejected. Returned as a `&'static str` to Java so no
/// allocation crosses the FFI boundary.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum TuningError {
    RadiusOutOfRange,
    DamageOutOfRange,
    SpeedOutOfRange,
    LifetimeOutOfRange,
}

impl TuningError {
    #[must_use]
    pub const fn message(self) -> &'static str {
        match self {
            Self::RadiusOutOfRange => "radius outside [1, HARD_MAX_RADIUS]",
            Self::DamageOutOfRange => "damage outside [0, MAX_DAMAGE]",
            Self::SpeedOutOfRange => "speed outside (0, MAX_PROJECTILE_SPEED]",
            Self::LifetimeOutOfRange => "lifetime outside [1, MAX_PROJECTILE_LIFETIME]",
        }
    }
}

impl Tuning {
    /// Clamps every field into the legal range. NaN becomes the default value
    /// rather than propagating.
    #[must_use]
    pub fn clamped(self) -> Self {
        let d = Self::default();
        Self {
            radius: clamp_or(self.radius, d.radius, MIN_RADIUS, HARD_MAX_RADIUS),
            damage: clamp_or(self.damage, d.damage, 0.0, MAX_DAMAGE),
            speed: clamp_or(self.speed, d.speed, 0.05, MAX_PROJECTILE_SPEED),
            lifetime_ticks: self.lifetime_ticks.clamp(1, MAX_PROJECTILE_LIFETIME),
        }
    }

    /// Strict validation. Non-finite input is an error, not a silent clamp.
    pub fn validate(self) -> Result<Self, TuningError> {
        if !in_range(self.radius, MIN_RADIUS, HARD_MAX_RADIUS) {
            return Err(TuningError::RadiusOutOfRange);
        }
        if !in_range(self.damage, 0.0, MAX_DAMAGE) {
            return Err(TuningError::DamageOutOfRange);
        }
        if !in_range(self.speed, f64::MIN_POSITIVE, MAX_PROJECTILE_SPEED) {
            return Err(TuningError::SpeedOutOfRange);
        }
        if self.lifetime_ticks == 0 || self.lifetime_ticks > MAX_PROJECTILE_LIFETIME {
            return Err(TuningError::LifetimeOutOfRange);
        }
        Ok(self)
    }
}

fn in_range(v: f64, lo: f64, hi: f64) -> bool {
    v.is_finite() && v >= lo && v <= hi
}

fn clamp_or(v: f64, fallback: f64, lo: f64, hi: f64) -> f64 {
    if v.is_finite() {
        v.clamp(lo, hi)
    } else {
        fallback
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn default_is_valid() {
        assert!(Tuning::default().validate().is_ok());
    }

    #[test]
    fn huge_radius_is_rejected_even_if_slider_allows() {
        let t = Tuning {
            radius: MAX_RADIUS_SLIDER,
            ..Tuning::default()
        };
        assert_eq!(t.validate(), Err(TuningError::RadiusOutOfRange));
    }

    #[test]
    fn nan_is_rejected() {
        let t = Tuning {
            damage: f64::NAN,
            ..Tuning::default()
        };
        assert_eq!(t.validate(), Err(TuningError::DamageOutOfRange));
    }

    #[test]
    fn infinite_speed_is_rejected() {
        let t = Tuning {
            speed: f64::INFINITY,
            ..Tuning::default()
        };
        assert_eq!(t.validate(), Err(TuningError::SpeedOutOfRange));
    }

    #[test]
    fn zero_lifetime_is_rejected() {
        let t = Tuning {
            lifetime_ticks: 0,
            ..Tuning::default()
        };
        assert_eq!(t.validate(), Err(TuningError::LifetimeOutOfRange));
    }

    #[test]
    fn clamped_repairs_garbage() {
        let t = Tuning {
            radius: f64::NAN,
            damage: 1e9,
            speed: -3.0,
            lifetime_ticks: u32::MAX,
        }
        .clamped();
        assert_eq!(t.radius, Tuning::default().radius);
        assert_eq!(t.damage, MAX_DAMAGE);
        assert_eq!(t.speed, 0.05);
        assert_eq!(t.lifetime_ticks, MAX_PROJECTILE_LIFETIME);
        assert!(t.validate().is_ok());
    }

    #[test]
    fn error_messages_are_non_empty() {
        for e in [
            TuningError::RadiusOutOfRange,
            TuningError::DamageOutOfRange,
            TuningError::SpeedOutOfRange,
            TuningError::LifetimeOutOfRange,
        ] {
            assert!(!e.message().is_empty());
        }
    }
}
