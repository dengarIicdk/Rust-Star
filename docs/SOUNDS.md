# SOUNDS

All audio here is **original and synthesised in this repository**. Nothing was
taken from the upstream mod, from Minecraft, or from any sound library — the
upstream project is All Rights Reserved (see `LICENSE_AUDIT.md`).

## The two sounds

| File | Duration | Recipe |
|---|---|---|
| `launch.ogg` | 0.70 s | White noise through a low-pass filter whose cutoff opens over time (the whoosh), plus a tone rising 180 → 700 Hz (the pitch), multiplied by an attack/decay envelope. |
| `impact.ogg` | 1.10 s | Heavy low-passed noise (the rumble) plus a sub-bass tone falling 55 → 30 Hz, under an exponential decay. |

Both are mono, 44 100 Hz, quality 0.3 (≈80 kb/s).

## Why they are generated rather than downloaded

A royalty-free sample pack would still carry licence obligations and could not be
verified here. Synthesising from first principles means the licence is simply
this repository's MIT.

## Reproducing them

The converter is `scripts/sndconv` (pure Rust, no C toolchain):

```bash
cargo build --release --manifest-path scripts/sndconv/Cargo.toml
./scripts/sndconv/target/release/sndconv launch.wav \
  mods/starflight/src/main/resources/assets/starflight/sounds/launch.ogg
```

The WAV synthesiser was a throwaway; the parameters are described above so the
WAVs can be regenerated exactly.

## Verified

Both files were decoded back with an independent Vorbis decoder (`lewton`) to
prove they are real audio and not silent:

```
launch.ogg: OK ch=1 rate=44100 samples=30720 peak=0.595
impact.ogg: OK ch=1 rate=44100 samples=48704 peak=0.855
```
