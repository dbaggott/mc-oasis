package com.oasissmp.technoblade;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Checks that a setting is its default until set, that one can't be set
 * outside its bounds, that set ones read back as saved while a saved line
 * that sets nothing is left out, that every default is within its own bounds,
 * and that no subject has two settings of one name.
 */
public final class SettingsTest {

    private SettingsTest() { }

    public static void main(String[] args) throws IOException {
        List<String> failures = new ArrayList<>();

        Settings settings = new Settings();
        check(failures, settings.get(Setting.RUMOR_INTERVAL) == 30 && settings.get(Setting.HIT_DAMAGE) == 6,
                "settings nobody set aren't their defaults");
        settings.set(Setting.RUMOR_INTERVAL, 12);
        settings.set(Setting.STARE_REACH, 8);
        check(failures, settings.get(Setting.RUMOR_INTERVAL) == 12 && settings.get(Setting.RUMOR_JITTER) == 10
                && settings.minutes(Setting.RUMOR_INTERVAL).equals(Duration.ofMinutes(12)),
                "setting the rumor interval to 12 left it at " + settings.get(Setting.RUMOR_INTERVAL)
                        + " or changed the jitter");
        for (int outside : new int[] {Setting.RUMOR_INTERVAL.min() - 1, Setting.RUMOR_INTERVAL.max() + 1}) {
            try {
                settings.set(Setting.RUMOR_INTERVAL, outside);
                failures.add("the rumor interval was set to " + outside);
            } catch (IllegalArgumentException expected) {
                check(failures, settings.get(Setting.RUMOR_INTERVAL) == 12, "a refused value changed the setting");
            }
        }

        Path file = Files.createTempDirectory("oasistechnoblade").resolve("OasisTechnoblade/" + Settings.FILE_NAME);
        List<String> skipped = new ArrayList<>();
        check(failures, Settings.load(file, skipped::add).lines().isEmpty(), "a missing file read as settings");
        settings.save(file);
        Settings loaded = Settings.load(file, skipped::add);
        check(failures, loaded.lines().equals(settings.lines()) && loaded.lines().size() == 2 && skipped.isEmpty(),
                "the settings didn't read back: " + loaded.lines() + ", skipping " + skipped);

        Settings read = Settings.read(List.of("FEED_NUGGET 25", "NO_SUCH_SETTING 3", "HIT_DAMAGE lots",
                "HIT_DAMAGE 21", "STARE_REACH", " GUARD_REACH 4 "), skipped::add);
        check(failures, read.lines().equals(List.of("FEED_NUGGET 25", "GUARD_REACH 4")) && skipped.size() == 4,
                "saved lines read as " + read.lines() + ", skipping " + skipped);

        if (Settings.ticks(Duration.ofMinutes(30)) != 36_000 || Settings.ticks(Duration.ZERO) != 1) {
            failures.add("30 minutes is " + Settings.ticks(Duration.ofMinutes(30)) + " ticks, not 36000, or no time is "
                    + Settings.ticks(Duration.ZERO) + ", not 1");
        }

        Set<String> names = new HashSet<>();
        for (Setting setting : Setting.values()) {
            check(failures, setting.min() <= setting.defaultValue() && setting.defaultValue() <= setting.max(),
                    setting + "'s default is outside its bounds");
            check(failures, names.add(setting.subject().commandName() + " " + setting.commandName()),
                    "two settings answer to " + setting.subject().commandName() + " " + setting.commandName());
            check(failures, setting.subject().settings().contains(setting), setting + " isn't among its subject's");
        }

        if (!failures.isEmpty()) {
            failures.forEach(System.err::println);
            System.exit(1);
        }
        System.out.println("SettingsTest passed");
    }

    private static void check(List<String> failures, boolean ok, String failure) {
        if (!ok) {
            failures.add(failure);
        }
    }
}
