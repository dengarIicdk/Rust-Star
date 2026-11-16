# Starflight for Horizon — native mod

Calls an orbital strike down on a marked spot: a beam falls for 30 ticks, then
carves a column of air with a 16-block radius around the clicked point.

Independent reimplementation of the mechanics described by *The Shooting Star
[Demo]* (All Rights Reserved). No upstream code or assets are used; the texture
and the sounds are original. MIT.

## Install

Native mods are **source mixins**: they are compiled into your own Horizon build.
Horizon calls them experimental and builds them only with your explicit consent,
because their code runs with your rights.

1. Copy this folder next to your instance, e.g. `C:\HorizonMods\starflight`.
   It must keep `horizon-mixin.toml` beside `runtime.rs`, `use_item.rs`,
   `server_tick.rs`, `register_commands.rs`, `engine/`, `assets/` and `data/`.
2. Build and play it:

```
horizon-launcher native-build C:\HorizonMods\starflight --trust-native-code
```

In the window: Mods -> native mods -> add this folder, then
Settings -> Game -> **Native mods (experimental)**, then **Build**.

## Use

- Right-click holding a **Starflight Rocket** (`starflight:orbital_rocket`)
  to call a strike where you look. The rocket is not consumed - see the gaps.
- Operators: `/starflight strike [radius]` (level 2+, radius 8..128).

The item and its recipe are content in `assets/` and `data/`. If your launcher
build does not include content mods yet, the command still works, and so does
the right click on the item once it is in your inventory.

## What this is built on

| need | Horizon API |
|---|---|
| take the right click | named hook `use_item` v1, `native_use_item(stack, &mut claimed)` |
| tick the server | named hook `server_tick` v1, `native_server_tick(context, phase)` |
| edit blocks | `native_edit_blocks(dimension, &NativeBlockEdit) -> Result<u64, String>` |
| register a command | named hook `register_commands` v1, `NativeCommand::op(usage, level)` |
| find the player | `native_player() -> Option<NativePlayer>` |

Every one of those was read from Horizon's source at the revision this was
written against, plus Horizon's own `native-hooks.json` catalog (434 hooks).
Nothing here is guessed from documentation.

No `[compatibility] horizon_revision` is pinned on purpose: a bundle without a
pin builds on the commit of the version you have installed, so it is not tied to
one development commit. All hooks used are version 1 contracts.

## Tests

The geometry and the strike schedule are ordinary Rust with no game types:

```
cd engine && cargo test        # 36 tests
```

`runtime.rs` keeps its own 6 tests (descent, exactly-once delivery, per-tick
budget, refused radius, refused non-finite centre, radius argument). The
game-facing entry points are behind `#[cfg(horizon_game)]`, so a desktop
`cargo test` never needs Horizon to exist.

## Known gaps

* **The rocket is not consumed.** Horizon's native API has no write to a
  player's inventory hand in this revision; one rocket calls any number of
  strikes. Documented instead of faked.
* **Nothing here has been run in a game.** This machine has no Horizon client
  and no Linux build host, and the mixin staging tool is validated on Linux
  only. What is proven: 42 tests, and every API signature read from the source.
  What is not: that the item appears, that a strike carves, that the build
  succeeds on your machine.
* **Radius above 128 needs a streaming plan.** Larger requests are refused, not
  clamped.
