package com.oasissmp.spawn;

import com.sk89q.worldedit.util.SideEffect;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Checks when OasisSpawn pastes, the seed hash against values from
 * {@code printf %s <seed> | shasum -a 256}, the paste's side effects, and that
 * the repo's plugins/OasisSpawn/spawn.seed-sha256 reads. Runs from the repo root.
 */
public final class SpawnDecisionTest {

    private static final long SEED = 1234567890123456789L;
    private static final String SEED_HASH = "22085aa929bcd7af4b23d9d9c046a1d4fde8be51f79d91392efafef96574ab01";
    private static final long NEGATIVE_SEED = -4172144997902289642L;
    private static final String NEGATIVE_SEED_HASH = "c8805189a2e8dc7300685d634bd1ae5655249640d3620b57bd5760b89ed135b2";

    private SpawnDecisionTest() { }

    public static void main(String[] args) throws IOException {
        List<String> failures = new ArrayList<>();

        check(failures, SpawnDecision.seedHash(SEED).equals(SEED_HASH), "the seed's hash: " + SpawnDecision.seedHash(SEED));
        check(failures, SpawnDecision.seedHash(NEGATIVE_SEED).equals(NEGATIVE_SEED_HASH),
                "the negative seed's hash: " + SpawnDecision.seedHash(NEGATIVE_SEED));

        decides(failures, "a new world on the seed", false, 0, SEED, SEED_HASH, SpawnDecision.PASTE);
        decides(failures, "a new world on the negative seed", false, 0, NEGATIVE_SEED, NEGATIVE_SEED_HASH,
                SpawnDecision.PASTE);
        decides(failures, "a world just short of a day old", false, SpawnDecision.NEW_WORLD_TICKS - 1, SEED, SEED_HASH,
                SpawnDecision.PASTE);
        decides(failures, "a world with the marker", true, 0, SEED, SEED_HASH, SpawnDecision.ALREADY_DECIDED);
        decides(failures, "a day-old world on the seed, as the live world is", false, SpawnDecision.NEW_WORLD_TICKS,
                SEED, SEED_HASH, SpawnDecision.NOT_NEW);
        decides(failures, "an old world on the seed", false, 50_000_000, SEED, SEED_HASH, SpawnDecision.NOT_NEW);
        decides(failures, "a new world on another seed", false, 0, NEGATIVE_SEED, SEED_HASH, SpawnDecision.OTHER_SEED);

        List<SideEffect> on = Arrays.stream(SideEffect.values())
                .filter(effect -> OasisSpawn.PASTE_SIDE_EFFECTS.getState(effect) == SideEffect.State.ON).toList();
        // NETWORK, sending the changes to players in range, is on in every
        // set WorldEdit makes, none() included.
        check(failures, on.equals(List.of(SideEffect.LIGHTING, SideEffect.POI_UPDATE, SideEffect.NETWORK)),
                "the paste's side effects on: " + on);
        check(failures, Arrays.stream(SideEffect.values())
                        .allMatch(effect -> OasisSpawn.PASTE_SIDE_EFFECTS.getState(effect) != SideEffect.State.DELAYED),
                "a paste side effect is delayed");

        check(failures, SpawnDecision.parseSeedHash(SEED_HASH + "\n").equals(SEED_HASH), "a hash file ending in a newline");
        rejects(failures, "an uppercase hash", SEED_HASH.toUpperCase());
        rejects(failures, "a short hash", SEED_HASH.substring(1));
        rejects(failures, "an empty hash file", "");
        SpawnDecision.parseSeedHash(Files.readString(Path.of("plugins/OasisSpawn/spawn.seed-sha256")));

        if (!failures.isEmpty()) {
            failures.forEach(System.err::println);
            System.exit(1);
        }
        System.out.println("OasisSpawn decides as expected; the repo's spawn.seed-sha256 reads");
    }

    private static void decides(List<String> failures, String what, boolean marked, long gameTime, long seed,
            String spawnSeedHash, SpawnDecision expected) {
        SpawnDecision decision = SpawnDecision.decide(marked, gameTime, seed, spawnSeedHash);
        check(failures, decision == expected, what + ": " + decision + ", not " + expected);
    }

    private static void rejects(List<String> failures, String what, String contents) {
        try {
            SpawnDecision.parseSeedHash(contents);
            failures.add(what + " was accepted");
        } catch (IllegalArgumentException expected) {
            // OasisSpawn refuses to enable rather than compare against a malformed hash.
        }
    }

    private static void check(List<String> failures, boolean passed, String failure) {
        if (!passed) {
            failures.add(failure);
        }
    }
}
