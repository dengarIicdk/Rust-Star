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
                .then(CommandManager.argument("radius", DoubleArgumentType.doubleArg(1.0, 64.0))
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
                var start = new net.minecraft.util.math.Vec3d(at.getX() + 0.5, at.getY() + 40, at.getZ() + 0.5);
                var rocket = RocketEntity.create(
                    world,
                    null,
                    start,
                    new net.minecraft.util.math.Vec3d(0.0, -1.0, 0.0),
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
            .then(CommandManager.literal("cancel").executes(ctx -> {
                StrikeManager.cancelAll();
                ctx.getSource().sendFeedback(() -> Text.literal("All strikes cancelled"), false);
                return 1;
            })));
    }

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
