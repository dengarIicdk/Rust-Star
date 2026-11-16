package dev.starport;

/**
 * Bounded "risk launcher" that saturates the JNI boundary with concurrent
 * traffic while the server tick normally would. Used only by tests and a
 * diagnostic command; never runs during normal gameplay.
 *
 * <p>If the Rust panic boundary or plan registry ever regressed, this would
 * crash the JVM — which is the point of a stress test — so it is hard-gated
 * behind an explicit flag.
 */
public final class RiskLauncher {
    private static volatile boolean enabled;

    private RiskLauncher() {}

    public static void setEnabled(boolean e) {
        enabled = e;
    }

    public static boolean isEnabled() {
        return enabled;
    }

    /**
     * Runs {@code threads × iters} validation + plan-build/drain/drop cycles.
     * Returns the number of errors observed.
     */
    public static int hammer(int threads, int iters) {
        if (!Ops.isReady()) {
            return -1;
        }
        java.util.concurrent.atomic.AtomicInteger errors = new java.util.concurrent.atomic.AtomicInteger();
        Thread[] ts = new Thread[threads];
        for (int t = 0; t < threads; t++) {
            final int id = t;
            ts[t] = new Thread(() -> {
                for (int i = 0; i < iters; i++) {
                    try {
                        if (NativeBridge.validateTuning(8.0, 60.0, 2.5, 400) != NativeBridge.OK) {
                            errors.incrementAndGet();
                        }
                        int slot = 50_000 + id;
                        long total = NativeBridge.buildPlan(0, 0, 2.0, 0, 4, slot);
                        int[] buf = new int[64];
                        while (!NativeBridge.planComplete(slot)) {
                            NativeBridge.takeBatch(slot, buf, 16);
                        }
                        NativeBridge.dropPlan(slot);
                    } catch (Throwable e) {
                        errors.incrementAndGet();
                    }
                }
            });
            ts[t].start();
        }
        for (Thread th : ts) {
            try {
                th.join(120_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                errors.incrementAndGet();
            }
        }
        return errors.get();
    }
}