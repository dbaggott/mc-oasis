package com.oasissmp.rules;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Checks how Rules reads rules.txt and when its fingerprint changes, and that
 * the repo's plugins/OasisRules/rules.txt loads. Runs from the repo root.
 */
public final class RulesTest {

    private RulesTest() { }

    public static void main(String[] args) throws IOException {
        List<String> failures = new ArrayList<>();

        Rules rules = Rules.parse(List.of("# a comment", "", "  Be kind.  ", "No griefing."));
        check(failures, rules.numbered().equals(List.of("1. Be kind.", "2. No griefing.")),
                "comments and blank lines left out, rules stripped and numbered: " + rules.numbered());

        check(failures, rules.fingerprint().equals(
                        Rules.parse(List.of("Be kind.", "# another comment", "No griefing.")).fingerprint()),
                "a comment or blank line changed the fingerprint");
        check(failures, !rules.fingerprint().equals(
                        Rules.parse(List.of("Be kind.", "No griefing!")).fingerprint()),
                "a rule's wording didn't change the fingerprint");
        check(failures, !rules.fingerprint().equals(
                        Rules.parse(List.of("No griefing.", "Be kind.")).fingerprint()),
                "reordering the rules didn't change the fingerprint");

        try {
            Rules.parse(List.of("# only a comment", ""));
            failures.add("a file with no rules was accepted");
        } catch (IllegalArgumentException expected) {
            // The plugin refuses to enable with no rules to show.
        }

        int repoRules = Rules.load(Path.of("plugins/OasisRules")).rules().size();

        if (!failures.isEmpty()) {
            failures.forEach(System.err::println);
            System.exit(1);
        }
        System.out.println("rules.txt reads as expected; the repo's has " + repoRules + " rules");
    }

    private static void check(List<String> failures, boolean passed, String failure) {
        if (!passed) {
            failures.add(failure);
        }
    }
}
