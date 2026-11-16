package dev.starport;

import net.minecraft.server.MinecraftServer;

/**
 * Central place that (a) loads the native library and (b) exposes the current
 * server for the tick-driven code paths.
 *
 * <p>The native library is a hard requirement: without it the Rust core cannot
 * compute a strike, so registration is skipped rather than silently producing a
 * half-broken mod.
 */
public final class Ops {
    private static boolean ready;
    private static volatile MinecraftServer server;

    private Ops() {}

    /** Attempts to load and handshake with the native core. Idempotent. */
    public static synchronized boolean initialise() {
        if (ready) {
            return true;
        }
        try {
            String version = NativeBridge.version(); // triggers load + ABI check
            int abi = NativeBridge.abiVersion();
            if (abi != NativeBridge.EXPECTED_ABI) {
                StarflightMod.LOGGER.error(
                    "ABI mismatch: native={} mod={}", abi, NativeBridge.EXPECTED_ABI);
                return false;
            }
            StarflightMod.LOGGER.info("Native core {} (ABI {})", version, abi);
            ready = true;
            return true;
        } catch (Throwable t) {
            StarflightMod.LOGGER.error(
                "Failed to load native core. Build it with 'cargo build --release' "
                    + "and make starflight_jni.dll discoverable, either via "
                    + "-Djava.library.path=<dir> or bundled under /natives/<platform>/ in the jar.",
                t);
            return false;
        }
    }

    public static boolean isReady() {
        return ready;
    }

    static void setServer(MinecraftServer s) {
        server = s;
    }

    static MinecraftServer server() {
        return server;
    }
}