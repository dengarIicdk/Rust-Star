# FEATURE PARITY

**Important framing:** the upstream mod is All Rights Reserved and publishes
only a marketing description. A feature listed here is therefore a **described
behaviour**, not a confirmed specification. Anything not observable in the
description is marked `unconfirmed` and would have to be designed by us before
implementation. This table tracks our independent reimplementation, not a
faithful copy.

Status key:
- **Imported** — description on CurseForge (public page)
- **impl** — implemented in Rust core
- **adapter** — needs the Minecraft adapter (Stage 3)
- **design** — designed, not yet implemented
- **n/a** — not applicable / declined

## Catalogue (from the public description)

| Feature | Upstream name | Imported | Our status | How we implement | Test |
|---|---|---|---|---|---|
| Beam that erases a cylinder | SS-01 "Railgun" (200-block radius, 10–1500 slider) | ✅ | **impl** | `beam.rs`: vertical cylinder, exact footprint + streamed source | unit: count/unique |
| Impact damage | (in description) | ✅ | **impl** core | `projectile.rs` hit → `damage` | unit: damage value |
| Block mutation, bounded per tick | (implied by scale) | ✅ | **impl** | `plan.rs`: chunk plan + 16 384/tick budget | unit: drain no-dup |
| Projectile flight | (in description) | ✅ | **impl** | `projectile.rs` swept AABB per tick | unit: hit/fly/expire |
| Skill menu | (H key, sliders) | ✅ | **design** | Java GUI, config values via `validateTuning` | manual |
| Cutscenes / camera | (SS-01, SS-04 etc.) | ✅ | **design** | Java+shaders, out of scope for core | n/a |
| Earth-curving kinetic strike | SS-03 "Gungnir" | ✅ (described) | design | crater function not written | n/a |
| Constellation of strikes | SS-04 "Seven Stars" | ✅ (described) | design | multi-beam plan per star | n/a |
| Tesseract grid | SS-05 | ✅ (described) | design | grid beam set | n/a |
| World-replace | SS-06 "Gravitia" | ✅ (described) | design | new-dimension logic, out of scope | n/a |
| Stellar Remote item | main item | ✅ | **design** | Java item; no copy of assets | manual |
| Item/config persistence | — | unconfirmed | **design** | Rust save format at Stage 6 | tbd |
| Multiplayer authority | server must validate | ✅ (environment: Client & Server) | **designed** | server-side check, packets | Stage 6 |
| Sound / VFX | (described) | ✅ | not started | own assets required (ARR) | n/a |
| Textures / models | (some) | ✅ (exist, not ours) | not started | own assets, licences recorded | n/a |

## The original mod's specific identifiers we deliberately do NOT reuse

`SS-01..06`, `Railgun`, `Gungnir`, `Seven Stars`, `Tesseract`, `Gravitia`,
`Stellar Remote`, `Rimulib`. They are cited above only to reference the *source
description*; our own names are chosen independently (`Starflight` as the mod
name, original item IDs like `starflight:orbital_rocket`).

## New user wishes (not upstream)

| Wish | Status |
|---|---|
| Custom rocket, own ID | designed: `starflight:orbital_rocket` (adapter) |
| Speed / lifetime / damage configurable | **impl** (`limits.rs` `Tuning`) + validated |
| Optional historical-themed skin | **designed, not shipped** — pending user's visual reference; neutral default; distribution caveat in `README` & `DECISIONS.md` |
| Trail / sound / impact effects | designed, assets not started |

## Honesty rules

- "confirmed" only means *seen in the public description*.
- Because the licence forbids copying, **parity is behavioural intent, never
  bit-for-bit**. Numeric values (radius 200, slider 1500) are the author's
  description and may not match the actual mod.