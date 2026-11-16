package dev.starport;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

/** Registers entities. */
public final class ModEntities {
    public static final EntityType<RocketEntity> ORBITAL_ROCKET = Registry.register(
        Registries.ENTITY_TYPE,
        Identifier.of(StarflightMod.MOD_ID, "orbital_rocket"),
        FabricEntityTypeBuilder.<RocketEntity>create(SpawnGroup.MISC, RocketEntity::new)
            // Small source, long collision reach is handled by the per-tick
            // raycast rather than a huge AABB (which would lag chunk lookups).
            .dimensions(EntityDimensions.fixed(0.4f, 0.4f))
            .trackRangeBlocks(256)
            .trackedUpdateRate(1)
            .build());

    private ModEntities() {}

    public static void register() {
        // Static initialiser performs the registration.
    }
}