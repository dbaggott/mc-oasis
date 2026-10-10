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
        check(failures, texts(rules.numbered()).equals(List.of("1. Be kind.", "2. No griefing.")),
                "comments and blank lines left out, rules stripped and numbered: " + rules.numbered());

        Rules headed = Rules.parse(List.of("[ Chat ]", "Be kind.", "[Building]", "No griefing."));
        check(failures, headed.numbered().equals(List.of(
                        new Rules.Line("Chat", true), new Rules.Line("1. Be kind.", false),
                        new Rules.Line("Building", true), new Rules.Line("2. No griefing.", false))),
                "headings unnumbered and stripped, rules numbered on across them: " + headed.numbered());
        check(failures, !headed.fingerprint().equals(
                        Rules.parse(List.of("[Chat]", "Be kind.", "[Builds]", "No griefing.")).fingerprint()),
                "a heading's wording didn't change the fingerprint");
        check(failures, !headed.fingerprint().equals(
                        Rules.parse(List.of("[Chat]", "Be kind.", "No griefing.", "[Building]")).fingerprint()),
                "moving a heading didn't change the fingerprint");

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
            Rules.parse(List.of("# only a comment", "", "[Only a heading]"));
            failures.add("a file with no rules was accepted");
        } catch (IllegalArgumentException expected) {
            // The plugin refuses to enable with no rules to show.
        }

        long repoRules = Rules.load(Path.of("plugins/OasisRules")).lines().stream()
                .filter(line -> !line.heading()).count();

        if (!failures.isEmpty()) {
            failures.forEach(System.err::println);
            System.exit(1);
        }
        System.out.println("rules.txt reads as expected; the repo's has " + repoRules + " rules");
    }

    private static List<String> texts(List<Rules.Line> lines) {
        return lines.stream().map(Rules.Line::text).toList();
    }

    private static void check(List<String> failures, boolean passed, String failure) {
        if (!passed) {
            failures.add(failure);
        }
    }
}
