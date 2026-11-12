package com.oasissmp.technoblade;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Checks that a player is guarded for the full length from when their
 * guarding last started and not after, that starting it again restarts the
 * length, and that players are guarded apart.
 */
public final class GuardingTest {

    private static final UUID ALEX = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID STEVE = UUID.fromString("00000000-0000-0000-0000-000000000002");

    private GuardingTest() { }

    public static void main(String[] args) {
        List<String> failures = new ArrayList<>();
        Guarding guarding = new Guarding();
        Instant start = Instant.ofEpochSecond(1_790_000_000L);

        guards(failures, guarding, ALEX, start, false, "a player who was never fed from");
        guarding.start(ALEX, start, Duration.ofMinutes(5));
        guards(failures, guarding, ALEX, start, true, "a player as their guarding starts");
        guards(failures, guarding, ALEX, start.plusSeconds(299), true, "a player a second before it ends");
        guards(failures, guarding, ALEX, start.plusSeconds(300), false, "a player as it ends");
        guards(failures, guarding, STEVE, start.plusSeconds(60), false, "another player meanwhile");

        guarding.start(STEVE, start.plusSeconds(200), Duration.ofMinutes(5));
        guards(failures, guarding, ALEX, start.plusSeconds(250), true, "the first player after a second's started");
        guards(failures, guarding, STEVE, start.plusSeconds(499), true, "the second player after the first's ended");
        guards(failures, guarding, STEVE, start.plusSeconds(500), false, "the second player as theirs ends");

        guarding.start(ALEX, start.plusSeconds(600), Duration.ofMinutes(5));
        guarding.start(ALEX, start.plusSeconds(700), Duration.ofMinutes(5));
        guards(failures, guarding, ALEX, start.plusSeconds(999), true, "a player whose guarding started again");
        guards(failures, guarding, ALEX, start.plusSeconds(1000), false, "a player as the later guarding ends");

        if (!failures.isEmpty()) {
            failures.forEach(System.err::println);
            System.exit(1);
        }
        System.out.println("GuardingTest passed");
    }

    private static void guards(List<String> failures, Guarding guarding, UUID player, Instant now, boolean expected,
            String what) {
        if (guarding.guards(player, now) != expected) {
            failures.add(what + ": " + (expected ? "not guarded" : "guarded"));
        }
    }
}
