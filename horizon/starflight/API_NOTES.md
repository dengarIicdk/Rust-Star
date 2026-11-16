# Verified Horizon API facts

Researched 2026-11-16 from `horizon-rust/horizon@main`. These are the facts the
port is built on; each was read from the repository, not assumed.

## Mod kinds

* Java mods cannot run in Horizon. Its docs state the mod would have to patch
  Minecraft's Java classes, which Horizon does not have.
* Content mod (`.hmod`, API 0.2): data and assets only — blocks, items,
  recipes, loot, tags, models, textures. No code.
* Native mod (`.hnmod`): a Rust bundle built into the game. Needed for anything
  that behaves.

## A native mod's crate

* Depends on `horizon-mod-api` (lib name `mod_api`) via
  `.horizon/sdk/api/crates/horizon-mod-api`, never on the game directly.
* `Cargo.toml` + `src/` form the mod's crate; `#![forbid(unsafe_code)]` is
  compatible with everything this port does.
* Registry dependencies must be pinned in a launcher-generated `Cargo.lock`
  (`horizon-launcher native-lock`). The core crate is a local path dependency,
  which is traversed without registry resolution.

## Hooks

```rust
#[mod_api::horizon_mod]
impl Hooks for YourMod {
    fn some_hook(&self, host: &mut dyn ServerHost, event: &mut SomeEvent<'_>) {
        // ...
    }
}
```

* 405 native hook points exist — far more than the capability table in
  `docs/mods.md` implies.
* Relevant to this port: `block_use_item_on`, `use_item_hand`,
  `right_click_item`, `client_tick_post`, `server_about_to_start`.
* Return semantics: listen (notify only), cancellable (`Flow::Cancel`),
  replacement (`Option`, first `Some` wins), collection (append to a list).
* `#[listener(priority = N)]` controls ordering; higher runs first.

## World writes

From `docs/modding/src/how-to/world/bulk-block-edits.md`:

* `BlockEdit` carries a `palette` of complete `BlockState` values and `filters`
  of `StatePredicate`.
* Everything is prepared all-or-nothing **before** any mutation.
* At most 65 536 filters per edit.
* Unloaded chunks and out-of-height portions of runs are skipped — the caller
  does not have to guard for them.
* `BlockState::of(&host.block_states(), block, constraints)` constructs a state;
  `state.with(...)` changes one.
* Edits preserve strict block-entity behaviour, notify no neighbours, and send
  whole changed chunks.

## Effects

* `LevelRef::level_event(host, id, pos, data)` — vanilla numbered effect, heard
  by players within 64 blocks.
* `LevelRef::global_level_event(host, id, pos, data)` — every player, any
  distance, any level.

## Manifest

```toml
id = "starflight"
api = "0.2"
side = "both"
capabilities = ["registries", "world-write"]
```

⚠️ `registries` is confirmed. The **world-write capability name is not yet
confirmed** against the SDK; the registry checks reject an unknown one, so this
must be verified before submission.

## Still unknown, and why it blocks the hooks

The generated `mod_api` surface (exact field names on `RightClickItemEvent`,
`ServerHost` methods, `BlockEdit` construction) is produced per Horizon build by
`horizon-mod-api-gen`. It is not published as readable source. Writing hook code
without it would be guesswork that never compiles. `cargo horizon-mod` would
resolve it, but installing that toolchain fails here:
`cc-rs: failed to find tool "gcc.exe"`.
