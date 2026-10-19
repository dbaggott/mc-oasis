package com.oasissmp.voice;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Checks when a player counts as muted. */
public final class MutesTest {

    private static final Instant NOW = Instant.parse("2026-10-18T12:00:00Z");

    private MutesTest() { }

    public static void main(String[] args) {
        List<String> failures = new ArrayList<>();
        Mutes mutes = new Mutes();
        UUID player = UUID.randomUUID();

        check(failures, !mutes.isMuted(player, NOW), "a player never muted isn't muted");

        mutes.mute(player, NOW.plusSeconds(60));
        check(failures, mutes.isMuted(player, NOW), "a timed mute holds before its end");
        check(failures, !mutes.isMuted(player, NOW.plusSeconds(60)), "a timed mute ends at its end");

        mutes.mute(player, Instant.MAX);
        check(failures, mutes.isMuted(player, NOW.plusSeconds(1_000_000_000L)),
                "a permanent mute never ends");

        mutes.unmute(player);
        check(failures, !mutes.isMuted(player, NOW), "an unmuted player isn't muted");

        if (!failures.isEmpty()) {
            failures.forEach(System.err::println);
            System.exit(1);
        }
        System.out.println("mute cases right");
    }

    private static void check(List<String> failures, boolean ok, String expectation) {
        if (!ok) {
            failures.add(expectation);
        }
    }
}
