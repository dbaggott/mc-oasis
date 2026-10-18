package com.oasissmp.filter;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Checks that players' names are taken out of text before the word lists in
 * plugins/OasisFilter see it, and nothing else is. Runs from the repo root.
 */
public final class PlayerNamesTest {

    private static final PlayerNames NAMES =
            new PlayerNames(List.of("Bastard_Raiser", "Steve")).with("Arse_Daniel");

    private static final Map<String, Boolean> BLOCKED = Map.ofEntries(
            Map.entry("/msg Bastard_Raiser hi", false),
            Map.entry("hi bastard_raiser", false),
            Map.entry("BASTARD_RAISER, over here", false),
            Map.entry("/ban Arse_Daniel", false),
            Map.entry("bastard", true),
            Map.entry("Bastard_Raiser you bastard", true),
            Map.entry("Bastard_Raiser2", true),
            Map.entry("Bastard_Raiser_x", true),
            Map.entry("Bastard Raiser", true));

    private PlayerNamesTest() { }

    public static void main(String[] args) throws IOException {
        WordFilter filter = WordFilter.load(Path.of("plugins/OasisFilter"));
        List<String> failures = new ArrayList<>();
        BLOCKED.forEach((text, expected) -> {
            if (filter.blocks(NAMES.removeFrom(text)) != expected) {
                failures.add("'" + text + "' should " + (expected ? "" : "not ") + "be blocked");
            }
        });
        if (NAMES.with("steve") != NAMES) {
            failures.add("adding a name already there, in any case, should change nothing");
        }
        if (!failures.isEmpty()) {
            failures.forEach(System.err::println);
            System.exit(1);
        }
        System.out.println(BLOCKED.size() + " player name cases right");
    }
}
