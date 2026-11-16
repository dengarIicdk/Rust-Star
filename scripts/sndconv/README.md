# sndconv

Build-time helper that turns a 16-bit WAV into Ogg Vorbis, used once to produce
the mod's original sounds (`launch.ogg`, `impact.ogg`). It is kept in the repo so
the sounds are reproducible from source instead of being unexplained binaries.

It is a **pure-Rust** encoder (no C compiler needed): `zvidlib-vorbis-encoder`
does the Vorbis bitstream and `src/main.rs` writes the Ogg container pages
(lacing, granule positions, CRC-32).

```bash
./tools/sndconv/target/release/sndconv input.wav output.ogg
```

The sounds themselves were synthesised in `docs/SOUNDS.md`'s recipe: filtered
noise plus a rising tone for the launch, a low-passed rumble for the impact. No
audio was taken from any other mod.
