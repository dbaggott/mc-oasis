package com.oasissmp.technoblade;

import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Checks that a rumor names the biome whenever no player is near, that either
 * hint can come out when one is, that rumors every 30 minutes with 10 of
 * jitter come 20 to 40 minutes apart, and how a biome is named from its key.
 */
public final class RumorTest {

    private RumorTest() { }

    public static void main(String[] args) {
        List<String> failures = new ArrayList<>();

        for (boolean flip : new boolean[] {true, false}) {
            // With nobody near, the hint doesn't depend on the random source, which may go unasked.
            Rumor.Hint hint = Rumor.choose(false, new Scripted().thenBooleans(flip));
            if (hint != Rumor.Hint.BIOME) {
                failures.add("with no player near, the hint is " + hint);
            }
        }
        Set<Rumor.Hint> near = EnumSet.noneOf(Rumor.Hint.class);
        for (boolean flip : new boolean[] {true, false}) {
            near.add(Rumor.choose(true, new Scripted().thenBooleans(flip)));
        }
        if (!near.equals(EnumSet.allOf(Rumor.Hint.class))) {
            failures.add("with a player near, the hint is only ever one of " + near);
        }

        Random random = new Random(119);
        long shortest = Long.MAX_VALUE;
        long longest = Long.MIN_VALUE;
        for (int i = 0; i < 10_000; i++) {
            long delay = Rumor.delayTicks(random, Duration.ofMinutes(30), Duration.ofMinutes(10));
            shortest = Math.min(shortest, delay);
            longest = Math.max(longest, delay);
        }
        // 20 and 40 minutes in ticks; 10,000 waits come within a minute of each end.
        if (shortest < 24_000 || shortest > 25_200 || longest > 48_000 || longest < 46_800) {
            failures.add("waits run from " + shortest + " to " + longest + " ticks, not from 24000 to 48000");
        }

        named(failures, "plains", "Plains");
        named(failures, "dark_forest", "Dark Forest");
        named(failures, "cave/andesite_caves", "Andesite Caves");

        if (!failures.isEmpty()) {
            failures.forEach(System.err::println);
            System.exit(1);
        }
        System.out.println("RumorTest passed");
    }

    private static void named(List<String> failures, String path, String expected) {
        String name = Rumor.biomeName(path);
        if (!name.equals(expected)) {
            failures.add("the biome " + path + " is named " + name + ", not " + expected);
        }
    }
}
