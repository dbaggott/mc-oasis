package com.oasissmp.technoblade;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Checks that a player's turn starts their wait, that a turn during it is
 * refused with the time left and doesn't extend it, that it ends at its full
 * length, and that players wait apart.
 */
public final class CooldownTest {

    private static final UUID ALEX = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID STEVE = UUID.fromString("00000000-0000-0000-0000-000000000002");

    private CooldownTest() { }

    public static void main(String[] args) {
        List<String> failures = new ArrayList<>();
        Cooldown cooldown = new Cooldown();
        Instant start = Instant.ofEpochSecond(1_790_000_000L);

        takes(failures, cooldown, ALEX, start, Optional.empty(), "a first turn");
        takes(failures, cooldown, ALEX, start.plusSeconds(60), Optional.of(Duration.ofMinutes(4)),
                "a turn a minute in");
        takes(failures, cooldown, STEVE, start.plusSeconds(60), Optional.empty(), "another player's first turn");
        takes(failures, cooldown, ALEX, start.plusSeconds(299), Optional.of(Duration.ofSeconds(1)),
                "a turn a second before the wait ends");
        takes(failures, cooldown, ALEX, start.plusSeconds(300), Optional.empty(), "a turn as the wait ends");
        takes(failures, cooldown, ALEX, start.plusSeconds(301), Optional.of(Duration.ofSeconds(299)),
                "a turn a second into the next wait");

        if (!failures.isEmpty()) {
            failures.forEach(System.err::println);
            System.exit(1);
        }
        System.out.println("CooldownTest passed");
    }

    private static void takes(List<String> failures, Cooldown cooldown, UUID player, Instant now,
            Optional<Duration> expected, String what) {
        Optional<Duration> wait = cooldown.take(player, now, Duration.ofMinutes(5));
        if (!wait.equals(expected)) {
            failures.add(what + ": " + wait + ", not " + expected);
        }
    }
}
