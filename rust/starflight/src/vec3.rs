//! Minimal 3D vector math.

use core::ops::{Add, Mul, Sub};

/// A 3D point or direction in world space (Minecraft uses `f64` for entities).
#[derive(Debug, Clone, Copy, PartialEq)]
pub struct Vec3 {
    pub x: f64,
    pub y: f64,
    pub z: f64,
}

impl Vec3 {
    pub const ZERO: Self = Self::new(0.0, 0.0, 0.0);

    #[must_use]
    pub const fn new(x: f64, y: f64, z: f64) -> Self {
        Self { x, y, z }
    }

    #[must_use]
    pub fn length_squared(self) -> f64 {
        self.x * self.x + self.y * self.y + self.z * self.z
    }

    #[must_use]
    pub fn length(self) -> f64 {
        self.length_squared().sqrt()
    }

    /// Returns `None` for a zero-length or non-finite vector instead of
    /// silently producing NaN. Callers must handle this: NaN never compares
    /// false-safe against Minecraft's collision checks and would poison state.
    #[must_use]
    pub fn normalize(self) -> Option<Self> {
        let len = self.length();
        if !len.is_finite() || len <= 1e-9 {
            return None;
        }
        Some(Self::new(self.x / len, self.y / len, self.z / len))
    }

    #[must_use]
    pub fn dot(self, other: Self) -> f64 {
        self.x * other.x + self.y * other.y + self.z * other.z
    }

    #[must_use]
    pub fn is_finite(self) -> bool {
        self.x.is_finite() && self.y.is_finite() && self.z.is_finite()
    }

    /// Makes the vector finite and bounded; used at every trust boundary.
    #[must_use]
    pub fn sanitized(self, bound: f64) -> Self {
        fn fix(v: f64, b: f64) -> f64 {
            if v.is_finite() {
                v.clamp(-b, b)
            } else {
                0.0
            }
        }
        Self::new(fix(self.x, bound), fix(self.y, bound), fix(self.z, bound))
    }
}

impl Add for Vec3 {
    type Output = Self;
    fn add(self, rhs: Self) -> Self {
        Self::new(self.x + rhs.x, self.y + rhs.y, self.z + rhs.z)
    }
}

impl Sub for Vec3 {
    type Output = Self;
    fn sub(self, rhs: Self) -> Self {
        Self::new(self.x - rhs.x, self.y - rhs.y, self.z - rhs.z)
    }
}

impl Mul<f64> for Vec3 {
    type Output = Self;
    fn mul(self, rhs: f64) -> Self {
        Self::new(self.x * rhs, self.y * rhs, self.z * rhs)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn normalize_unit_length() {
        let v = Vec3::new(3.0, 4.0, 0.0).normalize().expect("non-zero");
        assert!((v.length() - 1.0).abs() < 1e-12);
    }

    #[test]
    fn normalize_zero_is_none() {
        assert!(Vec3::ZERO.normalize().is_none());
    }

    #[test]
    fn normalize_nan_is_none() {
        assert!(Vec3::new(f64::NAN, 0.0, 0.0).normalize().is_none());
    }

    #[test]
    fn sanitized_clamps_and_kills_nan() {
        let v = Vec3::new(f64::INFINITY, f64::NAN, -1e9).sanitized(100.0);
        assert_eq!(v, Vec3::new(0.0, 0.0, -100.0));
    }

    #[test]
    fn arithmetic() {
        let a = Vec3::new(1.0, 2.0, 3.0);
        assert_eq!(a + a, Vec3::new(2.0, 4.0, 6.0));
        assert_eq!(a - a, Vec3::ZERO);
        assert_eq!(a * 2.0, Vec3::new(2.0, 4.0, 6.0));
        assert!((a.dot(a) - 14.0).abs() < 1e-12);
    }
}
