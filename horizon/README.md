# Horizon port — status

Port of the Fabric mod in `mods/starflight` to **Horizon** (the Rust Minecraft
client). Both are independent reimplementations of the mechanics described by
*The Shooting Star [Demo]* (All Rights Reserved); no upstream code or assets are
used in either.

Last checked: 2026-11-16 against `horizon-rust/horizon` `main`.

## Where it stands

| Piece | State |
|---|---|
| Verified API facts the port depends on | ✅ researched and recorded below |
| Game-independent core (`rust/starflight`) | ✅ reusable unchanged, 38 tests |
| Strike scheduling over that core | ✅ **6 tests pass**, linted, end-to-end drain proven |
| `horizon-mod.toml` manifest | ✅ written (needs the real capability list confirming) |
| Hook implementations reaching the game | ⛔ **blocked** — see below |

## The blocker, precisely

Horizon builds native mods through `cargo-horizon-mod`, which the launcher runs
against a pinned SDK. On this machine installing it fails:

```
error occurred in cc-rs: failed to find tool "gcc.exe": program not found
```

There is no C compiler here — the Rust target is `x86_64-pc-windows-gnu` and no
`gcc`/`msvc` is present. The same limitation already forced a workaround for Ogg
encoding (`docs/KNOWN_ISSUES.md`). Without the SDK, `horizon-mod-api` cannot be
resolved, so any hook code written now would sit permanently unverified at the
API boundary — and the exact field names of events like `RightClickItemEvent`
are *generated* per Horizon build, not published as readable source.

Writing that guesswork would mean presenting code that has never compiled as
progress. This port does not do that.

## Verified API facts (the research that makes the port tractable)

* **Java mods cannot run in Horizon** — its docs state the mod would have to
  patch Minecraft's Java classes, which Horizon does not have.
* Hooks are methods of `mod_api::Hooks`, implemented in
  `#[mod_api::horizon_mod] impl Hooks for YourMod`, each taking
  `(&self, host, event)`. No hand-written Mixins.
* A native mod is a crate depending on `horizon-mod-api` (lib `mod_api`) through
  `.horizon/sdk/...`, never on the game directly.
* **405 native hook points exist** — far more than the capability table in
  `docs/mods.md` suggests. `block_use_item_on`, `use_item_hand`,
  `right_click_item` and `client_tick_post` are all present.
* **Bulk block edits exist** (`docs/modding/src/how-to/world/bulk-block-edits.md`):
  `BlockEdit` with a `BlockState` palette and `StatePredicate` filters, prepared
  all-or-nothing before any mutation, ≤65 536 filters, skipping unloaded chunks
  and out-of-height positions. This is exactly what a strike needs — and it is
  the part the Fabric build had to implement itself.
* `BlockState::of(&host.block_states(), block, constraints)` builds a state;
  `LevelRef::level_event` / `global_level_event` play the impact effects.

## What transfers unchanged

The core in `rust/starflight`: beam geometry, per-tick budget, flight,
validation. `src/strike.rs` drives it and is fully tested here:

```
queued a radius-4 strike at (0, 0): 572 positions
drained 572 positions over 30 ticks
ok: every position delivered exactly once
```

Tests: descent delay honoured, every position delivered exactly once, the
per-tick budget never exceeded, absurd radius refused not clamped, non-finite
centre refused, inverted height range refused.

## Next step on a machine with a C toolchain

```sh
cargo install --git https://github.com/horizon-rust/horizon cargo-horizon-mod
cd horizon/starflight
cargo horizon-mod new . --template native   # if starting fresh
cargo horizon-mod run
```

Then wire `strike::Strike` into the real hooks. The Fabric implementation in
`mods/starflight/src/main/java/dev/starport/` is the behavioural reference for
what each hook must do: right-click → mark the target column → descend → hand
`BlockEdit` a budget's worth of positions per tick → play sound and particles.

## Manifest caveat

`horizon-mod.toml` lists `capabilities = ["registries", "world-write"]`. The
exact capability name for world edits must be confirmed against the SDK before
submission; the registry checks will reject an unknown one.
