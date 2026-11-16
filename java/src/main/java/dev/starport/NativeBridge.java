package dev.starport;

/**
 * JNI bridge to the Rust {@code starflight-jni} dynamic library.
 *
 * <p>This class only knows how to call native functions; it contains no game
 * logic. Every Rust entry point confirms the ABI version first, and failures
 * are returned as negative codes rather than exceptions so a stale or corrupt
 * native library cannot silently misbehave inside a server tick.
 *
 * <p>The native library name is {@code starflight_jni} and is loaded from
 * either the JVM library path or the JAR's bundled platform subdirectory, in
 * that order. See {@link #loadNative()}.
 */
public final class NativeBridge {
    /** Must equal {@code starflight::ABI_VERSION} in Rust. */
    public static final int EXPECTED_ABI = 1;

    // Result codes; must equal the constants in starflight-jni/src/lib.rs.
    public static final int OK = 0;
    public static final int ERR_INVALID_TUNING = -1;
    public static final int ERR_PANIC = -2;
    public static final int ERR_INVALID_BEAM = -3;
    public static final int ERR_NO_SUCH_SLOT = -4;
    public static final int ERR_NULL_BUFFER = -5;

    private static boolean loaded;

    private NativeBridge() {}

    /** Loads the native library if not already loaded. Idempotent and thread-safe-ish. */
    public static synchronized void loadNative() {
        if (loaded) {
            return;
        }
        Throwable failure;
        try {
            System.loadLibrary("starflight_jni");
            loaded = true;
            failure = null;
        } catch (UnsatisfiedLinkError e) {
            failure = e;
        }
        if (failure == null) {
            return;
        }
        // Fallback: a platform-named subdirectory next to the JAR.
        String os = System.getProperty("os.name", "").toLowerCase();
        String arch = System.getProperty("os.arch", "").toLowerCase();
        String libName = System.mapLibraryName("starflight_jni");
        String subdir;
        if (os.contains("win")) {
            subdir = arch.contains("aarch") ? "win-arm64" : "win-x64";
        } else if (os.contains("mac")) {
            subdir = arch.contains("aarch") ? "mac-arm64" : "mac-x64";
        } else {
            subdir = arch.contains("aarch") ? "linux-arm64" : "linux-x64";
        }
        try {
            String base = NativeBridge.class.getProtectionDomain().getCodeSource().getLocation().getPath();
            java.nio.file.Path dir = java.nio.file.Path.of(base).getParent().resolve(subdir);
            java.nio.file.Path lib = dir.resolve(libName);
            if (java.nio.file.Files.exists(lib)) {
                System.load(lib.toString());
                loaded = true;
                return;
            }
        } catch (Exception ignored) {
            // fall through to the diagnostic
        }
        throw new IllegalStateException(
            "Cannot load starflight_jni native library. Set -Djava.library.path to the directory "
                + "containing the built library (rust/target/release), or bundle it under "
                + "'/" + subdir + "/" + libName + "'. Root cause: " + failure,
            failure);
    }

    /** Version handshake. Returns the Rust crate version, or throws if ABI mismatch. */
    public static String version() {
        loadNative();
        String v = versionNative();
        if (v == null) {
            throw new IllegalStateException("Native version() returned null");
        }
        return v;
    }

    /** Compile-time ABI version exported by the library. */
    public static int abiVersion() {
        loadNative();
        return abiVersionNative();
    }

    /** Validates tuning. Returns OK or a negative ERR_ code. */
    public static int validateTuning(double radius, double damage, double speed, long lifetimeTicks) {
        loadNative();
        return vtNative(radius, damage, speed, lifetimeTicks);
    }

    /** Validates a beam geometry. Returns OK or a negative ERR_ code. */
    public static int validateBeam(double centerX, double centerZ, double radius, int yMin, int yMax) {
        loadNative();
        return vbNative(centerX, centerZ, radius, yMin, yMax);
    }

    /** Number of blocks a beam would cover, without building a plan. */
    public static long beamBlockCount(double centerX, double centerZ, double radius, int yMin, int yMax) {
        loadNative();
        return bbcNative(centerX, centerZ, radius, yMin, yMax);
    }

    /** Builds a plan for the beam and stores it in {@code slot}. Returns total blocks or Long.MIN_VALUE on error. */
    public static long buildPlan(double centerX, double centerZ, double radius, int yMin, int yMax, int slot) {
        loadNative();
        return bpNative(centerX, centerZ, radius, yMin, yMax, slot);
    }

    /** True when the plan in {@code slot} is complete (or unknown). */
    public static boolean planComplete(int slot) {
        loadNative();
        return pcNative(slot) != 0;
    }

    /** Number of live plans (diagnostics / leak detection). */
    public static int planCount() {
        loadNative();
        return plcNative();
    }

    /** Frees the plan in {@code slot}. Idempotent. */
    public static void dropPlan(int slot) {
        loadNative();
        dpNative(slot);
    }

    /**
     * Copies up to {@code maxTriplets} {@code (x,y,z)} triplets from the plan in {@code slot} into
     * {@code out} and returns how many were written. {@code out} must hold
     * {@code maxTriplets * 3} ints.
     *
     * @return number of triplets written (0 when the plan is complete), or a negative ERR_ code
     */
    public static int takeBatch(int slot, int[] out, int maxTriplets) {
        loadNative();
        return tbNative(slot, out, maxTriplets);
    }

    // Fast sanity checks used by the test suite.
    public static boolean isLoaded() {
        return loaded;
    }

    private static native String versionNative();
    private static native int abiVersionNative();
    private static native int vtNative(double radius, double damage, double speed, long lifetimeTicks);
    private static native int vbNative(double centerX, double centerZ, double radius, int yMin, int yMax);
    private static native long bbcNative(double centerX, double centerZ, double radius, int yMin, int yMax);
    private static native long bpNative(double centerX, double centerZ, double radius, int yMin, int yMax, int slot);
    private static native int pcNative(int slot);
    private static native int plcNative();
    private static native int dpNative(int slot);
    private static native int tbNative(int slot, int[] out, int maxTriplets);
}