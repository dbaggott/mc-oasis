package com.oasissmp.technoblade;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Whom Technoblade is guarding: each player for a length of time from when
 * their guarding last started, kept in memory only.
 */
final class Guarding {

    private final Map<UUID, Instant> ends = new HashMap<>();

    void start(UUID player, Instant now, Duration length) {
        ends.values().removeIf(end -> !end.isAfter(now));
        ends.put(player, now.plus(length));
    }

    boolean guards(UUID player, Instant now) {
        Instant end = ends.get(player);
        return end != null && now.isBefore(end);
    }
}
