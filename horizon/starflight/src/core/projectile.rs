//! Straight-line projectile integration with swept AABB collision.
//!
//! The simulation is intentionally stateless between calls: the caller owns the
//! authoritative position/velocity (it lives in the Minecraft entity) and calls
//! [`Projectile::step`] once per tick. That keeps the JNI boundary small and
//! makes rollback/replay trivial to test.

use super::limits::Tuning;
use super::vec3::Vec3;

/// Outcome of one simulation tick.
#[derive(Debug, Clone, Copy, PartialEq)]
pub enum Step {
    /// Still flying; new position and remaining lifetime.
    Flying { pos: Vec3, age: u32 },
    /// Hit an AABB at `pos` after travelling `fraction` of the step.
    Hit {
        pos: Vec3,
        fraction: f64,
        target_id: i32,
        damage: f64,
    },
    /// Ran out of lifetime or left the world bounds.
    Expired { pos: Vec3, reason: ExpiryReason },
}

/// Why a projectile stopped.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum ExpiryReason {
    Lifetime,
    OutOfWorld,
}

/// An axis-aligned bounding box, as used by Minecraft entities.
#[derive(Debug, Clone, Copy, PartialEq)]
pub struct Aabb {
    pub min: Vec3,
    pub max: Vec3,
}

impl Aabb {
    #[must_use]
    pub fn new(min: Vec3, max: Vec3) -> Self {
        Self { min, max }
    }

    /// Builds a box from a center and half-extents (the common entity case).
    #[must_use]
    pub fn from_center(center: Vec3, half_x: f64, half_y: f64, half_z: f64) -> Self {
        let h = Vec3::new(half_x, half_y, half_z);
        Self::new(center - h, center + h)
    }

    #[must_use]
    pub fn contains(&self, p: Vec3) -> bool {
        p.x >= self.min.x
            && p.x <= self.max.x
            && p.y >= self.min.y
            && p.y <= self.max.y
            && p.z >= self.min.z
            && p.z <= self.max.z
    }

    /// Slab test against the segment `from → from + delta`.
    ///
    /// Returns the entry fraction in `[0, 1]`, or `None` if the segment misses.
    /// A segment starting inside the box returns `0.0`.
    #[must_use]
    pub fn ray_fraction(&self, from: Vec3, delta: Vec3) -> Option<f64> {
        if self.contains(from) {
            return Some(0.0);
        }
        let mut t_min = 0.0_f64;
        let mut t_max = 1.0_f64;

        // Each axis: clip [t_min, t_max] against the slab.
        let axes = [
            (from.x, delta.x, self.min.x, self.max.x),
            (from.y, delta.y, self.min.y, self.max.y),
            (from.z, delta.z, self.min.z, self.max.z),
        ];
        for (o, d, lo, hi) in axes {
            if d.abs() < 1e-12 {
                // Parallel to this slab: must already be inside it.
                if o < lo || o > hi {
                    return None;
                }
                continue;
            }
            let inv = 1.0 / d;
            let mut t1 = (lo - o) * inv;
            let mut t2 = (hi - o) * inv;
            if t1 > t2 {
                core::mem::swap(&mut t1, &mut t2);
            }
            t_min = t_min.max(t1);
            t_max = t_max.min(t2);
            if t_min > t_max {
                return None;
            }
        }
        if t_min > 1.0 || t_max < 0.0 {
            None
        } else {
            Some(t_min.clamp(0.0, 1.0))
        }
    }
}

/// An entity the projectile may hit.
#[derive(Debug, Clone, Copy, PartialEq)]
pub struct Target {
    pub id: i32,
    pub bounds: Aabb,
    /// Free-form faction/mask: the Java side decides which targets this
    /// projectile is allowed to hit (e.g. skip the shooter and their pets).
    pub hostile: bool,
}

/// One projectile's per-tick parameters.
#[derive(Debug, Clone, Copy)]
pub struct Projectile {
    pub pos: Vec3,
    pub velocity: Vec3,
    pub age: u32,
    pub tuning: Tuning,
    /// World limits: projectiles beyond them are expired.
    pub world_min_y: f64,
    pub world_max_y: f64,
    pub world_radius: f64,
}

impl Projectile {
    /// Creates a validated projectile. Returns `None` for invalid tuning or a
    /// non-finite direction so the caller cannot smuggle NaN into the world.
    #[must_use]
    pub fn new(pos: Vec3, direction: Vec3, tuning: Tuning) -> Option<Self> {
        let tuning = tuning.validate().ok()?;
        let dir = direction.normalize()?;
        Some(Self {
            pos: pos.sanitized(3.0e7),
            velocity: dir * tuning.speed,
            age: 0,
            tuning,
            world_min_y: -256.0,
            world_max_y: 512.0,
            world_radius: 3.0e7,
        })
    }

    /// Advances one tick and resolves collision against `targets`.
    ///
    /// Picks the earliest hit; ties are broken by lowest entity id so results
    /// are deterministic and reproducible across client/server.
    #[must_use]
    pub fn step(&self, targets: &[Target]) -> Step {
        let delta = self.velocity;
        let next = self.pos + delta;

        let mut best: Option<(f64, i32)> = None;
        for t in targets {
            if !t.hostile {
                continue;
            }
            if let Some(f) = t.bounds.ray_fraction(self.pos, delta) {
                let better = match best {
                    None => true,
                    Some((bf, bid)) => {
                        // Compare with a tolerance so ties resolve by id rather
                        // than by floating-point noise from different machines.
                        // 1e-9 blocks is far below any gameplay-relevant
                        // distance and well above f64 round-off here.
                        const EPS: f64 = 1e-9;
                        if (f - bf).abs() <= EPS {
                            t.id < bid
                        } else {
                            f < bf
                        }
                    }
                };
                if better {
                    best = Some((f, t.id));
                }
            }
        }

        if let Some((fraction, target_id)) = best {
            return Step::Hit {
                pos: self.pos + delta * fraction,
                fraction,
                target_id,
                damage: self.tuning.damage,
            };
        }

        if self.age + 1 >= self.tuning.lifetime_ticks {
            return Step::Expired {
                pos: next,
                reason: ExpiryReason::Lifetime,
            };
        }
        if !next.is_finite()
            || next.y < self.world_min_y
            || next.y > self.world_max_y
            || next.length_squared() > self.world_radius * self.world_radius
        {
            return Step::Expired {
                pos: next,
                reason: ExpiryReason::OutOfWorld,
            };
        }

        Step::Flying {
            pos: next,
            age: self.age + 1,
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn tgt(id: i32, x: f64) -> Target {
        Target {
            id,
            bounds: Aabb::from_center(Vec3::new(x, 64.0, 0.0), 0.5, 0.5, 0.5),
            hostile: true,
        }
    }

    #[test]
    fn flies_straight_when_nothing_ahead() {
        let p = Projectile::new(
            Vec3::new(0.0, 64.0, 0.0),
            Vec3::new(1.0, 0.0, 0.0),
            Tuning::default(),
        )
        .expect("valid");
        match p.step(&[]) {
            Step::Flying { pos, age } => {
                assert!((pos.x - p.tuning.speed).abs() < 1e-9);
                assert_eq!(age, 1);
            }
            other => panic!("expected Flying, got {other:?}"),
        }
    }

    #[test]
    fn hits_nearest_target() {
        let p = Projectile::new(
            Vec3::new(0.0, 64.0, 0.0),
            Vec3::new(1.0, 0.0, 0.0),
            Tuning::default(),
        )
        .expect("valid");
        let step = p.step(&[tgt(2, 2.0), tgt(1, 1.0)]);
        match step {
            Step::Hit {
                target_id, damage, ..
            } => {
                assert_eq!(target_id, 1);
                assert!((damage - p.tuning.damage).abs() < 1e-9);
            }
            other => panic!("expected Hit, got {other:?}"),
        }
    }

    #[test]
    fn ignores_non_hostile() {
        let p = Projectile::new(
            Vec3::new(0.0, 64.0, 0.0),
            Vec3::new(1.0, 0.0, 0.0),
            Tuning::default(),
        )
        .expect("valid");
        let mut t = tgt(1, 1.0);
        t.hostile = false;
        assert!(matches!(p.step(&[t]), Step::Flying { .. }));
    }

    #[test]
    fn expires_on_lifetime() {
        let mut p = Projectile::new(
            Vec3::new(0.0, 64.0, 0.0),
            Vec3::new(0.0, 0.0, 1.0),
            Tuning::default(),
        )
        .expect("valid");
        p.age = p.tuning.lifetime_ticks - 1;
        match p.step(&[]) {
            Step::Expired { reason, .. } => assert_eq!(reason, ExpiryReason::Lifetime),
            other => panic!("expected Expired, got {other:?}"),
        }
    }

    #[test]
    fn expires_below_world() {
        let mut p = Projectile::new(
            Vec3::new(0.0, -255.0, 0.0),
            Vec3::new(0.0, -1.0, 0.0),
            Tuning::default(),
        )
        .expect("valid");
        p.world_min_y = -256.0;
        match p.step(&[]) {
            Step::Expired { reason, .. } => assert_eq!(reason, ExpiryReason::OutOfWorld),
            other => panic!("expected Expired, got {other:?}"),
        }
    }

    #[test]
    fn rejects_zero_direction() {
        assert!(Projectile::new(Vec3::ZERO, Vec3::ZERO, Tuning::default()).is_none());
    }

    #[test]
    fn ray_hits_box_center_line() {
        let b = Aabb::from_center(Vec3::new(5.0, 0.0, 0.0), 1.0, 1.0, 1.0);
        let f = b
            .ray_fraction(Vec3::ZERO, Vec3::new(10.0, 0.0, 0.0))
            .expect("hit");
        assert!((f - 0.4).abs() < 1e-9);
    }

    #[test]
    fn ray_misses_parallel_offset() {
        let b = Aabb::from_center(Vec3::new(5.0, 0.0, 0.0), 1.0, 1.0, 1.0);
        assert!(b
            .ray_fraction(Vec3::ZERO, Vec3::new(10.0, 5.0, 0.0))
            .is_none());
    }

    #[test]
    fn ray_starting_inside_is_zero() {
        let b = Aabb::from_center(Vec3::ZERO, 1.0, 1.0, 1.0);
        assert_eq!(
            b.ray_fraction(Vec3::ZERO, Vec3::new(1.0, 0.0, 0.0)),
            Some(0.0)
        );
    }

    #[test]
    fn deterministic_ties_choose_lowest_id() {
        let p = Projectile::new(
            Vec3::new(0.0, 64.0, 0.0),
            Vec3::new(1.0, 0.0, 0.0),
            Tuning::default(),
        )
        .expect("valid");
        match p.step(&[tgt(9, 1.0), tgt(3, 1.0)]) {
            Step::Hit { target_id, .. } => assert_eq!(target_id, 3),
            other => panic!("expected Hit, got {other:?}"),
        }
    }
}
