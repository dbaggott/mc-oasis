package com.oasissmp.rules;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/**
 * The server rules from {@code rules.txt}: one rule per line, in order, with a
 * line in {@code [brackets]} heading the rules after it, and blank lines and
 * lines starting with {@code #} left out.
 */
record Rules(List<Line> lines) {

    static final String FILE_NAME = "rules.txt";

    /** A rule, or a heading over the rules after it. */
    record Line(String text, boolean heading) { }

    Rules {
        lines = List.copyOf(lines);
        if (lines.stream().allMatch(Line::heading)) {
            throw new IllegalArgumentException(FILE_NAME + " has no rules");
        }
    }

    static Rules load(Path dataFolder) throws IOException {
        return parse(Files.readAllLines(dataFolder.resolve(FILE_NAME)));
    }

    static Rules parse(List<String> lines) {
        return new Rules(lines.stream()
                .map(String::strip)
                .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                .map(line -> line.startsWith("[") && line.endsWith("]")
                        ? new Line(line.substring(1, line.length() - 1).strip(), true)
                        : new Line(line, false))
                .toList());
    }

    /** A line as shown: a rule after its {@code number}, "1. ", or a heading, whose number is "". */
    record Numbered(String number, Line line) { }

    /** Each line with its number, the rules counting on across headings. */
    List<Numbered> numbered() {
        List<Numbered> numbered = new ArrayList<>();
        int number = 0;
        for (Line line : lines) {
            numbered.add(new Numbered(line.heading() ? "" : ++number + ". ", line));
        }
        return numbered;
    }

    /**
     * Identifies these rules as written: it changes with any change to a
     * rule's or heading's text or their order, and with nothing else.
     */
    String fingerprint() {
        String written = String.join("\n", lines.stream()
                .map(line -> line.heading() ? "[" + line.text() + "]" : line.text())
                .toList());
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(written.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Every JVM has SHA-256", e);
        }
    }
}
