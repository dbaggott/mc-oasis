package com.oasissmp.voice;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Which online players are muted, and until when. Read by the voice thread for
 * every packet a player sends, so it's answered from memory and never waits.
 */
final class Mutes {

    private final Map<UUID, Instant> ends = new ConcurrentHashMap<>();
    // Muted players who've been told their voice isn't heard, so they're told once a mute.
    private final Set<UUID> told = ConcurrentHashMap.newKeySet();

    /** Mutes the player until {@code end}, which is {@link Instant#MAX} for a permanent mute. */
    void mute(UUID player, Instant end) {
        ends.put(player, end);
        told.remove(player);
    }

    void unmute(UUID player) {
        ends.remove(player);
        told.remove(player);
    }

    boolean isMuted(UUID player, Instant now) {
        Instant end = ends.get(player);
        return end != null && now.isBefore(end);
    }

    /** True the first time it's asked for each mute, after that false. */
    boolean tell(UUID player) {
        return told.add(player);
    }
}
