package com.oasissmp.rules;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.IntStream;

/**
 * The server rules from {@code rules.txt}: one rule per line, in order, with
 * blank lines and lines starting with {@code #} left out.
 */
record Rules(List<String> rules) {

    static final String FILE_NAME = "rules.txt";

    Rules {
        rules = List.copyOf(rules);
        if (rules.isEmpty()) {
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
                .toList());
    }

    /** Each rule with its number, "1. Be kind." */
    List<String> numbered() {
        return IntStream.range(0, rules.size())
                .mapToObj(i -> (i + 1) + ". " + rules.get(i))
                .toList();
    }

    /**
     * Identifies these rules as written: it changes with any change to a
     * rule's text or their order, and with nothing else.
     */
    String fingerprint() {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(String.join("\n", rules).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Every JVM has SHA-256", e);
        }
    }
}
