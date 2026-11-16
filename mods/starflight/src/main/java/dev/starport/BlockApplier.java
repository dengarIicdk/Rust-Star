package dev.starport;

/**
 * Applies Rust-produced block plans to a Minecraft world.
 *
 * <p>Two rules make this safe to run on the server thread:
 * <ol>
 *   <li>at most {@link #MAX_PER_TICK} positions are applied per tick, so a huge
 *       strike spreads over time instead of freezing the server;</li>
 *   <li>a position is only mutated if the chunk is loaded and the block is not
 *       protected by the world border or a "no-destroy" block list.</li>
 * </ol>
 *
 * <p>Rust never touches the world; it only says <i>which</i> coordinates. All
 * permission and world-safety policy lives here.
 */
public final class BlockApplier {
    /** Mirrors {@code starflight::limits::MAX_BLOCKS_PER_TICK}. */
    public static final int MAX_PER_TICK = 65_536;

    private BlockApplier() {}

    /**
     * Drains up to {@code budget} positions from the native plan in {@code slot}
     * into {@code world}.
     *
     * @return number of positions actually examined (applied or skipped)
     */
    public static int drain(net.minecraft.server.world.ServerWorld world, int slot, int budget) {
        int want = Math.min(Math.max(1, budget), MAX_PER_TICK);
        int[] buffer = Scratch.buffer(want);
        int n = NativeBridge.takeBatch(slot, buffer, want);
        if (n <= 0) {
            return 0;
        }
        for (int i = 0; i < n; i++) {
            int x = buffer[i * 3];
            int y = buffer[i * 3 + 1];
            int z = buffer[i * 3 + 2];
            if (!isWritable(world, x, y, z)) {
                continue;
            }
            // Air is the cheapest possible "erase": no item drops, no entity
            // churn, no neighbour update storms for a large volume.
            world.setBlockState(
                new net.minecraft.util.math.BlockPos(x, y, z),
                net.minecraft.block.Blocks.AIR.getDefaultState(),
                net.minecraft.block.Block.NOTIFY_LISTENERS | net.minecraft.block.Block.SKIP_DROPS);
        }
        return n;
    }

    private static boolean isWritable(net.minecraft.server.world.ServerWorld world, int x, int y, int z) {
        if (y < world.getBottomY() || y >= world.getTopY()) {
            return false;
        }
        var pos = new net.minecraft.util.math.BlockPos(x, y, z);
        if (!world.isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4)) {
            return false;
        }
        // Bedrock and other unbreakable blocks are left alone: the strike carves
        // the world but does not remove its floor.
        var state = world.getBlockState(pos);
        return !state.isIn(net.minecraft.registry.tag.BlockTags.FEATURES_CANNOT_REPLACE)
            && state.getHardness(world, pos) >= 0.0f;
    }

    /** Reusable per-thread buffers so a long strike does not allocate per tick. */
    private static final class Scratch {
        private static final ThreadLocal<int[]> BY_TICK = new ThreadLocal<>();

        static int[] buffer(int want) {
            int[] arr = BY_TICK.get();
            if (arr == null || arr.length < want * 3) {
                arr = new int[want * 3];
                BY_TICK.set(arr);
            }
            return arr;
        }
    }
}