# LICENSE AUDIT

Date: 2026-11-16. Scope: everything that would be copied, ported or redistributed
in this project.

## Verdict

**No upstream code or assets may be copied.** Both the mod and its required
library are All Rights Reserved. This project is therefore a **clean-room,
independent implementation of publicly described behaviour**, and it must be
described that way in all documentation. It is *not* a "port" in the legal sense
and is not a drop-in replacement.

## Upstream project

| Item | Value |
|---|---|
| Project | The Shooting Star [Demo] |
| Author | rimuru_dev |
| Source | https://www.curseforge.com/minecraft/mc-mods/the-shooting-star-demo |
| Licence | **All Rights Reserved** (stated on the project page) |
| Source code published? | No repository, no source link on the page |
| Required library | Rimulib (project 1753907, also **All Rights Reserved**) |

"All Rights Reserved" means: no permission granted to copy, modify, or
redistribute the work, including its code, models, textures, sounds and the text
of its documentation. Downloading a `.jar` grants no such permission either.

### What this forbids

- ❌ Copying or decompiling the `.jar` into this project
- ❌ Reusing any texture, model, sound, or shader from it
- ❌ Reusing its code structure as a translation source
- ❌ Calling the result a port of the original
- ❌ Redistributing the original `.jar` in this repository
- ❌ Depending on Rimulib as a library, since it is ARR and we would need its
  internals for anything beyond documented API use

### What remains allowed

- ✅ Reading the **public project description** and implementing the described
  *behaviour* independently (game mechanics are not copyrightable; specific
  expression is)
- ✅ Naming the original mod descriptively, with attribution, as inspiration
- ✅ Writing your own code, your own textures, your own sounds

## Legal risk notes (not legal advice)

- Mechanics like "a beam that erases blocks" are generic. The *specific
  expression* (art, sound, code, names) is protected. This project invents its
  own names, its own identifiers, its own assets.
- We deliberately do **not** reuse upstream identifiers such as `SS-01`,
  `SS-03`, `Gungnir`, `Tesseract`, `Seven Stars`, `Gravitia` as our own asset
  names. They appear in `docs/FEATURE_PARITY.md` only as *references to the
  source feature* being reimplemented.
- Minecraft itself is governed by the
  [Minecraft Usage Guidelines](https://www.minecraft.net/en-us/usage-guidelines)
  and the EULA. A mod distributed as a mod is the normal, accepted case; we do
  not ship Minecraft code.
- The optional historical-themed skin (see `README` §rocket) has jurisdiction-
  specific restrictions in some countries. The neutral texture is the default;
  the alternate skin is a separate, user-opted resource pack that this
  repository does **not** ship.

## Assets in this repository

| Asset | Origin | Licence |
|---|---|---|
| All `.rs` sources | written for this project | MIT |
| `java/` sources | written for this project | MIT |
| `docs/` | written for this project | MIT |
| Textures | **none yet** — placeholders are not shipped as finished art | — |

Any future texture or sound must be recorded here with its origin and licence
before it is committed.

## Third-party dependencies

| Crate | Licence | Use |
|---|---|---|
| `jni` | MIT OR Apache-2.0 | JNI bindings only |
| `jni-sys` | MIT OR Apache-2.0 | transitive |
| `cesu8`, `combine`, `thiserror`, `log`, `bytes`, `memchr`, `cfg-if` | MIT / Apache-2.0 | transitive |

No copyleft dependency is present, so the MIT licence on this project is
compatible.

## Toolchains (not redistributed)

Rust and Temurin JDK are installed into `./tools`, which is git-ignored.
Their licences (MIT/Apache-2.0 and GPLv2+CE respectively) permit local use;
they are not part of the deliverable and are not committed.
