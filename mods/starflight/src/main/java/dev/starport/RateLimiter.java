package dev.starport;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side rate limiting for strike requests.
 *
 * <p>The client-side item cooldown is a hint, not a control: a modified client
 * can ignore it entirely. This limiter runs on the server and counts *accepted*
 * requests per player over a sliding window, so spamming packets cannot queue
 * dozens of multi-million-block strikes.
 *
 * <p>Deliberately per-player rather than global: one abusive player must not be
 * able to lock out everyone else, but must also not be able to consume the whole
 * server's strike budget.
 */
public final class RateLimiter {
    /** Requests allowed per player per window. */
    public static final int MAX_PER_WINDOW = 3;
    /** Window length in milliseconds. */
    public static final long WINDOW_MS = 30_000;

    private record Window(long windowStart, int count) {}

    private static final Map<UUID, Window> WINDOWS = new ConcurrentHashMap<>();

    private RateLimiter() {}

    /**
     * @return {@code true} if the request may proceed; records it if so
     */
    public static boolean tryAcquire(UUID player, long nowMs) {
        Window w = WINDOWS.get(player);
        if (w == null || nowMs - w.windowStart() > WINDOW_MS) {
            WINDOWS.put(player, new Window(nowMs, 1));
            return true;
        }
        if (w.count() >= MAX_PER_WINDOW) {
            return false;
        }
        WINDOWS.put(player, new Window(w.windowStart(), w.count() + 1));
        return true;
    }

    /** Remaining requests in the current window. */
    public static int remaining(UUID player, long nowMs) {
        Window w = WINDOWS.get(player);
        if (w == null || nowMs - w.windowStart() > WINDOW_MS) {
            return MAX_PER_WINDOW;
        }
        return Math.max(0, MAX_PER_WINDOW - w.count());
    }

    /** Drops state for players who left, so the map cannot grow unbounded. */
    public static void forget(UUID player) {
        WINDOWS.remove(player);
    }

    public static void clear() {
        WINDOWS.clear();
    }
}
