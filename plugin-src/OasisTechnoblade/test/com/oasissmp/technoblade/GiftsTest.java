package com.oasissmp.technoblade;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Checks that, at odds of 1 in 10 each, a feed's nugget and effect each come
 * on 1 roll of the 10, rolled apart so that a feed can give neither, either or
 * both, and that the effect is Luck or Speed.
 */
public final class GiftsTest {

    private GiftsTest() { }

    public static void main(String[] args) {
        List<String> failures = new ArrayList<>();

        int nuggets = 0;
        int effects = 0;
        for (int roll = 0; roll < 10; roll++) {
            Scripted nugget = new Scripted().thenInts(roll, 1);
            if (Gifts.roll(nugget, 10, 10).nugget()) {
                nuggets++;
            }
            Scripted effect = new Scripted().thenInts(1, roll).thenBooleans(true);
            if (Gifts.roll(effect, 10, 10).effect().isPresent()) {
                effects++;
            }
            if (!nugget.bounds.equals(List.of(10, 10)) || !effect.bounds.equals(List.of(10, 10))) {
                failures.add("the rolls are out of " + nugget.bounds + " and " + effect.bounds + ", not 10 and 10");
            }
        }
        if (nuggets != 1 || effects != 1) {
            failures.add(nuggets + " of the 10 nugget rolls and " + effects + " of the 10 effect rolls give one,"
                    + " not 1 of each");
        }

        gives(failures, new Scripted().thenInts(5, 5), new Gifts(false, Optional.empty()), "neither roll coming up");
        gives(failures, new Scripted().thenInts(0, 5), new Gifts(true, Optional.empty()), "only the nugget's");
        gives(failures, new Scripted().thenInts(5, 0).thenBooleans(true),
                new Gifts(false, Optional.of(Gifts.Effect.LUCK)), "only the effect's");
        gives(failures, new Scripted().thenInts(0, 0).thenBooleans(false),
                new Gifts(true, Optional.of(Gifts.Effect.SPEED)), "both");

        Set<Gifts.Effect> seen = EnumSet.noneOf(Gifts.Effect.class);
        for (boolean choice : new boolean[] {true, false}) {
            seen.add(Gifts.roll(new Scripted().thenInts(1, 0).thenBooleans(choice), 10, 10).effect().orElseThrow());
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
