//! Testable host for the Horizon runtime.
//!
//! Horizon builds the game by copying module files into `crates/horizon-client`
//! (see `horizon-mixin.toml`), so in the real build this file does not exist and
//! `runtime` becomes `crate::game::starflight`. Here it just re-exports the same
//! code so `cargo test` can run it on its own.

pub mod runtime;

// `tick.rs` is deliberately not declared here. It depends on `crate::game` and
// `log`, which exist only inside Horizon's patched client, so it cannot compile
// in this standalone crate. Kept as a file for review; structurally checked by
// `horizon-mixin.toml` naming it as the injection helper.
