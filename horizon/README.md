# Horizon port — status

This directory is the **Horizon port scaffold**, not a finished mod.

## Why it is not done

Horizon's own documentation states the constraint plainly:

> Java mod jars don't run in Horizon: their code calls and patches Minecraft's
> Java classes, and Horizon has none.

So the Fabric build in `mods/starflight` cannot be converted; the logic must be
an `.hnmod` (native Rust mod). That is what this directory prepares.

The blocking issue is the capability table in
`horizon/docs/mods.md`. Features this mod needs, as of the check date:

| Needed | Horizon status | Consequence |
|---|---|---|
| Item "use" behaviour (launch) | **Planned 0.3**, not shipped | cannot trigger a strike from an item yet |
| Entities with behaviour (projectile) | **Planned 0.5**, not shipped | cannot fly a real entity yet |
| Block editing from code | not shipped | cannot carve terrain from the mod yet |
| Content mod blocks/items/recipes | **works today** (`.hmod`, API 0.2) | the data half can ship now |

## What is already reusable

The Rust core in `rust/starflight` (1 299 lines, 38 tests, `#![forbid(unsafe_code)]`)
has **no Java and no Minecraft dependency** — strike geometry, flight
integration, collision and per-tick budgeting are plain Rust. It drops into a
Horizon mod with no rewrite. That is the whole payoff of the
"Rust owns logic, Java is a thin adapter" decision (D3).

## What exists here now

- `starflight/horizon-mixin.toml` — native mod manifest, hooking `tick`, which
  is the one hook that exists today and is enough to run a simulation.
- `starflight/src/lib.rs` — the tick hook body, calling the shared core.
- `starflight/compatibility.json` — pins the Horizon revision the bundle was
  written against. **Must be updated** to a commit the maintainers list before
  packaging; see "Verify" below.

Deliberately absent: any `native_set_block`, `native_use_item` or
`native_spawn_entity` binding. Those hooks do not exist, and inventing them would
produce a bundle that fails to build and a false claim of progress.

## Verify before trusting this

```bash
python3 native/tools/native.py check horizon/starflight
```

The check is expected to pass only once `compatibility.json` names a real
Horizon commit and `cargo` can resolve `horizon-mixins` at that revision.

## Next step

1. Clone `horizon-rust/horizon-mods`, read `native/<official mod>/` for the
   exact bundle shape used by Zoom / Minimap.
2. Copy that shape here, keeping our core as a path dependency.
3. Ship the data half (item, recipe, textures) as a separate `.hmod` now; it is
   the only part Horizon can load today.
4. Re-check `docs/mods.md` after the 0.3 release for the item-use hook.
