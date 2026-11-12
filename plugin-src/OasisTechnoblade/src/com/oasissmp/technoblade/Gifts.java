package com.oasissmp.technoblade;

import java.util.Optional;
import java.util.random.RandomGenerator;

/**
 * What a feed gives besides the golden apple: sometimes a gold nugget,
 * sometimes an effect, each rolled on its own, so one feed can give both.
 */
record Gifts(boolean nugget, Optional<Effect> effect) {

    enum Effect { LUCK, SPEED }

    /** The nugget is 1 chance in {@code nuggetOneIn}, and an effect 1 in {@code effectOneIn}. */
    static Gifts roll(RandomGenerator random, int nuggetOneIn, int effectOneIn) {
        boolean nugget = random.nextInt(nuggetOneIn) == 0;
        if (random.nextInt(effectOneIn) != 0) {
            return new Gifts(nugget, Optional.empty());
        }
        return new Gifts(nugget, Optional.of(random.nextBoolean() ? Effect.LUCK : Effect.SPEED));
    }
}
