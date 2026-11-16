package dev.starport;

import java.util.List;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * The orbital rocket: right-click with it to launch a strike at your crosshair.
 *
 * <p>All authority checks happen here, server-side only. The client never
 * decides that a strike happened.
 */
public final class RocketItem extends Item {
    /** Per-player cooldown after a successful launch. */
    public static final int COOLDOWN_TICKS = 20 * 6;

    public RocketItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (world.isClient) {
            // Client only plays the swing; the server decides.
            return TypedActionResult.success(stack);
        }
        if (!(user instanceof ServerPlayerEntity player)) {
            return TypedActionResult.fail(stack);
        }
        if (player.getItemCooldownManager().isCoolingDown(this)) {
            return TypedActionResult.fail(stack);
        }
        // The item cooldown is client-visible and can be bypassed; this is the
        // authoritative limit.
        long now = System.currentTimeMillis();
        if (!RateLimiter.tryAcquire(player.getUuid(), now)) {
            player.sendMessage(Text.translatable("starflight.error.rate_limited",
                RateLimiter.remaining(player.getUuid(), now)), true);
            return TypedActionResult.fail(stack);
        }

        Vec3d dir = player.getRotationVec(1.0f);
        if (dir.lengthSquared() < 1.0e-9) {
            return TypedActionResult.fail(stack);
        }

        StrikeTuning tuning = StarflightConfig.tuning();
        if (!tuning.isValid()) {
            player.sendMessage(Text.translatable("starflight.error.invalid_tuning"), true);
            return TypedActionResult.fail(stack);
        }

        // Launch from slightly in front of the player so the rocket does not
        // spawn inside their own hitbox.
        Vec3d start = player.getPos().add(0, player.getStandingEyeHeight() - 0.15, 0).add(dir.multiply(1.2));
        RocketEntity rocket = RocketEntity.create(
            (net.minecraft.server.world.ServerWorld) world, player, start, dir, tuning);
        if (rocket == null) {
            player.sendMessage(Text.translatable("starflight.error.invalid_direction"), true);
            return TypedActionResult.fail(stack);
        }
        world.spawnEntity(rocket);

        player.getItemCooldownManager().set(this, COOLDOWN_TICKS);
        player.sendMessage(Text.translatable("starflight.launched", tuning.radius()), true);
        return TypedActionResult.success(stack);
    }

    @Override
    public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.translatable("item.starflight.orbital_rocket.tooltip"));
    }
}