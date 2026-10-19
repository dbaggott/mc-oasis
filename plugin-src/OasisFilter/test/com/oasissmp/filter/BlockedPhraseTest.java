package com.oasissmp.filter;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Checks which part of a blocked message, as the word lists in
 * plugins/OasisFilter block it, is shown to its player. Runs from the repo root.
 */
public final class BlockedPhraseTest {

    private static final PlayerNames NAMES = new PlayerNames(List.of("Bastard_Raiser"));

    private static final Map<String, String> CASES = Map.ofEntries(
            Map.entry("nigger", "nigger"),
            Map.entry("hey you NIGGER lol", "NIGGER"),
            Map.entry("  you're   a bitch  ", "bitch"),
            Map.entry("ok n1gg3r, bye", "n1gg3r,"),
            Map.entry("lol nig nog haha", "nig nog"),
            Map.entry("hi n i g g e r ok", "n i g g e r"),
            Map.entry("/msg Steve you faggot", "faggot"),
            Map.entry("Bastard_Raiser you bastard", "bastard"),
            Map.entry("spic and kike", "spic"));

    private BlockedPhraseTest() { }

    public static void main(String[] args) throws IOException {
        WordFilter filter = WordFilter.load(Path.of("plugins/OasisFilter"));
        List<String> failures = new ArrayList<>();
        CASES.forEach((text, expected) -> {
            String phrase = BlockedPhrase.in(text, part -> filter.blocks(NAMES.removeFrom(part)));
            if (!phrase.equals(expected)) {
                failures.add("'" + text + "' showed '" + phrase + "', not '" + expected + "'");
            }
        });
        if (!failures.isEmpty()) {
            failures.forEach(System.err::println);
            System.exit(1);
        }
        System.out.println(CASES.size() + " blocked phrase cases right");
    }
}
