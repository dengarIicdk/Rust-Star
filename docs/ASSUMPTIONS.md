# ASSUMPTIONS

Assumptions made **without asking**, because the session runs autonomously.
Each one is reversible or is flagged where it is not.

| # | Assumption | Basis | Risk if wrong |
|---|---|---|---|
| A1 | Target is Minecraft **Java**, Fabric, **1.21.1** | brief's default rules + most-supported version | adapter work would need retargeting (cheap) |
| A2 | OS is the agent's OS: **Windows x64** | brief says "current OS" | a Linux build needs `libstarflight_jni.so` (the Rust is portable) |
| A3 | Java adapter via JNI is allowed | brief explicitly prefers it | none |
| A4 | The user does not have a `.jar` to give, and if they did it could not be reused | ARR licence | no functional change |
| A5 | "Port" means independent reimplementation | ARR licence forbids copying | documentation wording only |
| A6 | Upstream numeric values in the description are marketing, not spec | description is prose | our defaults are our own |
| A7 | Integrator (Java) is responsible for permissions/cooldowns | Rust returns positions, not authority | Stage 6 work |
| A8 | No Minecraft client/server is available here | environment inspection | Stage 3 blocked, reported honestly |
| A9 | `MAX_BLOCKS_PER_TICK = 16384` is a reasonable budget | chosen to keep a tick's block writes manageable | tunable constant |
| A10 | Historical skin is opt-in, neutral default | legal/platform risk | user can request otherwise, but distribution is on them |
| A11 | English is acceptable for code/docs | toolchain output is English | none |
| A12 | Git author identity is absent locally | `git config` empty | commits made with a project-local identity, global config untouched |

## Assumptions we explicitly did NOT make

- ✗ That the original mod is bug-free or that its numbers are correct.
- ✗ That a decompilation would be legal — it is not, under ARR.
- ✗ That Rust will make block edits faster. Only the boundary was measured.
- ✗ That the `.jar` on CurseForge is safe to run; it was never downloaded or
  executed.
- ✗ That the user wants the historical skin shipped in-repo; they asked for a
  variant, and the brief asks for a reference first.
