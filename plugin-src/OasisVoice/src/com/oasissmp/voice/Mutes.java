package com.oasissmp.voice;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Which online players are muted, and until when. Read by the voice thread for
 * every packet a player sends, so it's answered from memory and never waits.
 */
final class Mutes {

    private final Map<UUID, Instant> ends = new ConcurrentHashMap<>();

    /** Mutes the player until {@code end}, which is {@link Instant#MAX} for a permanent mute. */
    void mute(UUID player, Instant end) {
        ends.put(player, end);
    }

    void unmute(UUID player) {
        ends.remove(player);
    }

    boolean isMuted(UUID player, Instant now) {
        Instant end = ends.get(player);
        return end != null && now.isBefore(end);
    }
}
