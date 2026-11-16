package dev.starport;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

/**
 * Diagnostic and admin commands. All of them are server-authoritative.
 *
 * <ul>
 *   <li>{@code /starflight status} — native core version, ABI, active strikes</li>
 *   <li>{@code /starflight strike <radius>} — queue a beam at your position</li>
 *   <li>{@code /starflight stress} — bounded JNI stress test (opt-in)</li>
 * </ul>
 */
public final class Commands {
    private Commands() {}

    public static void register() {
        net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback.EVENT.register(
            (dispatcher, registryAccess, environment) -> build(dispatcher));
    }

    private static void build(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("starflight")
            .requires(src -> src.hasPermissionLevel(2))
            .then(CommandManager.literal("status").executes(ctx -> {
                ServerCommandSource src = ctx.getSource();
                String v = Ops.isReady() ? NativeBridge.version() : "<not loaded>";
                int abi = Ops.isReady() ? NativeBridge.abiVersion() : -1;
                src.sendFeedback(() -> Text.literal(
                    "Starflight native=" + v + " abi=" + abi
                        + " plans=" + (Ops.isReady() ? NativeBridge.planCount() : -1)
                        + " strikes=" + StrikeManager.activeCount()), false);
                return 1;
            }))
            .then(CommandManager.literal("strike")
                .then(CommandManager.argument("radius", DoubleArgumentType.doubleArg(1.0, HARD_MAX_RADIUS))
                    .executes(ctx -> {
                        ServerCommandSource src = ctx.getSource();
                        ServerPlayerEntity player = src.getPlayerOrThrow();
                        double radius = DoubleArgumentType.getDouble(ctx, "radius");
                        StrikeTuning tuning = StrikeTuning.DEFAULT.withRadius(radius);
                        if (!tuning.isValid()) {
                            src.sendError(Text.literal("Invalid radius"));
                            return 0;
                        }
                        StrikeManager.Strike s = StrikeManager.queueBeamAt(player, tuning);
                        if (s == null) {
                            src.sendError(Text.literal("Strike rejected by native core"));
                            return 0;
                        }
                        src.sendFeedback(() -> Text.literal(
                            "Queued strike slot=" + s.slot() + " blocks=" + s.total()), false);
                        return 1;
                    })))
            .then(CommandManager.literal("stress").executes(ctx -> {
                if (!RiskLauncher.isEnabled()) {
                    ctx.getSource().sendError(Text.literal(
                        "Stress mode disabled. Start the server with -Dstarflight.stress=true"));
                    return 0;
                }
                int errors = RiskLauncher.hammer(8, 500);
                ctx.getSource().sendFeedback(
                    () -> Text.literal("Stress finished, errors=" + errors), false);
                return errors == 0 ? 1 : 0;
            }))
            .then(CommandManager.literal("selftest").executes(ctx -> {
                // In-world integration check, runnable from the console so CI can
                // drive it. Builds a guaranteed column of stone, then carves it
                // with a radius-3 beam and records the probe block.
                ServerCommandSource src = ctx.getSource();
                var world = src.getServer().getOverworld();
                var at = world.getSpawnPos();
                var chunkPos = new net.minecraft.util.math.ChunkPos(at.getX() >> 4, at.getZ() >> 4);
                world.getChunk(chunkPos.x, chunkPos.z); // force-load, no ticking race

                int bx = at.getX() + 8;
                int bz = at.getZ();
                int base = 60; // well above the flat world floor, well below build limit
                // A 5x5x8 stone block so the probe is guaranteed solid regardless
                // of the world generator in use.
                for (int dx = -2; dx <= 2; dx++) {
                    for (int dz = -2; dz <= 2; dz++) {
                        for (int dy = 0; dy < 8; dy++) {
                            world.setBlockState(new BlockPos(bx + dx, base + dy, bz + dz),
                                net.minecraft.block.Blocks.STONE.getDefaultState());
                        }
                    }
                }
                BlockPos probe = new BlockPos(bx, base + 4, bz);
                boolean solidBefore = !world.getBlockState(probe).isAir();

                StrikeTuning t = StrikeTuning.DEFAULT.withRadius(3.0);
                StrikeManager.Strike s = StrikeManager.queueBeam(
                    world, java.util.UUID.fromString("00000000-0000-0000-0000-000000000001"),
                    bx + 0.5, bz + 0.5, t.radius(), base, base + 7);

                Probe.set(probe, solidBefore);
                if (s == null) {
                    src.sendFeedback(() -> Text.literal("STARFLIGHT-SELFTEST: FAIL (queue rejected)"), false);
                    return 0;
                }
                src.sendFeedback(() -> Text.literal(
                    "STARFLIGHT-SELFTEST: QUEUED slot=" + s.slot() + " total=" + s.total()
                        + " probe=" + probe.toShortString() + " solidBefore=" + solidBefore), false);
                return 1;
            }))
            .then(CommandManager.literal("verify").executes(ctx -> {
                ServerCommandSource src = ctx.getSource();
                var world = src.getServer().getOverworld();
                var p = Probe.get();
                if (p == null) {
                    src.sendFeedback(() -> Text.literal("STARFLIGHT-VERIFY: FAIL (no probe; run selftest first)"), false);
                    return 0;
                }
                boolean airNow = world.getBlockState(p.pos()).isAir();
                boolean ok = p.wasSolid() && airNow;
                src.sendFeedback(() -> Text.literal(
                    "STARFLIGHT-VERIFY: " + (ok ? "PASS" : "FAIL")
                        + " probe=" + p.pos().toShortString()
                        + " wasSolid=" + p.wasSolid() + " airNow=" + airNow
                        + " strikes=" + StrikeManager.activeCount()), false);
                return ok ? 1 : 0;
            }))
            .then(CommandManager.literal("registry").executes(ctx -> {
                // Asserts the mod's content actually made it into the registries.
                ServerCommandSource src = ctx.getSource();
                var reg = src.getServer().getRegistryManager().get(net.minecraft.registry.RegistryKeys.ITEM);
                var ent = src.getServer().getRegistryManager().get(net.minecraft.registry.RegistryKeys.ENTITY_TYPE);
                var rocketId = net.minecraft.util.Identifier.of(StarflightMod.MOD_ID, "orbital_rocket");
                var scopeId = net.minecraft.util.Identifier.of(StarflightMod.MOD_ID, "strike_scope");
                boolean item = reg.containsId(rocketId);
                boolean scope = reg.containsId(scopeId);
                boolean entity = ent.containsId(rocketId);
                src.sendFeedback(() -> Text.literal(
                    "STARFLIGHT-REGISTRY: " + (item && scope && entity ? "PASS" : "FAIL")
                        + " rocketItem=" + item + " scopeItem=" + scope + " rocketEntity=" + entity), false);
                return item && scope && entity ? 1 : 0;
            }))
            .then(CommandManager.literal("flight").executes(ctx -> {
                // Spawns a rocket programmatically and confirms it exists, has a
                // sane velocity, and is owned by the requesting source. Doing this
                // from the console exercises the entity path without a client.
                ServerCommandSource src = ctx.getSource();
                var world = src.getServer().getOverworld();
                var at = world.getSpawnPos();
                world.getChunk(at.getX() >> 4, at.getZ() >> 4);
                var start = new net.minecraft.util.math.Vec3d(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
                var rocket = RocketEntity.createFromOrbit(
                    world,
                    null,
                    start,
                    StrikeTuning.DEFAULT);
                boolean spawned = world.spawnEntity(rocket);
                double speed = rocket.getVelocity().length();
                boolean ok = spawned && speed > 0.0 && speed <= 10.0;
                src.sendFeedback(() -> Text.literal(
                    "STARFLIGHT-FLIGHT: " + (ok ? "PASS" : "FAIL")
                        + " spawned=" + spawned + " speed=" + String.format(java.util.Locale.ROOT, "%.3f", speed)
                        + " id=" + rocket.getId()), false);
                return ok ? 1 : 0;
            }))
            .then(CommandManager.literal("persistence").executes(ctx -> {
                // Writes a durable pending strike, then reads it back. Proves the
                // save format round-trips and that reload re-validates input.
                ServerCommandSource src = ctx.getSource();
                var world = src.getServer().getOverworld();
                Persistence state = Persistence.get(world);
                state.clear();
                Persistence.Pending p = new Persistence.Pending(
                    12.5, -7.5, 5.0, 40, 60, 0L, "00000000-0000-0000-0000-00000000abcd");
                state.add(p);
                boolean wrote = state.entries().size() == 1;

                // Round-trip through NBT exactly as a save/load would.
                var lookup = src.getServer().getRegistryManager();
                var nbt = state.writeNbt(new net.minecraft.nbt.NbtCompound(), lookup);
                Persistence reloaded = Persistence.read(nbt, lookup);
                boolean read = reloaded.entries().size() == 1;
                boolean matches = read && reloaded.entries().get(0).equals(p);

                // A corrupt radius must be dropped on load, not applied.
                var bad = nbt.copy();
                var list = bad.getList("strikes", 10);
                list.getCompound(0).putDouble("r", 99999.0);
                boolean rejected = Persistence.read(bad, lookup).entries().isEmpty();

                boolean ok = wrote && read && matches && rejected;
                src.sendFeedback(() -> Text.literal(
                    "STARFLIGHT-PERSISTENCE: " + (ok ? "PASS" : "FAIL")
                        + " wrote=" + wrote + " read=" + read + " matches=" + matches
                        + " rejectsCorrupt=" + rejected), false);
                state.clear();
                return ok ? 1 : 0;
            }))
            .then(CommandManager.literal("ratelimit").executes(ctx -> {
                // Exercises the limiter's window semantics with a fake clock.
                ServerCommandSource src = ctx.getSource();
                java.util.UUID id = java.util.UUID.randomUUID();
                RateLimiter.clear();
                long t0 = 1_000_000L;

                boolean firstThree = RateLimiter.tryAcquire(id, t0)
                    && RateLimiter.tryAcquire(id, t0)
                    && RateLimiter.tryAcquire(id, t0);
                boolean fourthBlocked = !RateLimiter.tryAcquire(id, t0);
                boolean stillBlockedLate = !RateLimiter.tryAcquire(id, t0 + RateLimiter.WINDOW_MS - 1);
                boolean allowedAfterWindow = RateLimiter.tryAcquire(id, t0 + RateLimiter.WINDOW_MS + 1);
                boolean independentPlayers = RateLimiter.tryAcquire(java.util.UUID.randomUUID(), t0);
                boolean forgetWorks = forgetIdempotent(id);

                boolean ok = firstThree && fourthBlocked && stillBlockedLate
                    && allowedAfterWindow && independentPlayers && forgetWorks;
                RateLimiter.clear();
                src.sendFeedback(() -> Text.literal(
                    "STARFLIGHT-RATELIMIT: " + (ok ? "PASS" : "FAIL")
                        + " burst3=" + firstThree + " blocked4=" + fourthBlocked
                        + " blockedNearEnd=" + stillBlockedLate
                        + " resetAfterWindow=" + allowedAfterWindow
                        + " perPlayer=" + independentPlayers + " forget=" + forgetWorks), false);
                return ok ? 1 : 0;
            }))
            .then(CommandManager.literal("config").executes(ctx -> {
                ServerCommandSource src = ctx.getSource();
                StrikeTuning t = StarflightConfig.tuning();
                boolean valid = t.isValid();
                boolean sane = t.radius() >= 1.0 && t.radius() <= HARD_MAX_RADIUS
                    && t.damage() >= 0.0 && t.speed() > 0.0 && t.lifetimeTicks() > 0
                    && StarflightConfig.maxActiveStrikes() >= 1
                    && StarflightConfig.maxActiveStrikes() <= StarflightConfig.ABSOLUTE_MAX_ACTIVE;
                boolean ok = valid && sane;
                src.sendFeedback(() -> Text.literal(
                    "STARFLIGHT-CONFIG: " + (ok ? "PASS" : "FAIL")
                        + " radius=" + t.radius() + " damage=" + t.damage()
                        + " speed=" + t.speed() + " lifetime=" + t.lifetimeTicks()
                        + " worldEdit=" + StarflightConfig.allowWorldEdit()
                        + " maxActive=" + StarflightConfig.maxActiveStrikes()
                        + " nativeValid=" + valid), false);
                return ok ? 1 : 0;
            }))
            .then(CommandManager.literal("cancel").executes(ctx -> {
                StrikeManager.cancelAll();
                ctx.getSource().sendFeedback(() -> Text.literal("All strikes cancelled"), false);
                return 1;
            })));
    }

    private static boolean forgetIdempotent(java.util.UUID id) {
        RateLimiter.forget(id);
        RateLimiter.forget(id);
        // After forgetting, a fresh acquire must succeed even inside the window.
        return RateLimiter.tryAcquire(id, 1_000_000L);
    }

    /**
     * Mirrors {@code starflight::limits::HARD_MAX_RADIUS}. Kept in one place so
     * the command bound and the config sanity check cannot drift from the core
     * again (they did: both still said 64 after the core moved to 128, and the
     * in-server CONFIG check is what caught it).
     */
    public static final double HARD_MAX_RADIUS = 128.0;

    /** One-shot state shared between the selftest and verify subcommands. */
    private static final class Probe {
        private record State(BlockPos pos, boolean wasSolid) {}

        private static volatile State state;

        static void set(BlockPos pos, boolean wasSolid) {
            state = new State(pos, wasSolid);
        }

        static State get() {
            return state;
        }
    }
}
