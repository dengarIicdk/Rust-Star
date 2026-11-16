package dev.starport;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.LightmapTextureManager;
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
            // Previously this pushed, translated and popped without drawing
            // anything, then called super.render (a no-op for a plain
            // EntityRenderer) — which is why the rocket was invisible in game.
            // Now it draws a textured, camera-facing quad scaled up so the
            // projectile reads clearly at range.
            matrices.push();
            // Lift the quad so the rocket sits centred on the entity origin
            // rather than half-sunk into the ground.
            matrices.translate(0.0, 0.5, 0.0);
            // Face the camera, exactly as a name tag does.
            matrices.multiply(this.dispatcher.getRotation());
            // Was 2.0, which read as a speck from the ground. The strike falls
            // from ~180 blocks up, so the projectile has to be large to be seen
            // at all on the way down.
            float scale = 8.0f;
            matrices.scale(-scale, -scale, scale);

            VertexConsumer vc = vertexConsumers.getBuffer(RenderLayer.getEntityTranslucentEmissive(this.getTexture(entity)));
            int full = LightmapTextureManager.MAX_LIGHT_COORDINATE;
            drawQuad(matrices, vc, full);

            matrices.pop();
            super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light);
        }

        /** Draws a 1x1 textured quad centred on the origin. */
        private static void drawQuad(MatrixStack matrices, VertexConsumer vc, int light) {
            MatrixStack.Entry e = matrices.peek();
            // Cheap, uniform normals: the shape is emissive and camera-facing,
            // so per-vertex lighting would add nothing.
            float n = 0.0f;
            vc.vertex(e, -0.5f, -0.5f, 0.0f).color(255, 255, 255, 255).texture(0.0f, 1.0f)
                .overlay(OverlayTexture.DEFAULT_UV).light(light).normal(e, n, n, 1.0f);
            vc.vertex(e, 0.5f, -0.5f, 0.0f).color(255, 255, 255, 255).texture(1.0f, 1.0f)
                .overlay(OverlayTexture.DEFAULT_UV).light(light).normal(e, n, n, 1.0f);
            vc.vertex(e, 0.5f, 0.5f, 0.0f).color(255, 255, 255, 255).texture(1.0f, 0.0f)
                .overlay(OverlayTexture.DEFAULT_UV).light(light).normal(e, n, n, 1.0f);
            vc.vertex(e, -0.5f, 0.5f, 0.0f).color(255, 255, 255, 255).texture(0.0f, 0.0f)
                .overlay(OverlayTexture.DEFAULT_UV).light(light).normal(e, n, n, 1.0f);
        }
    }
}