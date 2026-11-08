package com.oasissmp.technoblade;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Checks that the pig record reads back as saved, and that first-spawn.txt
 * reads, the repo's plugins/OasisTechnoblade/first-spawn.txt included. Runs
 * from the repo root.
 */
public final class RecordsTest {

    private RecordsTest() { }

    public static void main(String[] args) throws IOException {
        List<String> failures = new ArrayList<>();

        Path file = Files.createTempDirectory("oasistechnoblade").resolve("OasisTechnoblade/" + PigRecord.FILE_NAME);
        check(failures, PigRecord.load(file).isEmpty(), "a missing file read as a record");
        PigRecord record = new PigRecord(UUID.fromString("00000000-0000-0000-0000-000000000001"),
                "minecraft:overworld", new Pos(-480, 64, 880), Instant.ofEpochSecond(1_790_000_000L));
        record.save(file);
        check(failures, PigRecord.load(file).equals(Optional.of(record)), "the record didn't read back: " + record.line());
        Files.write(file, List.of("not a record"));
        try {
            PigRecord.load(file);
            failures.add("a damaged record read");
        } catch (IllegalArgumentException expected) {
            // OasisTechnoblade places a new pig instead.
        }

        check(failures, FirstSpawn.parse(List.of("# a comment", "", " -480 880 ")).equals(new FirstSpawn(-480, 880)),
                "an x z line didn't read");
        for (List<String> bad : List.<List<String>>of(List.of(), List.of("1 2", "3 4"), List.of("1,2"), List.of("1 2 3"))) {
            try {
                FirstSpawn.parse(bad);
                failures.add("first-spawn.txt read from " + bad);
            } catch (IllegalArgumentException expected) {
                // The plugin doesn't enable without a column to fall back to.
            }
        }
        FirstSpawn repo = FirstSpawn.load(Path.of("plugins/OasisTechnoblade"));

        if (!failures.isEmpty()) {
            failures.forEach(System.err::println);
            System.exit(1);
        }
        System.out.println("RecordsTest passed; the repo's first spawn is " + repo);
    }

    private static void check(List<String> failures, boolean ok, String failure) {
        if (!ok) {
            failures.add(failure);
        }
    }
}
