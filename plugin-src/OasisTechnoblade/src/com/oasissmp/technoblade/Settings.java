package com.oasissmp.technoblade;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * How Technoblade behaves, from {@code settings.txt}: one {@code name value}
 * line for each setting, with blank lines and lines starting with {@code #}
 * left out. Every setting has to be there, none twice, and nothing else. The
 * file's own comments say what each one does.
 */
record Settings(double hitDamage, double throwSpeed, double throwLift, Duration feedCooldown, int nuggetOneIn,
        int effectOneIn, Duration effectFor, Duration guardFor, double guardReach, Duration rumorEvery,
        Duration rumorJitter, double rumorNear, Duration stareFor, double stareReach) {

    static final String FILE_NAME = "settings.txt";

    private static final int TICK_MILLIS = 50;

    Settings {
        if (rumorJitter.compareTo(rumorEvery) >= 0) {
            throw new IllegalArgumentException(FILE_NAME + ": rumor-jitter-minutes has to be less than rumor-minutes");
        }
    }

    static Settings load(Path dataFolder) throws IOException {
        return parse(Files.readAllLines(dataFolder.resolve(FILE_NAME)));
    }

    static Settings parse(List<String> lines) {
        Map<String, String> values = new HashMap<>();
        for (String line : lines.stream().map(String::strip)
                .filter(line -> !line.isEmpty() && !line.startsWith("#")).toList()) {
            String[] fields = line.split(" +");
            if (fields.length != 2) {
                throw new IllegalArgumentException(FILE_NAME + " has a line that isn't `name value`: " + line);
            }
            if (values.put(fields[0], fields[1]) != null) {
                throw new IllegalArgumentException(FILE_NAME + " sets " + fields[0] + " twice");
            }
        }
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
            throw new IllegalArgumentException(FILE_NAME + " has settings that don't exist: " + values.keySet());
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
            throw new IllegalArgumentException(FILE_NAME + " doesn't set " + name);
        }
        double number;
        try {
            number = Double.parseDouble(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(FILE_NAME + ": " + name + " isn't a number: " + value, e);
        }
        if (!Double.isFinite(number) || number < least) {
            throw new IllegalArgumentException(FILE_NAME + ": " + name + " is below " + least + ": " + value);
        }
        return number;
    }

    private static double positive(Map<String, String> values, String name) {
        double number = number(values, name, 0);
        if (number == 0) {
            throw new IllegalArgumentException(FILE_NAME + ": " + name + " is 0");
        }
        return number;
    }

    private static Duration minutes(Map<String, String> values, String name) {
        return Duration.ofMillis(Math.round(positive(values, name) * 60_000));
    }

    private static long whole(Map<String, String> values, String name) {
        double number = number(values, name, 1);
        if (number != Math.rint(number) || number > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(FILE_NAME + ": " + name + " isn't a whole number: " + number);
        }
        return (long) number;
    }
}
