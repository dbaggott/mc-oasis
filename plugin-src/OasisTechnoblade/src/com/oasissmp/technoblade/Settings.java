package com.oasissmp.technoblade;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * How Technoblade behaves. {@code settings.txt}, which the image ships and
 * puts back at every start, gives every setting its default: one
 * {@code name value} line for each, with blank lines and lines starting with
 * {@code #} left out, every setting there, none twice, and nothing else. Its
 * own comments say what each one does. {@code settings-changed.txt}, in the
 * same form, holds the ones staff changed with {@code /technoblade set}. Only
 * the plugin writes it, on the world volume, so a change outlasts restarts
 * and new images.
 */
record Settings(double hitDamage, double throwSpeed, double throwLift, Duration feedCooldown, int nuggetOneIn,
        int effectOneIn, Duration effectFor, Duration guardFor, double guardReach, Duration rumorEvery,
        Duration rumorJitter, double rumorNear, Duration stareFor, double stareReach) {

    static final String FILE_NAME = "settings.txt";
    static final String CHANGED_FILE_NAME = "settings-changed.txt";

    private static final int TICK_MILLIS = 50;

    Settings {
        if (rumorJitter.compareTo(rumorEvery) >= 0) {
            throw new IllegalArgumentException("rumor-jitter-minutes has to be less than rumor-minutes");
        }
    }

    /** The settings in a file's lines by name, in the file's order, each still as written. */
    static Map<String, String> read(List<String> lines) {
        Map<String, String> values = new LinkedHashMap<>();
        for (String line : lines.stream().map(String::strip)
                .filter(line -> !line.isEmpty() && !line.startsWith("#")).toList()) {
            String[] fields = line.split(" +");
            if (fields.length != 2) {
                throw new IllegalArgumentException("A settings line isn't `name value`: " + line);
            }
            if (values.put(fields[0], fields[1]) != null) {
                throw new IllegalArgumentException(fields[0] + " is set twice");
            }
        }
        return values;
    }

    /** The file's settings, or none if there is no file. */
    static Map<String, String> read(Path file) throws IOException {
        return Files.exists(file) ? read(Files.readAllLines(file)) : new LinkedHashMap<>();
    }

    // Written beside the file and moved over it, so a crash mid-write leaves the old one.
    static void save(Path file, Map<String, String> changed) throws IOException {
        List<String> lines = new ArrayList<>();
        lines.add("# Settings changed with /technoblade set, over the defaults in " + FILE_NAME + ".");
        changed.forEach((name, value) -> lines.add(name + " " + value));
        Files.createDirectories(file.getParent());
        Path written = file.resolveSibling(file.getFileName() + ".new");
        Files.write(written, lines);
        Files.move(written, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    /** The settings {@code defaults} gives, with those in {@code changed} in place of its own. */
    static Settings of(Map<String, String> defaults, Map<String, String> changed) {
        Map<String, String> values = new HashMap<>(defaults);
        values.putAll(changed);
        return of(values);
    }

    static Settings of(Map<String, String> named) {
        Map<String, String> values = new HashMap<>(named);
        Settings settings = new Settings(
                number(values, "hit-damage", 0),
                number(values, "throw-speed", 0),
                number(values, "throw-lift", 0),
                minutes(values, "feed-cooldown-minutes"),
                (int) whole(values, "nugget-one-in"),
                (int) whole(values, "effect-one-in"),
                minutes(values, "effect-minutes"),
                minutes(values, "guard-minutes"),
                number(values, "guard-reach", 0),
                minutes(values, "rumor-minutes"),
                Duration.ofMillis(Math.round(number(values, "rumor-jitter-minutes", 0) * 60_000)),
                number(values, "rumor-near", 0),
                Duration.ofMillis(Math.round(positive(values, "stare-seconds") * 1000)),
                number(values, "stare-reach", 0));
        if (!values.isEmpty()) {
            throw new IllegalArgumentException("There are no settings " + values.keySet());
        }
        return settings;
    }

    /** A length of time in server ticks, at least one. */
    static long ticks(Duration length) {
        return Math.max(1, length.toMillis() / TICK_MILLIS);
    }

    // Takes the setting out of values, so what is left afterwards is what doesn't exist.
    private static double number(Map<String, String> values, String name, double least) {
        String value = values.remove(name);
        if (value == null) {
            throw new IllegalArgumentException(name + " isn't set");
        }
        double number;
        try {
            number = Double.parseDouble(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(name + " isn't a number: " + value, e);
        }
        if (!Double.isFinite(number) || number < least) {
            throw new IllegalArgumentException(name + " is below " + least + ": " + value);
        }
        return number;
    }

    private static double positive(Map<String, String> values, String name) {
        double number = number(values, name, 0);
        if (number == 0) {
            throw new IllegalArgumentException(name + " is 0");
        }
        return number;
    }

    private static Duration minutes(Map<String, String> values, String name) {
        return Duration.ofMillis(Math.round(positive(values, name) * 60_000));
    }

    private static long whole(Map<String, String> values, String name) {
        double number = number(values, name, 1);
        if (number != Math.rint(number) || number > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(name + " isn't a whole number: " + number);
        }
        return (long) number;
    }
}
