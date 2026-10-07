package com.oasissmp.staff;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Checks that every prefix in the LuckPerms groups under
 * plugins/LuckPerms/yaml-storage/groups applies only on duty, by the context
 * OasisStaff gives: one under another key would never apply, and one without
 * a context would show off duty. Runs from the repo root.
 */
public final class StaffPrefixesTest {

    private static final Path GROUPS = Path.of("plugins/LuckPerms/yaml-storage/groups");
    private static final String ON_DUTY = "      " + OasisStaff.CONTEXT + ": 'on'";

    private StaffPrefixesTest() { }

    public static void main(String[] args) throws IOException {
        List<String> failures = new ArrayList<>();
        int prefixes = 0;
        List<Path> files;
        try (Stream<Path> listing = Files.list(GROUPS)) {
            files = listing.filter(file -> file.toString().endsWith(".yml")).sorted().toList();
        }
        for (Path file : files) {
            String prefix = null;
            boolean onDuty = false;
            boolean inPrefixes = false;
            for (String line : Files.readAllLines(file)) {
                if (!line.startsWith(" ") && !line.startsWith("- ")) {
                    inPrefixes = line.equals("prefixes:");
                    continue;
                }
                if (!inPrefixes) {
                    continue;
                }
                if (line.startsWith("- ")) {
                    if (prefix != null && !onDuty) {
                        failures.add(file + ": " + prefix + " doesn't apply only on duty");
                    }
                    prefix = line;
                    onDuty = false;
                    prefixes++;
                } else if (line.equals(ON_DUTY)) {
                    onDuty = true;
                }
            }
            if (prefix != null && !onDuty) {
                failures.add(file + ": " + prefix + " doesn't apply only on duty");
            }
        }
        if (prefixes == 0) {
            failures.add("no prefixes found in " + GROUPS);
        }
        if (!failures.isEmpty()) {
            failures.forEach(System.err::println);
            System.exit(1);
        }
        System.out.println(prefixes + " staff prefixes apply only on duty");
    }
}
