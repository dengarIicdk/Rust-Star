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
                // drive it. Picks a fixed point near spawn and carves a column
                // there, then records what the probe block was beforehand.
                ServerCommandSource src = ctx.getSource();
                var world = src.getServer().getOverworld();
                var origin = src.getServer().getOverworld().getSpawnPos();
                world.getChunk(origin.getX() >> 4, origin.getZ() >> 4); // force-load
                var probe = new BlockPos(origin.getX() + 8, world.getTopY() - 1, origin.getZ());
                probe = world.getTopPosition(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, probe).down();
                boolean solidBefore = !world.getBlockState(probe).isAir();
                StrikeTuning t = StrikeTuning.DEFAULT.withRadius(3.0);
                StrikeManager.Strike s = StrikeManager.queueBeam(
                    world, java.util.UUID.fromString("00000000-0000-0000-0000-000000000001"),
                    probe.getX() + 0.5, probe.getZ() + 0.5,
                    t.radius(), probe.getY() - 6, probe.getY() + 2);
                final var probePos = probe;
                final boolean before = solidBefore;
                if (s == null) {
                    src.sendFeedback(() -> Text.literal("STARFLIGHT-SELFTEST: FAIL (queue rejected)"), false);
                    return 0;
                }
                Probe.set(probePos, before);
                src.sendFeedback(() -> Text.literal(
                    "STARFLIGHT-SELFTEST: QUEUED slot=" + s.slot() + " total=" + s.total()
                        + " probe=" + probePos.toShortString() + " solidBefore=" + before), false);
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
