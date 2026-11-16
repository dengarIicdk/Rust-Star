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

---

## Update 2026-11-16: the blocker is gone

Installing MinGW-w64 (`winget install BrechtSanders.WinLibs.POSIX.UCRT`) fixed
`cc-rs: failed to find tool "gcc.exe"`, so `cargo-horizon-mod` installs and the
generated API is readable on disk. Everything below is now confirmed by reading
`crates/horizon-mod-vocab/src/hooks/*.rs`,
`crates/horizon-mod-vocab/src/host/*.rs` and
`target/debug/build/horizon-mod-vocab/*/out/mod_api_generated{,_host}.rs`.

### Signatures that were guesses before

```rust
fn right_click_item(&self, host: HostSide<'_>, event: &mut RightClickItemEvent<'_>) -> Flow;
fn server_tick(&self, host: &mut dyn ServerHost, event: &mut ServerTickEvent<'_>);
```

* Hooks take **`&self`**, not `&mut self`, and the mod must be `Sync` — hence a
  `Mutex` for the live strikes rather than plain state.
* `HostSide` is **generic** (`HostSide<'_>`) with `Server`/`Client` variants,
  not a plain enum name.
* `RightClickItemEvent` fields: `click: &ItemClick`, `cancellation_result`,
  `cancelled: bool`.
* `ItemClick` fields: `side: LogicalSide`, `dimension`, `pos: [i32; 3]`,
  `hand`, `stack: &ItemStack`, `player: u128`, `player_entity_id: i32`.
* `ItemKey` is **opaque** (`pub struct ItemKey(pub(crate) u32)`) — it cannot be
  compared to a string. Resolve with `host.item_key(&Identifier)`;
  `Identifier::parse("ns:path")` (there is no `Identifier::new`).
* `ServerHost` already includes `WorldEditServerHost`, so `host.edit_blocks`
  is called directly, no downcast.
* `BlockEdit { palette: Vec<BlockState>, filters: Vec<StatePredicate>, runs: Vec<BlockRun> }`;
  `BlockRun { x, z, y, height, surface, block, only }` — note `x, z, y` order.
* `block_state_id(&str) -> Option<BlockState>` resolves a block directly.

### Capabilities

`capabilities = ["registries"]` is the **only** accepted value; `"world-write"`
is rejected as an unknown variant. World writes are gated by
`#[access(WorldOnly)]` on the host method, not by a manifest capability.

## Content declarations in code (added after the item work)

Items need not be JSON: the `items` hook declares them in code.

```rust
fn items(&self, _context: &RegistrationContext, event: &mut ItemsEvent<'_>) {
    event.items.push(Item::new("orbital_rocket", "minecraft:firework_rocket"));
}
```

* `ItemsEvent { items: &mut Vec<mod_api::Item> }`, context is
  `&RegistrationContext`. Asked once at start, both sides.
* `Item { id, template, max_stack_size, rarity, components, crafting_remainder,
  block, arm_pose }`. `id` is bare; it uses the manifest's mod id as namespace.
* `RegistrationContext` cannot be constructed outside the game (`new` is
  `pub(crate)`), so declaration content is kept in a free function and unit
  tested separately from the hook.

### Commands

```rust
fn register_commands(&self, _host: &mut dyn ServerHost, event: &mut RegisterCommandsEvent<'_>);
fn run_mod_command(&self, _host: &mut dyn ServerHost, event: &mut RunModCommandEvent<'_>) -> Flow;
```

* `Command::new(usage)` is level 0; `Command::op(usage, level)` sets the operator
  level (`level()` as a builder method does **not** exist).
* `RunModCommandEvent { source, command, cancelled }`;
  `CommandInvocation { literals, arguments, dimension, position: [f64; 3] }`.
* Arguments are read by name: `event.command.argument("radius")` →
  `Option<&CommandArgumentValue>`, an enum with `String`, `Integer(i32)`,
  `Number(f64)`, `Boolean`, `Player`, `Entity`, …

### Running

`cargo horizon-mod run` refuses native mods: they need
`horizon-launcher native-build <path> --trust-native-code`. No launcher or client
binary is published (the `launcher` and `sdk-*` release URLs 404), so the mod
cannot be launched from this machine.

## Second round of corrections (while making it actually compile and test)

These were all found by compiling the hooks and running the tests, not by
reading alone.

1. **A command's level and a click's level have different types.**
   `CommandInvocation::dimension` is a `DimensionKey` (opaque `u32` slot); a
   click's `ItemClick::dimension` is a `LevelDimension`, i.e.
   `horizon_core::dimension::Dimension`, an enum
   (`Overworld | Nether | End | Custom(LevelKey)`). One helper per type.
   `DimensionKey` has public `OVERWORLD`/`NETHER`/`END` constants and is
   `PartialEq`, which is enough to name the three vanilla levels.

2. **`LevelsHost::dimension_key_id` is not reachable from `dyn ServerHost`.**
   It is declared in `host/levels.rs`, and `host::levels::LevelsHost` *is* a
   supertrait of `Host`, but the method is not emitted into
   `mod_api_generated_host.rs` and `mod_api::prelude` does not bring the trait
   into scope for a `&mut dyn ServerHost`. `cargo build` resolves it
   inconsistently with `cargo test`, so the code avoids it entirely.

3. **`ItemPlace` is crate-private.** `crates/horizon-mod-vocab/src/block/mod.rs`
   declares `pub(crate) mod item_place;`, and no generated host method sets a
   player's slot. `PlayersServerHost` has no inventory write. So a native mod
   cannot consume an item from a player's hand in this SDK. The rocket is
   therefore not consumed; README says so.

4. **`horizon-mod-package` is the launcher's validator.**
   `Package::read(dir)` plus `directory_files(dir)` is what
   `launcher/src/native_mods/package.rs::read` does. The GitHub installer walks
   the archive to `DEPTH = 6`, skipping dotfiles, `target` and `node_modules`,
   and requires a folder with both `horizon-mod.toml` and `Cargo.toml`. Rejecting
   a folder that contains `native`, `rust`, `horizon-mixin.toml` or
   `horizon-native.json` is a per-folder rule, not a rule about the repo path.

5. **The error message in the launcher is misleading.** "holds no native mod (a
   folder with horizon-mixin.toml, or an unpacked .hnmod)" mentions files that
   are not the requirement; the real requirement is `horizon-mod.toml` +
   `Cargo.toml`. The actual cause in our case was a `Cargo.toml` whose SDK
   dependency was an absolute path into a local game checkout.
