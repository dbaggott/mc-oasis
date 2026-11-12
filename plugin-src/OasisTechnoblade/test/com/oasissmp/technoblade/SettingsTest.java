package com.oasissmp.technoblade;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Checks that settings.txt reads, the repo's
 * plugins/OasisTechnoblade/settings.txt included, and that one with a setting
 * missing, repeated, unknown or out of range doesn't. Runs from the repo root.
 */
public final class SettingsTest {

    private static final List<String> GOOD = List.of(
            "# a comment", "", " hit-damage 6 ", "throw-speed 0.7", "throw-lift 0.4", "feed-cooldown-minutes 5",
            "nugget-one-in 10", "effect-one-in 20", "effect-minutes 2", "guard-minutes 4", "guard-reach 16",
            "rumor-minutes 30", "rumor-jitter-minutes 10", "rumor-near 128", "stare-seconds 0.5", "stare-reach 32");

    private SettingsTest() { }

    public static void main(String[] args) throws IOException {
        List<String> failures = new ArrayList<>();

        Settings expected = new Settings(6, 0.7, 0.4, Duration.ofMinutes(5), 10, 20, Duration.ofMinutes(2),
                Duration.ofMinutes(4), 16, Duration.ofMinutes(30), Duration.ofMinutes(10), 128,
                Duration.ofMillis(500), 32);
        Settings read = Settings.parse(GOOD);
        if (!read.equals(expected)) {
            failures.add("the settings read as " + read + ", not " + expected);
        }
        if (Settings.ticks(Duration.ofMinutes(30)) != 36_000 || Settings.ticks(Duration.ofMillis(10)) != 1) {
            failures.add("30 minutes is " + Settings.ticks(Duration.ofMinutes(30)) + " ticks, not 36000, or 10 ms is "
                    + Settings.ticks(Duration.ofMillis(10)) + ", not 1");
        }

        refuses(failures, without("guard-reach"), "a setting missing");
        refuses(failures, with("guard-reach 8"), "a setting twice");
        refuses(failures, with("rumor-hours 1"), "a setting that doesn't exist");
        refuses(failures, with("just-a-name"), "a line with no value");
        refuses(failures, replaced("hit-damage", "lots"), "a value that isn't a number");
        refuses(failures, replaced("hit-damage", "-1"), "a negative damage");
        refuses(failures, replaced("nugget-one-in", "0"), "odds of 1 in 0");
        refuses(failures, replaced("effect-one-in", "2.5"), "odds of 1 in 2.5");
        refuses(failures, replaced("rumor-minutes", "0"), "rumors every 0 minutes");
        refuses(failures, replaced("rumor-jitter-minutes", "30"), "a jitter as long as the time between rumors");

        Settings repo = Settings.load(Path.of("plugins/OasisTechnoblade"));

        if (!failures.isEmpty()) {
            failures.forEach(System.err::println);
            System.exit(1);
        }
        System.out.println("SettingsTest passed; the repo's settings are " + repo);
    }

    private static List<String> without(String name) {
        return GOOD.stream().filter(line -> !line.startsWith(name + " ")).toList();
    }

    private static List<String> with(String line) {
        List<String> lines = new ArrayList<>(GOOD);
        lines.add(line);
        return lines;
    }

    private static List<String> replaced(String name, String value) {
        return with(name + " " + value).stream().filter(line -> !line.strip().startsWith(name + " ")
                || line.equals(name + " " + value)).toList();
    }

    private static void refuses(List<String> failures, List<String> lines, String what) {
        try {
            Settings.parse(lines);
            failures.add("settings.txt read with " + what);
        } catch (IllegalArgumentException expected) {
            // The plugin doesn't enable on settings it can't read.
        }
    }
}
