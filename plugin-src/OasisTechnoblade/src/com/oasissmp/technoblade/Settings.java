package com.oasissmp.technoblade;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * What each {@link Setting} is set to: its default until staff set it with
 * {@code /technoblade config set}. The ones that were set are saved in
 * {@value #FILE_NAME} on the world volume, which the image never carries, so
 * they outlast restarts and new images. One {@code NAME value} line each, by
 * the setting's enum name.
 */
final class Settings {

    static final String FILE_NAME = "config.txt";

    private static final int TICK_MILLIS = 50;

    private final Map<Setting, Integer> set = new EnumMap<>(Setting.class);

    int get(Setting setting) {
        return set.getOrDefault(setting, setting.defaultValue());
    }

    Duration minutes(Setting setting) {
        return Duration.ofMinutes(get(setting));
    }

    /** Sets it, or throws {@link IllegalArgumentException} for a value outside its bounds. */
    void set(Setting setting, int value) {
        if (value < setting.min() || value > setting.max()) {
            throw new IllegalArgumentException(setting.subject().commandName() + " " + setting.commandName()
                    + " must be between " + setting.min() + " and " + setting.max() + " " + setting.unit());
        }
        set.put(setting, value);
    }

    List<String> lines() {
        return set.entrySet().stream().map(entry -> entry.getKey().name() + " " + entry.getValue()).toList();
    }

    /**
     * The settings saved as {@code lines}. A line that names no setting, as
     * one for a setting since removed does, or whose value is no longer within
     * its bounds, is left out and handed to {@code skipped}.
     */
    static Settings read(List<String> lines, Consumer<String> skipped) {
        Settings settings = new Settings();
        for (String line : lines) {
            String[] fields = line.strip().split(" ");
            try {
                if (fields.length != 2) {
                    throw new IllegalArgumentException("Not `NAME value`");
                }
                settings.set(Setting.valueOf(fields[0]), Integer.parseInt(fields[1]));
            } catch (IllegalArgumentException e) {
                skipped.accept(line);
            }
        }
        return settings;
    }

    /** The settings saved in {@code file}, or the defaults if there is no file. */
    static Settings load(Path file, Consumer<String> skipped) throws IOException {
        return Files.exists(file) ? read(Files.readAllLines(file), skipped) : new Settings();
    }

    // Written beside the file and moved over it, so a crash mid-write leaves the old one.
    void save(Path file) throws IOException {
        Files.createDirectories(file.getParent());
        Path written = file.resolveSibling(file.getFileName() + ".new");
        Files.write(written, lines());
        Files.move(written, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    /** A length of time in server ticks, at least one. */
    static long ticks(Duration length) {
        return Math.max(1, length.toMillis() / TICK_MILLIS);
    }
}
