package com.oasissmp.technoblade;

import java.time.Duration;
import java.util.Arrays;
import java.util.random.RandomGenerator;
import java.util.stream.Collectors;

/** The rules of a rumor: how long until the next, and what it says Technoblade was seen by. */
final class Rumor {

    enum Hint { PLAYER, BIOME }

    private Rumor() { }

    /** The ticks until the next rumor: {@code every}, give or take up to {@code jitter}, and never none. */
    static long delayTicks(RandomGenerator random, Duration every, Duration jitter) {
        return random.nextLong(Settings.ticks(every.minus(jitter)), Settings.ticks(every.plus(jitter)) + 1);
    }

    /** Either hint when a player is near enough to name, and otherwise the biome. */
    static Hint choose(boolean playerNearby, RandomGenerator random) {
        return playerNearby && random.nextBoolean() ? Hint.PLAYER : Hint.BIOME;
    }

    /**
     * A biome's name from its key's path, for a client with no translation of it:
     * {@code dark_forest} is Dark Forest.
     */
    static String biomeName(String path) {
        return Arrays.stream(path.substring(path.lastIndexOf('/') + 1).split("_"))
                .filter(word -> !word.isEmpty())
                .map(word -> Character.toUpperCase(word.charAt(0)) + word.substring(1))
                .collect(Collectors.joining(" "));
    }
}
