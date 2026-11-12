package com.oasissmp.technoblade;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Checks that, at 10 percent each, a feed's nugget and effect each come on 10
 * rolls of the 100, which is 1 in 10, rolled apart so that a feed can give
 * neither, either or both, that 0 percent never gives and 100 always does,
 * and that the effect is Luck or Speed.
 */
public final class GiftsTest {

    private GiftsTest() { }

    public static void main(String[] args) {
        List<String> failures = new ArrayList<>();

        int nuggets = 0;
        int effects = 0;
        for (int roll = 0; roll < 100; roll++) {
            Scripted nugget = new Scripted().thenInts(roll, 99);
            if (Gifts.roll(nugget, 10, 10).nugget()) {
                nuggets++;
            }
            Scripted effect = new Scripted().thenInts(99, roll).thenBooleans(true);
            if (Gifts.roll(effect, 10, 10).effect().isPresent()) {
                effects++;
            }
            if (!nugget.bounds.equals(List.of(100, 100)) || !effect.bounds.equals(List.of(100, 100))) {
                failures.add("the rolls are out of " + nugget.bounds + " and " + effect.bounds
                        + ", not 100 and 100");
            }
        }
        if (nuggets != 10 || effects != 10) {
            failures.add(nuggets + " of the 100 nugget rolls and " + effects + " of the 100 effect rolls give one,"
                    + " not 10 of each");
        }
        if (Gifts.roll(new Scripted().thenInts(0, 0), 0, 0).nugget()
                || !Gifts.roll(new Scripted().thenInts(99, 0).thenBooleans(true), 100, 0).nugget()) {
            failures.add("a nugget came at 0 percent, or didn't at 100");
        }

        gives(failures, new Scripted().thenInts(50, 50), new Gifts(false, Optional.empty()), "neither roll coming up");
        gives(failures, new Scripted().thenInts(0, 50), new Gifts(true, Optional.empty()), "only the nugget's");
        gives(failures, new Scripted().thenInts(50, 0).thenBooleans(true),
                new Gifts(false, Optional.of(Gifts.Effect.LUCK)), "only the effect's");
        gives(failures, new Scripted().thenInts(0, 0).thenBooleans(false),
                new Gifts(true, Optional.of(Gifts.Effect.SPEED)), "both");

        Set<Gifts.Effect> seen = EnumSet.noneOf(Gifts.Effect.class);
        for (boolean choice : new boolean[] {true, false}) {
            seen.add(Gifts.roll(new Scripted().thenInts(50, 0).thenBooleans(choice), 10, 10).effect().orElseThrow());
        }
        if (!seen.equals(EnumSet.allOf(Gifts.Effect.class))) {
            failures.add("the effect is only ever one of " + seen);
        }

        if (!failures.isEmpty()) {
            failures.forEach(System.err::println);
            System.exit(1);
        }
        System.out.println("GiftsTest passed");
    }

    private static void gives(List<String> failures, Scripted random, Gifts expected, String what) {
        Gifts gifts = Gifts.roll(random, 10, 10);
        if (!gifts.equals(expected)) {
            failures.add(what + ": " + gifts + ", not " + expected);
        }
    }
}
