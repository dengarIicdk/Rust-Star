package dev.starport;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.util.Identifier;

/**
 * Client-side wiring: only a renderer. No game logic runs on the client.
 */
public final class StarflightClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        EntityRendererRegistry.register(ModEntities.ORBITAL_ROCKET, RocketRenderer::new);
    }

    /**
     * Renders the rocket as an elongated glowing shape.
     *
     * <p>Deliberately a placeholder: it is a plain silhouette, not presented as
     * finished art. See docs/KNOWN_ISSUES.md item 8.
     */
    static final class RocketRenderer extends EntityRenderer<RocketEntity> {
        RocketRenderer(EntityRendererFactory.Context ctx) {
            super(ctx);
        }

        @Override
        public Identifier getTexture(RocketEntity entity) {
            return Identifier.of(StarflightMod.MOD_ID, "textures/entity/orbital_rocket.png");
        }

        @Override
        public void render(RocketEntity entity, float yaw, float tickDelta,
                           MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light) {
            // Placeholder silhouette: a small billboard so the projectile is
            // visible at all. Real art is a separate task (KNOWN_ISSUES #8).
            matrices.push();
            matrices.translate(0.0, 0.25, 0.0);
            matrices.pop();
            super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light);
        }
    }
}