package com.oasissmp.egg;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Checks that sightings and their sources read back as written, that the
 * log keeps each source's latest sightings across a reload, and that a
 * damaged line is set aside rather than stopping the rest loading.
 */
public final class SightingLogTest {

    private static final Instant TIME = Instant.ofEpochSecond(1_790_000_000L);

    private SightingLogTest() { }

    public static void main(String[] args) throws IOException {
        List<String> failures = new ArrayList<>();

        Source player = new Source.Player(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        Source blocks = new Source.Blocks("minecraft:the_end", -1, 0);
        Source entities = new Source.Entities("minecraft:overworld", 250, -3);
        for (Source source : List.of(player, blocks, entities)) {
            check(failures, Source.parse(source.key()).equals(source), "source didn't read back: " + source.key());
        }

        Sighting nested = new Sighting(blocks, "in a shulker box in a chest", 2, "minecraft:the_end",
                -5, 64, 3, TIME);
        check(failures, Sighting.parse(nested.line()).equals(nested), "sighting didn't read back: " + nested.line());

        Path file = Files.createTempDirectory("oasisegg").resolve("OasisEgg/sightings.txt");
        SightingLog log = SightingLog.load(file);
        check(failures, log.all().isEmpty(), "a log with no file has sightings: " + log.all());

        Sighting held = new Sighting(player, "in Someone's inventory", 1, "minecraft:overworld", 10, 70, 10, TIME);
        log.replace(player, List.of(held));
        log.replace(blocks, List.of(nested));
        check(failures, SightingLog.load(file).all().equals(List.of(held, nested)),
                "sightings didn't survive a reload: " + SightingLog.load(file).all());

        Sighting moved = new Sighting(player, "in Someone's ender chest", 1, "minecraft:overworld",
                12, 70, 10, TIME.plusSeconds(60));
        log.replace(player, List.of(moved));
        log.replace(blocks, List.of());
        check(failures, SightingLog.load(file).all().equals(List.of(moved)),
                "a source's new sightings didn't replace its old ones: " + SightingLog.load(file).all());

        try {
            log.replace(entities, List.of(moved));
            failures.add("a sighting was logged under another source than its own");
        } catch (IllegalArgumentException expected) {
            // A sighting kept under the wrong source would outlive the egg moving.
        }

        Files.write(file, List.of(moved.line(), "player not-a-uuid", nested.line().replace("\t2\t", "\tmany\t")));
        SightingLog damaged = SightingLog.load(file);
        check(failures, damaged.all().equals(List.of(moved)) && damaged.unreadable().size() == 2,
                "a damaged file didn't keep its good lines and set the rest aside: "
                        + damaged.all() + " " + damaged.unreadable());

        if (!failures.isEmpty()) {
            failures.forEach(System.err::println);
            System.exit(1);
        }
        System.out.println("egg sightings read back and replace by source");
    }

    private static void check(List<String> failures, boolean passed, String failure) {
        if (!passed) {
            failures.add(failure);
        }
    }
}
