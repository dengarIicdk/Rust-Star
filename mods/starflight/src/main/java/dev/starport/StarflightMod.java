package dev.starport;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main entrypoint for the Starflight mod.
 *
 * <p>Responsibilities: register the rocket item and entity, and install the
 * server tick hook that applies pending Rust block plans. All heavy math is in
 * the Rust core via {@link NativeBridge}.
 */
public final class StarflightMod implements ModInitializer {
    public static final String MOD_ID = "starflight";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("Starflight initialising");

        // Wire the native core. Failure here is fatal-but-clean: the mod refuses
        // to register anything rather than running half-working.
        boolean nativeOk = Ops.initialise();
        if (!nativeOk) {
            LOGGER.error("Starflight native core unavailable; items and entities will NOT be registered.");
            return;
        }

        ModItems.register();
        ModEntities.register();
        StrikeManager.register();
        Commands.register();

        ServerTickEvents.END_SERVER_TICK.register(StrikeManager::tick);
        LOGGER.info("Starflight ready (native core {})", NativeBridge.version());
    }
}