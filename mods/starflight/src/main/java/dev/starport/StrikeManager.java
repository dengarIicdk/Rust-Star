package dev.starport;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

/**
 * Owns every in-flight strike on the server.
 *
 * <p>Each strike is one native plan (a block coordinate stream) plus the world
 * it targets. Per tick, {@link #tick} gives every strike a fixed share of the
 * block budget, so a single catastrophic strike cannot starve the others.
 *
 * <p>Strike identity: a strike is keyed by the native plan slot. Slots come from
 * a small counter; a slot is reused only after {@code dropPlan}, so a stale
 * strike cannot apply another player's blocks.
 */
public final class StrikeManager {
    private static final int SLOT_BASE = 1_000;

    private static final List<Strike> ACTIVE = new ArrayList<>();
    private static int nextSlot = SLOT_BASE;

    private StrikeManager() {}

    static void register() {
        // Nothing to register yet; the tick hook is installed by the mod.
        // Placeholder kept so the entrypoint compiles if registration expands.
    }

    /** A queued strike being applied over multiple ticks. */
    public static final class Strike {
        final int slot;
        final ServerWorld world;
        final UUID owner;
        final long total;
        final long createdAtTick;
        long applied;
        /** Persisted request, so the strike can be rebuilt after a restart. */
        final Persistence.Pending request;

        Strike(int slot, ServerWorld world, UUID owner, long total, long createdAtTick,
               Persistence.Pending request) {
            this.slot = slot;
            this.world = world;
            this.owner = owner;
            this.total = total;
            this.createdAtTick = createdAtTick;
            this.request = request;
        }

        public int slot() {
            return slot;
        }

        public long total() {
            return total;
        }

        public long applied() {
            return applied;
        }

        public boolean isComplete() {
            return applied >= total || NativeBridge.planComplete(slot);
        }
    }

    /**
     * Queues a vertical cylindrical strike centred on {@code (x, z)}.
     *
     * @return the created strike, or {@code null} if the request was rejected
     *     (invalid geometry, or the native core refused the volume)
     */
    public static Strike queueBeam(
        ServerWorld world, UUID owner, double x, double z, double radius, int yMin, int yMax) {
        if (!Ops.isReady()) {
            return null;
        }
        // Refuse to grow the queue without bound: each strike holds a plan in the
        // native registry and consumes a share of every tick.
        if (ACTIVE.size() >= StarflightConfig.maxActiveStrikes()) {
            StarflightMod.LOGGER.warn(
                "Rejected strike: already {} active (max {})",
                ACTIVE.size(), StarflightConfig.maxActiveStrikes());
            return null;
        }
        if (!StarflightConfig.allowWorldEdit()) {
            StarflightMod.LOGGER.warn("Rejected strike: world editing disabled by config");
            return null;
        }
        int slot = nextSlot++;
        long total = NativeBridge.buildPlan(x, z, radius, yMin, yMax, slot);
        if (total <= 0) {
            NativeBridge.dropPlan(slot);
            StarflightMod.LOGGER.warn(
                "Rejected beam at ({}, {}) r={} y=[{},{}]: native returned {}",
                x, z, radius, yMin, yMax, total);
            return null;
        }
        Persistence.Pending request = new Persistence.Pending(
            x, z, radius, yMin, yMax, 0L, owner == null ? "" : owner.toString());
        Strike s = new Strike(slot, world, owner, total, world.getServer().getTicks(), request);
        ACTIVE.add(s);
        // Record the request so a crash mid-strike resumes rather than silently
        // leaving a half-carved beam behind.
        Persistence state = Persistence.get(world);
        state.add(request);
        return s;
    }

    /**
     * Re-queues strikes that were in progress when the world was last saved.
     * Called once on server start. Each request is rebuilt from scratch because
     * a beam plan is a pure function of its parameters.
     */
    public static int resumePending(MinecraftServer server) {
        if (!Ops.isReady()) {
            return 0;
        }
        ServerWorld world = server.getOverworld();
        Persistence state = Persistence.get(world);
        if (state.entries().isEmpty()) {
            return 0;
        }
        int resumed = 0;
        for (Persistence.Pending p : state.entries()) {
            int slot = nextSlot++;
            long total = NativeBridge.buildPlan(p.x(), p.z(), p.radius(), p.yMin(), p.yMax(), slot);
            if (total <= 0) {
                NativeBridge.dropPlan(slot);
                state.remove(p);
                continue;
            }
            UUID owner = p.owner().isEmpty() ? null : UUID.fromString(p.owner());
            ACTIVE.add(new Strike(slot, world, owner, total, server.getTicks(), p));
            resumed++;
        }
        StarflightMod.LOGGER.info("Resumed {} strike(s) from the saved world", resumed);
        return resumed;
    }

    /** Applies one tick of work to every active strike and retires finished ones. */
    public static void tick(net.minecraft.server.MinecraftServer server) {
        if (!Ops.isReady() || ACTIVE.isEmpty()) {
            return;
        }
        int budgetEach = Math.max(1, BlockApplier.MAX_PER_TICK / Math.max(1, ACTIVE.size()));
        Iterator<Strike> it = ACTIVE.iterator();
        while (it.hasNext()) {
            Strike s = it.next();
            int examined = BlockApplier.drain(s.world, s.slot, budgetEach);
            s.applied += examined;
            if (StarflightMod.DEBUG_STRIKES) {
                StarflightMod.LOGGER.info(
                    "strike slot={} applied={}/{} examined={} complete={}",
                    s.slot, s.applied, s.total, examined, NativeBridge.planComplete(s.slot));
            }
            if (examined == 0 || s.isComplete()) {
                NativeBridge.dropPlan(s.slot);
                // The strike is finished: drop its persisted request so a
                // restart does not replay it.
                Persistence.get(s.world).remove(s.request);
                it.remove();
            }
        }
    }

    /** Number of strikes currently being applied (diagnostics). */
    public static int activeCount() {
        return ACTIVE.size();
    }

    /** Cancels every strike and frees the native plans. Used on server stop. */
    public static void cancelAll() {
        for (Strike s : ACTIVE) {
            NativeBridge.dropPlan(s.slot);
            Persistence.get(s.world).remove(s.request);
        }
        ACTIVE.clear();
    }

    /** Convenience: strike the column at a player's crosshair position. */
    public static Strike queueBeamAt(net.minecraft.server.network.ServerPlayerEntity player, StrikeTuning tuning) {
        StrikeTuning t = tuning.orDefault();
        ServerWorld world = (ServerWorld) player.getWorld();
        BlockPos target = player.getBlockPos();
        return queueBeam(
            world,
            player.getUuid(),
            target.getX() + 0.5,
            target.getZ() + 0.5,
            t.radius(),
            world.getBottomY(),
            world.getTopY() - 1);
    }
}