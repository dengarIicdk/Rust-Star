//! # starflight core
//!
//! Portable, engine-agnostic simulation core for the Shooting Star Rust port.
//!
//! This crate contains **no** Minecraft, JNI or platform code. It is pure
//! deterministic math over plain data, which makes it unit-testable on any
//! machine and reusable from a Java adapter, a dedicated server, a test
//! harness, or a future Bedrock/WASM front end.
//!
//! Everything here is an *original* implementation. See
//! `docs/LICENSE_AUDIT.md`: the upstream mod and its library are All Rights
//! Reserved, so no upstream code or resources are used.
//!
//! ## Layers
//!
//! * [`vec3`]      – math primitives.
//! * [`projectile`] – straight-line projectile integration + swept AABB hit test.
//! * [`beam`]      – cylindrical, column-aligned beam volume raster (the
//!   railgun-style "erase a column through the world" mechanic).
//! * [`limits`]    – hard safety limits and tuning validation shared by every
//!   front end (never trust the caller).
//! * [`plan`]      – deterministic, chunk-batched block mutation planning with a
//!   hard per-tick budget.

#![forbid(unsafe_code)]
#![deny(clippy::float_cmp)]

pub mod beam;
pub mod limits;
pub mod plan;
pub mod projectile;
pub mod vec3;

pub use vec3::Vec3;

/// Semantic version of the ABI surface exposed to Java.
///
/// The Java adapter checks this at startup. Bump it whenever the meaning of an
/// exposed function changes, so a stale native library is rejected loudly
/// instead of misbehaving silently.
pub const ABI_VERSION: u32 = 1;

/// Returns the crate version string reported to the Java adapter.
#[must_use]
pub fn version() -> &'static str {
    env!("CARGO_PKG_VERSION")
}

#[cfg(test)]
mod tests {
    #[test]
    fn version_is_not_empty() {
        assert!(!super::version().is_empty());
    }

    #[test]
    fn abi_version_is_positive() {
        // Compile-time check: ABI 0 would mean "unversioned" and must never ship.
        const { assert!(super::ABI_VERSION > 0) };
    }
}
