package com.oasissmp.technoblade;

import java.util.Optional;
import java.util.random.RandomGenerator;

/**
 * What a feed gives besides the golden apple: sometimes a gold nugget,
 * sometimes an effect, each rolled on its own, so one feed can give both.
 */
record Gifts(boolean nugget, Optional<Effect> effect) {

    enum Effect { LUCK, SPEED }

    /** The nugget comes {@code nuggetPercent} times in 100, and an effect {@code effectPercent} in 100. */
    static Gifts roll(RandomGenerator random, int nuggetPercent, int effectPercent) {
        boolean nugget = random.nextInt(100) < nuggetPercent;
        if (random.nextInt(100) >= effectPercent) {
            return new Gifts(nugget, Optional.empty());
        }
        return new Gifts(nugget, Optional.of(random.nextBoolean() ? Effect.LUCK : Effect.SPEED));
    }
}
