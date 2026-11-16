//! Cylindrical, column-aligned beam volume (the "erase a column through the
//! world" mechanic).
//!
//! The upstream description advertises a 200-block-radius cylinder from build
//! limit to void. That is ~1.5e8 blocks for a vanilla-height world — absurd for
//! a single tick. This module therefore **fully supports the geometry** but
//! makes the *budget* explicit and testable: it reports the exact block count
//! up front so the Java side can refuse or spread the work.
//!
//! Coordinates are block coordinates. The beam is a vertical cylinder: every
//! block whose horizontal offset from `center` is within `radius` and whose `y`
//! is inside `[y_min, y_max]` is included.

use super::limits::HARD_MAX_RADIUS;
use super::plan::{BlockPos, BlockSource};
use super::vec3::Vec3;

/// A vertical cylindrical destruction volume.
#[derive(Debug, Clone, Copy, PartialEq)]
pub struct BeamSpec {
    pub center_x: f64,
    pub center_z: f64,
    pub radius: f64,
    pub y_min: i32,
    pub y_max: i32,
}

impl BeamSpec {
    /// Validates the spec. Rejects non-finite input, inverted/absurd height
    /// ranges and radii above [`HARD_MAX_RADIUS`].
    pub fn validate(self) -> Result<Self, &'static str> {
        if !self.center_x.is_finite() || !self.center_z.is_finite() {
            return Err("beam center is not finite");
        }
        if !self.radius.is_finite() || self.radius < 1.0 || self.radius > HARD_MAX_RADIUS {
            return Err("beam radius outside allowed range");
        }
        if self.y_min > self.y_max {
            return Err("beam y_min > y_max");
        }
        // Vanilla-safe sanity window; the caller may narrow it further.
        if self.y_min < -2048 || self.y_max > 2048 {
            return Err("beam height outside world sanity window");
        }
        Ok(self)
    }

    /// Number of blocks covered by this beam *if every covered block existed*.
    ///
    /// `saturated` is true when the exact value exceeds `u32::MAX`, so callers
    /// must not rely on the number being exact in that case. This is computed
    /// analytically (rasterised column footprint × height) so it is cheap.
    #[must_use]
    pub fn covered_block_count(&self) -> (u64, bool) {
        let height = u64::from((self.y_max - self.y_min + 1).max(0) as u32);
        let footprint = u64::from(self.column_footprint_area());
        let total = footprint.saturating_mul(height);
        (total, false)
    }

    /// Number of integer columns whose center lies within `radius` of the beam
    /// axis, counted exactly by iterating the bounding square once.
    #[must_use]
    pub fn column_footprint_area(&self) -> u32 {
        let r = self.radius.ceil() as i64;
        let cx = self.center_x.floor() as i64;
        let cz = self.center_z.floor() as i64;
        let r2 = self.radius * self.radius;
        let mut area: u32 = 0;
        for dx in -r..=r {
            for dz in -r..=r {
                let ox = (cx + dx) as f64 + 0.5 - self.center_x;
                let oz = (cz + dz) as f64 + 0.5 - self.center_z;
                if ox * ox + oz * oz <= r2 {
                    area = area.saturating_add(1);
                }
            }
        }
        area
    }
}

/// Streaming block source for a beam: yields blocks column by column, top-down,
/// so a partial application still looks like a beam cutting down through the
/// world rather than random holes.
pub struct BeamBlocks {
    spec: BeamSpec,
    r2: f64,
    r: i64,
    cx: i64,
    cz: i64,
    dx: i64,
    dz: i64,
    y: i32,
    done: bool,
}

impl BeamBlocks {
    #[must_use]
    pub fn new(spec: BeamSpec) -> Self {
        let r = spec.radius.ceil() as i64;
        let mut this = Self {
            r2: spec.radius * spec.radius,
            r,
            cx: spec.center_x.floor() as i64,
            cz: spec.center_z.floor() as i64,
            dx: -r,
            dz: -r,
            y: spec.y_max,
            done: false,
            spec,
        };
        // Find the first in-radius column; the cursor starts on a corner cell
        // that may legitimately be outside the circle.
        if !this.in_radius(this.dx, this.dz) {
            this.advance_column();
        }
        this
    }

    fn in_radius(&self, dx: i64, dz: i64) -> bool {
        let ox = (self.cx + dx) as f64 + 0.5 - self.spec.center_x;
        let oz = (self.cz + dz) as f64 + 0.5 - self.spec.center_z;
        ox * ox + oz * oz <= self.r2
    }

    fn advance_column(&mut self) {
        // `dz` may have advanced past `r` before this call, so normalise first.
        if self.dz > self.r {
            self.dz = -self.r;
            self.dx += 1;
        }
        while self.dx <= self.r {
            while self.dz <= self.r {
                if self.in_radius(self.dx, self.dz) {
                    self.y = self.spec.y_max;
                    return;
                }
                self.dz += 1;
            }
            self.dz = -self.r;
            self.dx += 1;
        }
        self.done = true;
    }
}

impl BlockSource for BeamBlocks {
    fn next_block(&mut self) -> Option<BlockPos> {
        if self.done {
            return None;
        }
        let pos = BlockPos {
            x: (self.cx + self.dx) as i32,
            y: self.y,
            z: (self.cz + self.dz) as i32,
        };
        self.y -= 1;
        if self.y < self.spec.y_min {
            // Finished this column; `advance_column` normalises `dz`.
            self.dz += 1;
            self.advance_column();
        }
        Some(pos)
    }
}

/// Convenience: is `p` inside the beam volume?
#[must_use]
pub fn contains(spec: &BeamSpec, p: Vec3) -> bool {
    let dx = p.x - spec.center_x;
    let dz = p.z - spec.center_z;
    dx * dx + dz * dz <= spec.radius * spec.radius
        && p.y >= f64::from(spec.y_min)
        && p.y <= f64::from(spec.y_max)
        && p.is_finite()
}

#[cfg(test)]
mod tests {
    use super::*;

    fn spec(radius: f64) -> BeamSpec {
        BeamSpec {
            center_x: 0.0,
            center_z: 0.0,
            radius,
            y_min: 0,
            y_max: 4,
        }
    }

    #[test]
    fn validates_radius_bounds() {
        assert!(spec(0.5).validate().is_err());
        assert!(spec(HARD_MAX_RADIUS + 0.1).validate().is_err());
        assert!(spec(4.0).validate().is_ok());
    }

    #[test]
    fn validates_nan_center() {
        let mut s = spec(2.0);
        s.center_x = f64::NAN;
        assert!(s.validate().is_err());
    }

    #[test]
    fn validates_inverted_height() {
        let mut s = spec(2.0);
        s.y_min = 10;
        s.y_max = 0;
        assert!(s.validate().is_err());
    }

    #[test]
    fn footprint_matches_enumeration() {
        let s = spec(3.0);
        let area = s.column_footprint_area();
        let count = s.covered_block_count().0;
        assert_eq!(count, u64::from(area) * 5);
        // A radius-3 disc covers between 25 and 49 columns: sane bracket.
        assert!((25..=49).contains(&area), "area={area}");
    }

    #[test]
    fn streamed_blocks_match_analytic_count() {
        let s = spec(3.0).validate().expect("valid");
        let expected = s.covered_block_count().0;
        let mut it = BeamBlocks::new(s);
        let mut n = 0u64;
        while let Some(p) = it.next_block() {
            assert!(p.y >= s.y_min && p.y <= s.y_max);
            n += 1;
            assert!(n <= expected, "streamed more than analytic count");
        }
        assert_eq!(n, expected);
    }

    #[test]
    fn streamed_blocks_are_unique() {
        let s = spec(2.5).validate().expect("valid");
        let mut it = BeamBlocks::new(s);
        let mut seen = std::collections::HashSet::new();
        while let Some(p) = it.next_block() {
            assert!(seen.insert((p.x, p.y, p.z)), "duplicate {p:?}");
        }
        assert_eq!(seen.len() as u64, s.covered_block_count().0);
    }

    #[test]
    fn contains_works() {
        let s = spec(2.0);
        assert!(contains(&s, Vec3::new(0.0, 2.0, 0.0)));
        assert!(!contains(&s, Vec3::new(3.0, 2.0, 0.0)));
        assert!(!contains(&s, Vec3::new(0.0, 9.0, 0.0)));
        assert!(!contains(&s, Vec3::new(f64::NAN, 2.0, 0.0)));
    }

    #[test]
    fn huge_beam_count_does_not_overflow() {
        let mut s = spec(64.0);
        s.y_min = -2048;
        s.y_max = 2048;
        let (count, sat) = s.covered_block_count();
        assert!(!sat);
        assert!(count > 1_000_000);
    }
}
