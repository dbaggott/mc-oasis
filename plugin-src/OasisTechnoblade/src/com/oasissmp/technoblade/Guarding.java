package com.oasissmp.technoblade;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Whom Technoblade is guarding: each player for one length of time from when
 * their guarding last started, kept in memory only.
 */
final class Guarding {

    private final Duration length;
    private final Map<UUID, Instant> ends = new HashMap<>();

    Guarding(Duration length) {
        this.length = length;
    }

    void start(UUID player, Instant now) {
        ends.values().removeIf(end -> !end.isAfter(now));
        ends.put(player, now.plus(length));
    }

    boolean guards(UUID player, Instant now) {
        Instant end = ends.get(player);
        return end != null && now.isBefore(end);
    }
}
