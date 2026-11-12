package com.oasissmp.technoblade;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The overworld column in {@code first-spawn.txt}: where
 * {@code /technoblade add} places Technoblade when given no column, and where
 * every other placement falls back to. One {@code x z} line, with blank lines and lines starting with {@code #}
 * left out.
 */
record FirstSpawn(int x, int z) {

    static final String FILE_NAME = "first-spawn.txt";

    private static final Pattern COLUMN = Pattern.compile("(-?\\d+) (-?\\d+)");

    static FirstSpawn load(Path dataFolder) throws IOException {
        return parse(Files.readAllLines(dataFolder.resolve(FILE_NAME)));
    }

    static FirstSpawn parse(List<String> lines) {
        List<String> columns = lines.stream().map(String::strip)
                .filter(line -> !line.isEmpty() && !line.startsWith("#")).toList();
        if (columns.size() != 1) {
            throw new IllegalArgumentException(FILE_NAME + " has " + columns.size() + " x z lines, not one");
        }
        Matcher matcher = COLUMN.matcher(columns.getFirst());
        if (!matcher.matches()) {
            throw new IllegalArgumentException(FILE_NAME + " isn't one x z line: " + columns.getFirst());
        }
        return new FirstSpawn(Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)));
    }
}
