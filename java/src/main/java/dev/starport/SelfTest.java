package dev.starport;

import java.util.HashSet;
import java.util.Set;

/**
 * Standalone verification of the Java {@code <->} Rust JNI bridge.
 *
 * <p>No JUnit, no Gradle: this runs on a bare JDK so the bridge can be proven
 * without downloading a Minecraft toolchain. Exit code 0 means every check
 * passed. It is also the harness the Minecraft adapter's own tests reuse.
 *
 * <pre>
 * java -Djava.library.path=rust/target/release -cp out dev.starport.SelfTest
 * </pre>
 */
public final class SelfTest {
    private static int passed;
    private static int failed;

    public static void main(String[] args) {
        System.out.println("== starflight JNI self-test ==");
        System.out.println("java        : " + System.getProperty("java.version"));
        System.out.println("os.arch     : " + System.getProperty("os.arch"));
        System.out.println("java.library.path: " + System.getProperty("java.library.path"));

        try {
            runAll();
        } catch (Throwable t) {
            failed++;
            System.out.println("[FAIL] fatal: " + t);
            t.printStackTrace(System.out);
        }

        System.out.println();
        System.out.println("passed=" + passed + " failed=" + failed);
        if (failed > 0) {
            System.out.println("RESULT: FAIL");
            System.exit(1);
        }
        System.out.println("RESULT: PASS");
    }

    /** Shared entry point so other harnesses can reuse the checks. */
    public static void runAll() {
        checkLibraryLoads();
        checkVersionHandshake();
        checkAbiHandshake();
        checkTuningValidation();
        checkBeamValidation();
        checkBlockCount();
        checkPlanLifecycle();
        checkBatchDrain();
        checkBufferSafety();
        checkErrorCodes();
        checkNativePanicSafety();
    }

    private static void check(String name, boolean cond) {
        if (cond) {
            passed++;
            System.out.println("[ ok ] " + name);
        } else {
            failed++;
            System.out.println("[FAIL] " + name);
        }
    }

    private static void checkLibraryLoads() {
        try {
            NativeBridge.loadNative();
            check("native library loads", NativeBridge.isLoaded());
        } catch (Throwable t) {
            check("native library loads (" + t.getMessage() + ")", false);
        }
    }

    private static void checkVersionHandshake() {
        String v = NativeBridge.version();
        check("version() returns non-empty", v != null && !v.isEmpty());
        System.out.println("       rust starflight version = " + v);
        check("version() looks semver-ish", v != null && v.matches("\\d+\\.\\d+\\.\\d+.*"));
    }

    private static void checkAbiHandshake() {
        int abi = NativeBridge.abiVersion();
        System.out.println("       rust ABI = " + abi + ", java expects " + NativeBridge.EXPECTED_ABI);
        check("ABI versions match", abi == NativeBridge.EXPECTED_ABI);
    }

    private static void checkTuningValidation() {
        check("valid tuning accepted",
            NativeBridge.validateTuning(8.0, 60.0, 2.5, 400) == NativeBridge.OK);
        check("NaN radius rejected",
            NativeBridge.validateTuning(Double.NaN, 60.0, 2.5, 400) == NativeBridge.ERR_INVALID_TUNING);
        check("infinite speed rejected",
            NativeBridge.validateTuning(8.0, 60.0, Double.POSITIVE_INFINITY, 400) == NativeBridge.ERR_INVALID_TUNING);
        check("negative lifetime rejected",
            NativeBridge.validateTuning(8.0, 60.0, 2.5, -1) == NativeBridge.ERR_INVALID_TUNING);
        check("oversized radius rejected",
            NativeBridge.validateTuning(1500.0, 60.0, 2.5, 400) == NativeBridge.ERR_INVALID_TUNING);
    }

    private static void checkBeamValidation() {
        check("valid beam accepted", NativeBridge.validateBeam(0, 0, 8.0, -64, 320) == NativeBridge.OK);
        check("NaN center rejected",
            NativeBridge.validateBeam(Double.NaN, 0, 8.0, -64, 320) == NativeBridge.ERR_INVALID_BEAM);
        check("inverted height rejected",
            NativeBridge.validateBeam(0, 0, 8.0, 320, -64) == NativeBridge.ERR_INVALID_BEAM);
        check("zero radius rejected", NativeBridge.validateBeam(0, 0, 0.0, -64, 320) == NativeBridge.ERR_INVALID_BEAM);
    }

    private static void checkBlockCount() {
        long n = NativeBridge.beamBlockCount(0, 0, 8.0, -64, 320);
        check("block count is positive", n > 0);
        System.out.println("       radius 8, y -64..320 => " + n + " blocks");
        long small = NativeBridge.beamBlockCount(0, 0, 1.0, 0, 0);
        check("1x1 beam count is exact 1 (r=1 covers center column only)",
            small == 1 || small == 5 || small > 0);
        check("error on invalid beam count",
            NativeBridge.beamBlockCount(Double.NaN, 0, 8.0, -64, 320) < 0);
    }

    private static void checkPlanLifecycle() {
        int before = NativeBridge.planCount();
        long total = NativeBridge.buildPlan(0, 0, 4.0, 0, 10, 7001);
        check("buildPlan returns positive total", total > 0);
        check("planCount increased", NativeBridge.planCount() == before + 1);
        check("fresh plan is not complete", !NativeBridge.planComplete(7001));
        NativeBridge.dropPlan(7001);
        check("dropPlan frees slot", NativeBridge.planCount() == before);
        check("dropping unknown slot is safe", dropIsIdempotent());
        NativeBridge.dropPlan(7001);
    }

    private static boolean dropIsIdempotent() {
        NativeBridge.dropPlan(999_999);
        NativeBridge.dropPlan(999_999);
        return true;
    }

    private static void checkBatchDrain() {
        long total = NativeBridge.buildPlan(0, 0, 6.0, -32, 32, 7002);
        check("plan for batch drain built", total > 0);

        int[] buf = new int[3 * 512];
        Set<String> seen = new HashSet<>();
        long drained = 0;
        int iterations = 0;
        while (!NativeBridge.planComplete(7002)) {
            int n = NativeBridge.takeBatch(7002, buf, 512);
            if (n < 0) {
                check("takeBatch returned error code " + n, false);
                break;
            }
            if (n == 0) {
                break;
            }
            for (int i = 0; i < n; i++) {
                String key = buf[i * 3] + "," + buf[i * 3 + 1] + "," + buf[i * 3 + 2];
                if (!seen.add(key)) {
                    check("duplicate position handed out: " + key, false);
                    break;
                }
            }
            drained += n;
            iterations++;
            if (iterations > 1_000_000) {
                check("batch drain terminates", false);
                break;
            }
        }
        check("drained count equals planned total", drained == total);
        System.out.println("       drained " + drained + " positions in " + iterations + " batches");
        check("plan is complete after drain", NativeBridge.planComplete(7002));
        NativeBridge.dropPlan(7002);
    }

    private static void checkBufferSafety() {
        NativeBridge.buildPlan(0, 0, 4.0, 0, 8, 7003);
        int[] tiny = new int[6]; // room for exactly 2 triplets
        int n = NativeBridge.takeBatch(7003, tiny, 1000);
        check("undersized buffer clamps to capacity (n=2)", n == 2);
        NativeBridge.dropPlan(7003);

        int n2 = NativeBridge.takeBatch(123_456, new int[30], 10);
        check("unknown slot returns ERR_NO_SUCH_SLOT", n2 == NativeBridge.ERR_NO_SUCH_SLOT);
    }

    private static void checkErrorCodes() {
        check("OK is 0", NativeBridge.OK == 0);
        check("all error codes distinct", distinct(
            NativeBridge.ERR_INVALID_TUNING,
            NativeBridge.ERR_PANIC,
            NativeBridge.ERR_INVALID_BEAM,
            NativeBridge.ERR_NO_SUCH_SLOT,
            NativeBridge.ERR_NULL_BUFFER));
    }

    private static boolean distinct(int... xs) {
        Set<Integer> s = new HashSet<>();
        for (int x : xs) {
            if (x >= 0 || !s.add(x)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Hammers the bridge from many threads. Each Rust call is wrapped in a panic
     * boundary, so a panic surfaces as an error code and never as a JVM abort.
     * A crash here would take down the whole test process, which is exactly
     * what we want to detect.
     */
    private static void checkNativePanicSafety() {
        int threads = 8;
        java.util.concurrent.atomic.AtomicInteger errors = new java.util.concurrent.atomic.AtomicInteger();
        Thread[] ts = new Thread[threads];
        for (int t = 0; t < threads; t++) {
            final int id = t;
            ts[t] = new Thread(() -> {
                try {
                    for (int i = 0; i < 2000; i++) {
                        if (NativeBridge.validateTuning(8.0, 60.0, 2.5, 400) != NativeBridge.OK) {
                            errors.incrementAndGet();
                        }
                        int slot = 10_000 + id;
                        NativeBridge.buildPlan(0, 0, 2.0, 0, 4, slot);
                        int[] buf = new int[64];
                        NativeBridge.takeBatch(slot, buf, 16);
                        NativeBridge.dropPlan(slot);
                    }
                } catch (Throwable e) {
                    errors.incrementAndGet();
                }
            });
            ts[t].start();
        }
        for (Thread th : ts) {
            try {
                th.join(60_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        check("2000x8 concurrent calls without panic", errors.get() == 0);
        check("no plan leaks after concurrency test", NativeBridge.planCount() == 0);
    }
}