package dev.starport;

import java.util.List;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

/**
 * A targeting scope: reports how large a strike would be at the block you are
 * looking at, without performing it. Useful to confirm a blast before firing.
 */
public final class StrikeScopeItem extends Item {
    public static final int RANGE = 160;

    public StrikeScopeItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (world.isClient) {
            return TypedActionResult.success(stack);
        }
        if (!(user instanceof ServerPlayerEntity player)) {
            return TypedActionResult.fail(stack);
        }
        ServerWorld sw = (ServerWorld) world;
        HitResult hit = sw.raycast(new RaycastContext(
            player.getEyePos(),
            player.getEyePos().add(player.getRotationVec(1.0f).multiply(RANGE)),
            RaycastContext.ShapeType.COLLIDER,
            RaycastContext.FluidHandling.NONE,
            player));
        if (hit.getType() != HitResult.Type.BLOCK) {
            player.sendMessage(Text.translatable("starflight.scope.no_target"), true);
            return TypedActionResult.fail(stack);
        }
        BlockPos target = ((BlockHitResult) hit).getBlockPos();
        StrikeTuning t = StarflightConfig.tuning();
        long count = NativeBridge.beamBlockCount(
            target.getX() + 0.5, target.getZ() + 0.5, t.radius(),
            sw.getBottomY(), sw.getTopY() - 1);
        player.sendMessage(Text.translatable("starflight.scope.report", target.toShortString(), count), true);
        return TypedActionResult.success(stack);
    }

    @Override
    public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.translatable("item.starflight.strike_scope.tooltip"));
    }
}