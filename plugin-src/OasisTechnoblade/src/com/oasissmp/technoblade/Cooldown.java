package com.oasissmp.technoblade;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** A wait of one length per player between one turn and the next, kept in memory only. */
final class Cooldown {

    private final Duration length;
    private final Map<UUID, Instant> ends = new HashMap<>();

    Cooldown(Duration length) {
        this.length = length;
    }

    /**
     * Takes {@code player}'s turn at {@code now} and starts their wait, or,
     * if they are still waiting, leaves it as it is and says how long is left.
     */
    Optional<Duration> take(UUID player, Instant now) {
        ends.values().removeIf(end -> !end.isAfter(now));
        Instant end = ends.get(player);
        if (end != null) {
            return Optional.of(Duration.between(now, end));
        }
        ends.put(player, now.plus(length));
        return Optional.empty();
    }
}
