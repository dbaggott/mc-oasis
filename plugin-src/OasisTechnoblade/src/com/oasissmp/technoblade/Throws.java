package com.oasissmp.technoblade;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * The players Technoblade has thrown and who haven't landed yet: the fall
 * from a throw doesn't hurt. A throw is over once the player is back on the
 * ground after leaving it, and in any case {@link #LASTS_TICKS} after it
 * started, so a later fall of their own isn't spared.
 */
final class Throws {

    static final int LASTS_TICKS = 200;

    private record Thrown(int ends, boolean leftGround) { }

    private final Map<UUID, Thrown> thrown = new HashMap<>();

    void start(UUID player, int tick) {
        thrown.put(player, new Thrown(tick + LASTS_TICKS, false));
    }

    /** Whether a fall {@code player} takes at {@code tick} is a throw's, which ends that throw. */
    boolean endsWithFall(UUID player, int tick) {
        Thrown ended = thrown.remove(player);
        return ended != null && tick < ended.ends();
    }

    /** Ends the throws that have landed or run out by {@code tick}; called every tick while any is on. */
    void land(int tick, Predicate<UUID> onGround) {
        thrown.entrySet().removeIf(entry -> tick >= entry.getValue().ends()
                || (entry.getValue().leftGround() && onGround.test(entry.getKey())));
        thrown.replaceAll((player, mark) -> mark.leftGround() || onGround.test(player)
                ? mark : new Thrown(mark.ends(), true));
    }

    boolean isEmpty() {
        return thrown.isEmpty();
    }
}
