# Horizon port — status

Port of the Fabric mod in `mods/starflight` to **Horizon** (the Rust Minecraft
client). Both are independent reimplementations of the mechanics described by
*The Shooting Star [Demo]* (All Rights Reserved); no upstream code or assets are
used in either. The item icon is drawn procedurally by `scripts/mkpng`.

Last verified: 2026-11-16 against
`horizon-rust/horizon@69ab572dbd1bf2f2924a424f791014e09d9d0b50` and
`horizon-mixins@e59da9094f0c2fdda6c627fe9eff22dec6a906f0`.

## It builds a complete mod

```
$ ./scripts/build-horizon.sh
built horizon/starflight/dist/starflight.hmod
starflight 0.1.0 (native-mod, format native-1/..., for 26.3)
  8084 bytes, SHA-256 13b2e90bd..., unsigned
```

Accepted by Horizon's own checker (`cargo horizon-mod check --kind native-mod` →
`{"ok":true}`). "unsigned" is expected: signing is only needed for the registry.

Contents: 10 files — manifest, native source, assets (item model, texture, lang)
and the recipe.

## What it contains

| Piece | State |
|---|---|
| Item `starflight:orbital_rocket` | declared in code via the `items` hook |
| Item model, 16×16 texture, English name | ✅ |
| Recipe (iron + diamond + firework rocket) | ✅ |
| `right_click_item` → queues a strike | ✅ |
| `server_tick` → carves via `edit_blocks` | ✅ |
| `/starflight strike [radius]` | ✅ (radius form is operator-only) |
| Test suite | ✅ 9/9 |

The template is `minecraft:firework_rocket`: stackable, places no block, which is
what a strike call should be. Right-clicking queues a strike on the aimed block;
`server_tick` hands the positions to `ServerHost::edit_blocks` as one `BlockEdit`
with a single `minecraft:air` palette entry. A refused edit abandons the strike
rather than leaving the world half-carved.

Carve: radius 16, 24 blocks above the target, 16 below. A strike waits 30 ticks
(`DESCENT_TICKS`) before the first carve, matching the Fabric build's fall time.

`/starflight strike [radius]` exists so the mechanic is testable with one command
and reachable if the item is lost.

## Verified

| Check | Result |
|---|---|
| Compiles against the real generated SDK | ✅ |
| `cargo clippy --all-targets` | ✅ no warnings |
| `cargo horizon-mod build` → `.hmod` | ✅ 8 084 bytes, 10 files |
| `cargo horizon-mod check --kind native-mod` | ✅ `ok: true` |
| Tests | ✅ 9/9 |
| Item id in the declaration matches the id the click hook matches | ✅ (guarded by a test) |
| `./scripts/verify.sh` | ✅ `VERIFY: PASS` |

## NOT verified — it has never been run

**The mod has not been executed in Horizon.** Launching needs an
`horizon-client` binary and, for native mods, `horizon-launcher native-build`:

```
$ cargo horizon-mod run
error: code mods run through the launcher's native build:
       horizon-launcher native-build . --trust-native-code
```

Neither is installed, and neither is published:
`https://github.com/horizon-rust/horizon/releases/download/launcher/SHA256SUMS`
returns 404, as does every `sdk-<commit>` release. So:

* **Not proven:** that the item appears in the inventory, that the texture
  renders, that the recipe is craftable, that right-clicking starts a strike,
  that the blocks actually disappear.
* **Proven:** the code compiles against the exact pinned API, the package is
  structurally valid and passes Horizon's checker, and the scheduling logic
  delivers every position exactly once within the per-tick budget.

Runtime behaviour is unproven and should not be described as working.

### Other gaps

* The rocket is **not consumed** on use. `ItemStack::shrink` returns a new stack,
  and writing it back needs inventory paths this port does not have yet. The item
  currently calls strikes indefinitely.
* No descent visual. The Fabric build draws a beam from orbit; this port waits the
  same 30 ticks but shows nothing. `LevelRef::global_level_event` is the hook.
* No sound or particles on impact.

## How it was unblocked

The original blocker was `cc-rs: failed to find tool "gcc.exe"`. Fixed with
WinLibs MinGW-w64:

```sh
winget install BrechtSanders.WinLibs.POSIX.UCRT
cargo install --git https://github.com/horizon-rust/horizon cargo-horizon-mod
```

Every signature was then read from the generated API at
`crates/horizon-mod-vocab/src/hooks/*.rs` and
`target/debug/build/horizon-mod-vocab/*/out/mod_api_generated*.rs`. See
`starflight/API_NOTES.md` for the confirmed surface and the five things I had
guessed wrong.

## Building

`scripts/build-horizon.sh` stages a copy of the crate inside the game checkout,
packs it, copies the `.hmod` back, and restores the checkout. Necessary because
`cargo horizon-mod build` refuses to pack a native mod outside a checkout and
will not download an SDK — no signed `sdk-<commit>` release exists for the pinned
build. The checkout is verified clean afterwards.

## Next steps

1. Get an `horizon-client` + `horizon-launcher` and actually run it.
2. Consume the rocket on use (needs inventory write paths).
3. Descent visual via `LevelRef::global_level_event`.
4. Impact sound and particles.
