# Starflight — orbital strike for Horizon

Calls an orbital strike down on the block you aim at. Right-click with the
**Orbital Rocket**, or run `/starflight strike` — a beam of air is carved out of
the world above and below the target.

This is a **native Horizon mod** (`.hnmod` / `.hmod`, Rust). It does not run in
vanilla Minecraft or Fabric.

This is an independent reimplementation of the mechanics described by
*The Shooting Star [Demo]* (All Rights Reserved). No upstream code, textures,
models or sounds are used; the item icon is drawn procedurally.

## Install

You need the Horizon launcher and an instance.

**From the Mods page:** *Add from GitHub* → paste this repository's URL.

**From the command line:**

```sh
horizon-launcher mods add-github https://github.com/dengarIicdk/Rust-Star/tree/horizon-mod --trust-native-code
```

That branch is this mod only: its root is the mod project, which is the layout
the launcher requires. Then build and launch:

```sh
horizon-launcher native-build
```

`--trust-native-code` records your consent to run native code. A native mod runs
with full access to your computer, so read the source first — it is all in `src/`.

If you would rather install a prebuilt package, `starflight.hmod` in this repo is
the same code already packed and checked. Use the launcher's *add from file*.

## Using it

| Action | Result |
|---|---|
| Right-click holding **Orbital Rocket** | Strike on the aimed block (radius 16) |
| `/starflight strike` | Strike at your position |
| `/starflight strike <radius>` | Custom radius (operator level 2) |

A strike waits 30 ticks (~1.5 s) before it starts carving, the way the original
falls from orbit first. It carves 24 blocks above the target and 16 below.

Recipe: firework rocket in the centre, 4 iron ingots and 4 diamonds around it.

## What is proven and what is not

Proven on this machine:

* compiles against Horizon `69ab572dbd1bf2f2924a424f791014e09d9d0b50`
* 47 unit tests pass (strike scheduling, beam geometry, limits, item declaration)
* `cargo horizon-mod check` accepts the package: `{"ok":true}`

**Not proven: it has never been run in a Horizon client.** No client or launcher
binary was available where it was built. So the item appearing in your inventory,
the texture, the recipe and the blocks actually disappearing are all unverified.
If something does not work, that is the most likely place.

Known gaps: the rocket is not consumed on use, there is no descent visual, and
there is no impact sound.

## How it works

`src/core/` is engine-independent: beam geometry, block plans, and the per-tick
budget, with no Horizon or Minecraft types, so it is fully unit-tested. `src/`
is the Horizon integration: the `items` hook registers the rocket, the
`right_click_item` hook queues a strike, and `server_tick` hands each tick's
positions to `ServerHost::edit_blocks` as one `BlockEdit` with a single
`minecraft:air` palette entry. A refused edit abandons the strike rather than
leaving the world half-carved.

Out-of-range input (an absurd radius, a non-finite centre, an inverted height
range) is refused with a reason, never quietly clamped.

## Licence

MIT. See `LICENSE`.
