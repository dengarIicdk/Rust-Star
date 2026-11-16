package dev.starport;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;

/**
 * Durable record of strikes that were still in progress when the world saved.
 *
 * <p>A strike is up to 2 M block positions spread over many ticks. Writing them
 * all would bloat the save file, so this stores only the <i>request</i> (centre,
 * radius, height range, progress) and rebuilds the plan on load. That is exact
 * because beam plans are a pure function of their parameters.
 *
 * <p>This is separate from documentation state: losing it means a partially
 * applied strike resumes instead of vanishing, which is a gameplay bug, so it is
 * covered by its own test.
 */
public final class Persistence extends PersistentState {
    /** Must bump when the stored layout changes incompatibly. */
    public static final int FORMAT = 1;
    public static final String KEY = "starflight_strikes";

    private static final String K_STRIKES = "strikes";

    /** A resumable strike request. */
    public record Pending(
        double x, double z, double radius, int yMin, int yMax, long applied, String owner) {}

    private final List<Pending> pending = new ArrayList<>();

    /** Declares how the manager constructs and reloads this state. */
    public static final PersistentState.Type<Persistence> TYPE = new PersistentState.Type<>(
        Persistence::new, Persistence::read, net.minecraft.datafixer.DataFixTypes.STRUCTURE);

    public static Persistence get(ServerWorld world) {
        return world.getServer().getOverworld().getPersistentStateManager()
            .getOrCreate(TYPE, KEY);
    }

    public List<Pending> entries() {
        return List.copyOf(pending);
    }

    public void add(Pending p) {
        pending.add(p);
        markDirty();
    }

    public void remove(Pending p) {
        if (pending.remove(p)) {
            markDirty();
        }
    }

    public void clear() {
        if (!pending.isEmpty()) {
            pending.clear();
            markDirty();
        }
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt, net.minecraft.registry.RegistryWrapper.WrapperLookup lookup) {
        nbt.putInt("format", FORMAT);
        NbtList list = new NbtList();
        for (Pending p : pending) {
            NbtCompound c = new NbtCompound();
            c.putDouble("x", p.x());
            c.putDouble("z", p.z());
            c.putDouble("r", p.radius());
            c.putInt("y0", p.yMin());
            c.putInt("y1", p.yMax());
            c.putLong("applied", p.applied());
            c.putString("owner", p.owner());
            list.add(c);
        }
        nbt.put(K_STRIKES, list);
        return nbt;
    }

    /** Reads a persisted state, ignoring anything it does not understand. */
    public static Persistence read(NbtCompound nbt, net.minecraft.registry.RegistryWrapper.WrapperLookup lookup) {
        Persistence p = new Persistence();
        int format = nbt.getInt("format");
        if (format != FORMAT) {
            // Unknown format: start empty rather than guess. A partial strike is
            // better than a corrupt world edit.
            return p;
        }
        NbtList list = nbt.getList(K_STRIKES, 10);
        for (int i = 0; i < list.size(); i++) {
            NbtCompound c = list.getCompound(i);
            double r = c.getDouble("r");
            double x = c.getDouble("x");
            double z = c.getDouble("z");
            // Re-validate on load: a hand-edited save must not be able to inject
            // an out-of-bounds strike.
            if (NativeBridge.validateBeam(x, z, r, c.getInt("y0"), c.getInt("y1")) != NativeBridge.OK) {
                continue;
            }
            p.pending.add(new Pending(x, z, r, c.getInt("y0"), c.getInt("y1"),
                c.getLong("applied"), c.getString("owner")));
        }
        return p;
    }

    /** Convenience for logging/tests: the anchor of the first pending strike. */
    public BlockPos firstAnchor() {
        if (pending.isEmpty()) {
            return null;
        }
        Pending p = pending.get(0);
        return new BlockPos((int) Math.floor(p.x()), p.yMin(), (int) Math.floor(p.z()));
    }
}
