package dev.starport;

/**
 * Immutable, validated tuning for one strike. Mirrors {@code starflight::limits::Tuning}.
 *
 * <p>All values are validated by the Rust core before use, so a corrupted or
 * malicious packet cannot produce a NaN speed or an absurd radius.
 */
public record StrikeTuning(double radius, double damage, double speed, int lifetimeTicks) {

    public static final StrikeTuning DEFAULT =
        new StrikeTuning(8.0, 60.0, 2.5, 400);

    /** Returns the clamp/validate verdict from the native core. */
    public int validate() {
        return NativeBridge.validateTuning(radius, damage, speed, lifetimeTicks);
    }

    public boolean isValid() {
        return validate() == NativeBridge.OK;
    }

    /** Returns this tuning if valid, otherwise {@link #DEFAULT}. */
    public StrikeTuning orDefault() {
        return isValid() ? this : DEFAULT;
    }

    public StrikeTuning withRadius(double r) {
        return new StrikeTuning(r, damage, speed, lifetimeTicks);
    }
}