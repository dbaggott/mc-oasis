package com.oasissmp.technoblade;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** A wait per player between one turn and the next, kept in memory only. */
final class Cooldown {

    private final Map<UUID, Instant> ends = new HashMap<>();

    /**
     * Takes {@code player}'s turn at {@code now} and starts their wait of
     * {@code length}, or, if they are still waiting, leaves it as it is and
     * says how long is left.
     */
    Optional<Duration> take(UUID player, Instant now, Duration length) {
        ends.values().removeIf(end -> !end.isAfter(now));
        Instant end = ends.get(player);
        if (end != null) {
            return Optional.of(Duration.between(now, end));
        }
        ends.put(player, now.plus(length));
        return Optional.empty();
    }
}
