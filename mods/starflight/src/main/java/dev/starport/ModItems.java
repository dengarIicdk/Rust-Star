package dev.starport;

import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.minecraft.util.Rarity;

/** Registers the mod's items. */
public final class ModItems {
    public static final Item ORBITAL_ROCKET = new RocketItem(
        new Item.Settings()
            // One per stack: launching consumes the item and it must not be
            // duplicated by a stack-split bug.
            .maxCount(1)
            .rarity(Rarity.EPIC)
            .fireproof()
    );

    public static final Item STRIKE_SCOPE = new StrikeScopeItem(
        new Item.Settings().maxCount(1).rarity(Rarity.RARE)
    );

    private ModItems() {}

    public static void register() {
        register("orbital_rocket", ORBITAL_ROCKET);
        register("strike_scope", STRIKE_SCOPE);

        ItemGroupEvents.modifyEntriesEvent(ItemGroups.COMBAT).register(entries -> {
            entries.add(ORBITAL_ROCKET);
            entries.add(STRIKE_SCOPE);
        });
    }

    private static void register(String path, Item item) {
        Registry.register(Registries.ITEM, Identifier.of(StarflightMod.MOD_ID, path), item);
    }
}