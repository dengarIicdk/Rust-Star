# Horizon port — status

Port of the Fabric mod in `mods/starflight` to **Horizon** (the Rust Minecraft
client). Both are independent reimplementations of the mechanics described by
*The Shooting Star [Demo]* (All Rights Reserved); no upstream code or assets are
used in either.

Last verified: 2026-11-16 against `horizon-rust/horizon@69ab572dbd1bf2f2924a424f791014e09d9d0b50`.

## It builds

```
$ ./scripts/build-horizon.sh
Building against the game checkout .../horizon-5a198cb72f4fbd36/69ab572
Built .\target/horizon-mod\starflight.hmod
built horizon/starflight/dist/starflight.hmod
starflight 0.1.0 (native-mod, format native-1/69ab572.../e59da909..., for 26.3)
  5515 bytes, SHA-256 f05c041a8..., unsigned
```

`dist/starflight.hmod` is a real Horizon native mod, accepted by Horizon's own
package checker (`cargo horizon-mod check --kind native-mod`). "unsigned" is
expected: signing is only needed to submit to the registry.

## What it does

* **`right_click_item`** — right-clicking while holding `starflight:orbital_rocket`
  resolves the item id through `ServerHost::item_key`, compares the opaque
  `ItemKey`, and queues a strike centred on the aimed block. Client side returns
  early so a client's copy never acts on a world it does not own.
* **`server_tick`** — advances each live strike one tick and hands the resulting
  positions to `ServerHost::edit_blocks` as a single `BlockEdit` with one
  `minecraft:air` palette entry. A refusal abandons the strike rather than
  leaving the world half-carved.

Carve: radius 16, 24 blocks above the target, 16 below.

## Verified

| Check | Result |
|---|---|
| Compiles against the real generated SDK | ✅ |
| `cargo clippy` | ✅ no warnings |
| `cargo horizon-mod build` → `.hmod` | ✅ 5 515 bytes |
| `cargo horizon-mod check --kind native-mod` | ✅ accepted |
| Scheduling tests (`src/strike.rs`) | ✅ 6/6 |
| `./scripts/verify.sh` | ✅ `VERIFY: PASS` |

## Not verified

**It has not been run in Horizon.** Launching the game needs the Horizon client
binary and a signed build; neither is reachable from here. The code is
compile-checked and package-checked against the exact pinned revisions, which is
the furthest automated verification goes. Runtime behaviour (does the block
actually disappear, does the item exist in the registry) is unproven.

The item `starflight:orbital_rocket` is also **not yet registered** — this port
uses the hook path only. Registering it needs a content artifact (`assets/`,
`data/`) alongside the native code, which is the next step.

## How it was unblocked

The earlier blocker was `cc-rs: failed to find tool "gcc.exe"`. Fixed by
installing WinLibs MinGW-w64 via `winget`. With a C compiler present,
`cargo-horizon-mod` installs and the SDK becomes readable:

```sh
winget install BrechtSanders.WinLibs.POSIX.UCRT
cargo install --git https://github.com/horizon-rust/horizon cargo-horizon-mod
```

The generated API then lives at
`crates/horizon-mod-vocab/src/hooks/*.rs` and
`target/debug/build/horizon-mod-vocab/*/out/mod_api_generated.rs`. Every
signature in `src/lib.rs` was read from there.

## Why the build script stages a copy

`cargo horizon-mod build` refuses to pack a native mod outside a game checkout,
and will not download an SDK — no signed `sdk-<commit>` release exists for the
pinned build. `scripts/build-horizon.sh` copies the crate into the checkout,
packs it, copies the `.hmod` back, and restores the checkout (verified clean
afterwards).

## Next steps

1. Register `starflight:orbital_rocket` as a real item (content artifact: model,
   texture, lang, recipe) so it can be held.
2. Run it in a Horizon client and confirm the carve.
3. Add the descent phase visually (`LevelRef::global_level_event`).
4. When an official signed SDK ships, replace the staging build with
   `cargo horizon-mod build` in place.
