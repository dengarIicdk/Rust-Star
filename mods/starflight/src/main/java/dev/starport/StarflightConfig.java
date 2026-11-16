package dev.starport;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Tiny, dependency-free server config: {@code config/starflight.properties}.
 *
 * <p>Kept in a plain properties file rather than a config library so there is no
 * extra runtime dependency and the values are trivially inspectable. Every value
 * is validated by the Rust core after loading; an invalid file falls back to
 * defaults rather than producing a broken strike.
 */
public final class StarflightConfig {
    /** Field names are stable; renaming one silently resets that setting. */
    public static final String RADIUS = "radius";
    public static final String DAMAGE = "damage";
    public static final String SPEED = "speed";
    public static final String LIFETIME = "lifetimeTicks";
    public static final String ALLOW_WORLD_EDIT = "allowWorldEdit";
    public static final String MAX_ACTIVE_STRIKES = "maxActiveStrikes";

    /** Hard ceiling on concurrent strikes, independent of the config value. */
    public static final int ABSOLUTE_MAX_ACTIVE = 8;

    private static volatile StrikeTuning tuning = StrikeTuning.DEFAULT;
    private static volatile boolean allowWorldEdit = true;
    private static volatile int maxActiveStrikes = 4;

    private StarflightConfig() {}

    /** Loads (or creates) the config file. Safe to call more than once. */
    public static synchronized void load(Path configDir) {
        Path file = configDir.resolve("starflight.properties");
        Properties p = new Properties();
        try {
            Files.createDirectories(configDir);
            if (Files.exists(file)) {
                try (var in = Files.newInputStream(file)) {
                    p.load(in);
                }
            } else {
                p.setProperty(RADIUS, String.valueOf(StrikeTuning.DEFAULT.radius()));
                p.setProperty(DAMAGE, String.valueOf(StrikeTuning.DEFAULT.damage()));
                p.setProperty(SPEED, String.valueOf(StrikeTuning.DEFAULT.speed()));
                p.setProperty(LIFETIME, String.valueOf(StrikeTuning.DEFAULT.lifetimeTicks()));
                p.setProperty(ALLOW_WORLD_EDIT, "true");
                p.setProperty(MAX_ACTIVE_STRIKES, "4");
                try (var out = Files.newOutputStream(file)) {
                    p.store(out, "Starflight configuration. Invalid values fall back to defaults.");
                }
                StarflightMod.LOGGER.info("Wrote default config to {}", file);
            }
        } catch (IOException e) {
            StarflightMod.LOGGER.error("Could not read/write {}; using defaults", file, e);
            return;
        }

        StrikeTuning candidate = new StrikeTuning(
            parseDouble(p, RADIUS, StrikeTuning.DEFAULT.radius()),
            parseDouble(p, DAMAGE, StrikeTuning.DEFAULT.damage()),
            parseDouble(p, SPEED, StrikeTuning.DEFAULT.speed()),
            parseInt(p, LIFETIME, StrikeTuning.DEFAULT.lifetimeTicks()));

        StrikeTuning accepted = candidate.orDefault();
        if (accepted != candidate) {
            StarflightMod.LOGGER.warn(
                "Config tuning rejected by the native core (radius={} damage={} speed={} lifetime={}); "
                    + "using defaults",
                candidate.radius(), candidate.damage(), candidate.speed(), candidate.lifetimeTicks());
        }
        tuning = accepted;

        allowWorldEdit = Boolean.parseBoolean(p.getProperty(ALLOW_WORLD_EDIT, "true"));
        int max = parseInt(p, MAX_ACTIVE_STRIKES, 4);
        maxActiveStrikes = Math.max(1, Math.min(max, ABSOLUTE_MAX_ACTIVE));
        StarflightMod.LOGGER.info(
            "Config: radius={} damage={} speed={} lifetime={} worldEdit={} maxActive={}",
            tuning.radius(), tuning.damage(), tuning.speed(), tuning.lifetimeTicks(),
            allowWorldEdit, maxActiveStrikes);
    }

    public static StrikeTuning tuning() {
        return tuning;
    }

    public static boolean allowWorldEdit() {
        return allowWorldEdit;
    }

    public static int maxActiveStrikes() {
        return maxActiveStrikes;
    }

    private static double parseDouble(Properties p, String key, double fallback) {
        String raw = p.getProperty(key);
        if (raw == null) {
            return fallback;
        }
        try {
            return Double.parseDouble(raw.trim());
        } catch (NumberFormatException e) {
            StarflightMod.LOGGER.warn("Config value {}='{}' is not a number; using {}", key, raw, fallback);
            return fallback;
        }
    }

    private static int parseInt(Properties p, String key, int fallback) {
        String raw = p.getProperty(key);
        if (raw == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            StarflightMod.LOGGER.warn("Config value {}='{}' is not an integer; using {}", key, raw, fallback);
            return fallback;
        }
    }
}
