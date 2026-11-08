package com.oasissmp.spawn;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.regex.Pattern;

/**
 * What OasisSpawn does with a world when it starts: once per world, it pastes
 * spawn only into a world that is new and has the seed spawn was captured in,
 * and otherwise leaves the world alone for good.
 */
enum SpawnDecision {

    /** The marker is there: this world was decided at an earlier start. */
    ALREADY_DECIDED,
    /** The world existed before this plugin did, the live world among them. */
    NOT_NEW,
    /** A new world on some other seed, such as the smoke test's random one. */
    OTHER_SEED,
    PASTE;

    /**
     * One in-game day. A newly generated world reaches this plugin at game
     * time 0, since plugins enable before the first tick.
     */
    static final long NEW_WORLD_TICKS = 24_000;

    private static final Pattern SHA_256_HEX = Pattern.compile("[0-9a-f]{64}");

    /**
     * {@code gameTime} is ticks since the world was generated
     * ({@code World.getGameTime()}). Not {@code getFullTime()}: that is the day
     * clock, which {@code /time set} puts back, so the live world could read
     * as new.
     */
    static SpawnDecision decide(boolean marked, long gameTime, long seed, String spawnSeedHash) {
        if (marked) {
            return ALREADY_DECIDED;
        }
        if (gameTime >= NEW_WORLD_TICKS) {
            return NOT_NEW;
        }
        if (!seedHash(seed).equals(spawnSeedHash)) {
            return OTHER_SEED;
        }
        return PASTE;
    }

    /**
     * The lowercase hex SHA-256 of the seed as {@link Long#toString(long)}
     * prints it, which is what {@code spawn.seed-sha256} holds.
     */
    static String seedHash(long seed) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(Long.toString(seed).getBytes(StandardCharsets.US_ASCII)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Every Java platform has SHA-256", e);
        }
    }

    /** The hash in a {@code spawn.seed-sha256} file's contents, which may end in a newline. */
    static String parseSeedHash(String contents) {
        String hash = contents.strip();
        if (!SHA_256_HEX.matcher(hash).matches()) {
            throw new IllegalArgumentException("The seed hash isn't 64 lowercase hex digits");
        }
        return hash;
    }
}
