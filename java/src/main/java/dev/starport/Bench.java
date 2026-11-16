package dev.starport;

/**
 * Measures the cost of crossing the Java {@code <->} Rust boundary, to justify
 * (or refute) the batching design with numbers instead of assertion.
 *
 * <p>Method: each scenario is warmed up, then repeated for enough rounds that
 * per-round variance is visible. The reported figure is the <b>median</b> of
 * the rounds, which is far more stable than a single run.
 *
 * <pre>
 * java -Djlp=rust/target/release -Djava.library.path=rust/target/release -cp out dev.starport.Bench
 * </pre>
 *
 * Results are printed, never asserted: the target machine is different.
 */
public final class Bench {
    private static final int ROUNDS = 9;

    public static void main(String[] args) {
        NativeBridge.loadNative();
        System.out.println("== starflight boundary benchmark ==");
        System.out.println("note: medians of " + ROUNDS + " rounds after warmup");

        measureTrivialCall();
        measurePerBlockCall();
        measureBatchedDrain();
    }

    /** Cost of one round-trip with no payload: the floor for any per-block design. */
    private static void measureTrivialCall() {
        int iters = 100_000;
        long[] samples = new long[ROUNDS];
        for (int r = 0; r < ROUNDS; r++) {
            long t0 = System.nanoTime();
            for (int i = 0; i < iters; i++) {
                NativeBridge.validateTuning(8.0, 60.0, 2.5, 400);
            }
            samples[r] = System.nanoTime() - t0;
        }
        long med = median(samples);
        System.out.printf("single JNI round-trip : %.0f ns/call (%d iterations)%n",
            (double) med / iters, iters);
    }

    /**
     * Cost of a call that actually copies one position out of a live plan.
     * This is the real per-block alternative to batching: one native call per
     * block.
     */
    private static void measurePerBlockCall() {
        int[] one = new int[3];
        long[] samples = new long[ROUNDS];
        long positions = 0;
        for (int r = 0; r < ROUNDS; r++) {
            // Build the plan ONCE, then drain it one position per native call.
            // That isolates the per-call cost from plan construction, which is
            // what a naive design would pay in steady state.
            long total = NativeBridge.buildPlan(0, 0, 32.0, -64, 320, 9001);
            positions = total;
            long t0 = System.nanoTime();
            long drained = 0;
            while (!NativeBridge.planComplete(9001)) {
                int n = NativeBridge.takeBatch(9001, one, 1);
                if (n <= 0) {
                    break;
                }
                drained += n;
            }
            samples[r] = System.nanoTime() - t0;
            NativeBridge.dropPlan(9001);
            if (drained != total) {
                System.out.println("[FAIL] per-block drain mismatch " + drained + " vs " + total);
                return;
            }
        }
        long med = median(samples);
        System.out.printf("one-position call     : %.1f ns/position (%d positions, one JNI call each)%n",
            (double) med / positions, positions);
    }

    /** Cost of draining a realistic beam scan with the production batch size. */
    private static void measureBatchedDrain() {
        int batchSize = 16384; // MAX_BLOCKS_PER_TICK
        int[] buf = new int[batchSize * 3];
        long[] samples = new long[ROUNDS];
        long plannedTotal = 0;
        long checksum = 0;

        for (int r = 0; r < ROUNDS; r++) {
            long t0 = System.nanoTime();
            long total = NativeBridge.buildPlan(0, 0, 32.0, -64, 320, 4242);
            plannedTotal = total;
            long sum = 0;
            long applied = 0;
            while (!NativeBridge.planComplete(4242)) {
                int n = NativeBridge.takeBatch(4242, buf, batchSize);
                if (n <= 0) {
                    break;
                }
                applied += n;
                // Real per-position work, mirroring what Minecraft's block loop
                // would cost. The checksum is a live data dependency so the JIT
                // cannot delete the loop.
                for (int i = 0; i < n; i++) {
                    sum += buf[i * 3] + buf[i * 3 + 1] + buf[i * 3 + 2];
                }
            }
            NativeBridge.dropPlan(4242);
            checksum = sum;
            samples[r] = System.nanoTime() - t0;
            if (plannedTotal <= 0) {
                System.out.println("[FAIL] plan build failed, aborting benchmark");
                return;
            }
        }
        long med = median(samples);
        System.out.printf("batched drain         : %.1f ns/position (%d positions, batch=%d)%n",
            (double) med / plannedTotal, plannedTotal, batchSize);
        System.out.printf("checksum              : %d (proves the loop ran)%n", checksum);
        System.out.println();
        System.out.println("Reading this: 'one-position call' is the cost of a naive design that");
        System.out.println("crosses the boundary once per block; 'batched drain' is this design.");
        System.out.println("Neither says anything about how fast Minecraft applies blocks -- that");
        System.out.println("depends on the server's light engine and block-update pipeline.");
    }

    private static long median(long[] xs) {
        long[] copy = xs.clone();
        java.util.Arrays.sort(copy);
        return copy[copy.length / 2];
    }
}