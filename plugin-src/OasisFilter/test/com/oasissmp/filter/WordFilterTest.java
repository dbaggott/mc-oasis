package com.oasissmp.filter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Checks the word lists in plugins/OasisFilter against cases.txt. Runs from the
 * repo root, and exits non-zero on any case the filter gets wrong.
 */
public final class WordFilterTest {

    private static final Path LISTS = Path.of("plugins/OasisFilter");
    private static final Path CASES = Path.of("plugin-src/OasisFilter/test/cases.txt");

    private WordFilterTest() { }

    public static void main(String[] args) throws IOException {
        WordFilter filter = WordFilter.load(LISTS);
        List<String> failures = new ArrayList<>();
        int cases = 0;
        List<String> lines = Files.readAllLines(CASES);
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }
            String where = CASES + ":" + (i + 1);
            String[] parts = line.split(" ", 2);
            if (parts.length != 2 || !(parts[0].equals("block") || parts[0].equals("pass"))) {
                failures.add(where + ": not 'block <text>' or 'pass <text>'");
                continue;
            }
            boolean blocked = filter.blocks(parts[1]);
            if (blocked != parts[0].equals("block")) {
                failures.add(where + ": " + (blocked ? "blocked" : "passed") + " '" + parts[1] + "'");
            }
            cases++;
        }
        if (!failures.isEmpty()) {
            failures.forEach(System.err::println);
            System.exit(1);
        }
        System.out.println(cases + " filter cases right");
    }
}
