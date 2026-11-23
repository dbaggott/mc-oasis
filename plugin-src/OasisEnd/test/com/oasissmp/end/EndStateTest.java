package com.oasissmp.end;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Checks that the End is disabled with no saved state, that enabling and
 * disabling read back as saved, and that a file saying anything else reads as
 * disabled and is reported.
 */
public final class EndStateTest {

    private EndStateTest() { }

    public static void main(String[] args) throws IOException {
        List<String> failures = new ArrayList<>();
        List<String> unreadable = new ArrayList<>();
        Path file = Files.createTempDirectory("oasisend").resolve("OasisEnd/" + EndState.FILE_NAME);

        check(failures, !EndState.load(file, unreadable::add), "the End read as enabled with no saved state");
        EndState.save(file, true);
        check(failures, EndState.load(file, unreadable::add), "the End read as disabled after it was enabled");
        EndState.save(file, false);
        check(failures, !EndState.load(file, unreadable::add), "the End read as enabled after it was disabled");
        check(failures, unreadable.isEmpty(), "a saved state was reported unreadable: " + unreadable);

        Files.write(file, List.of("open"));
        check(failures, !EndState.load(file, unreadable::add) && unreadable.equals(List.of("open")),
                "a file saying 'open' didn't read as disabled and get reported: " + unreadable);

        if (!failures.isEmpty()) {
            failures.forEach(System.err::println);
            System.exit(1);
        }
        System.out.println("EndStateTest passed");
    }

    private static void check(List<String> failures, boolean ok, String failure) {
        if (!ok) {
            failures.add(failure);
        }
    }
}
