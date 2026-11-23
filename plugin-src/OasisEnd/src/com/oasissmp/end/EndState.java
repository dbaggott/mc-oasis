package com.oasissmp.end;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.function.Consumer;

/**
 * Whether the End is enabled, saved in {@value #FILE_NAME} on the world
 * volume, which the image never carries, so it outlasts restarts and new
 * images. The End is disabled until an owner enables it: with no file, or one
 * that says anything but {@value #ENABLED}, it is disabled.
 */
final class EndState {

    static final String FILE_NAME = "state.txt";

    private static final String ENABLED = "enabled";
    private static final String DISABLED = "disabled";

    private EndState() { }

    /** Whether {@code file} says the End is enabled; a line it doesn't recognise is handed to {@code unreadable}. */
    static boolean load(Path file, Consumer<String> unreadable) throws IOException {
        if (!Files.exists(file)) {
            return false;
        }
        List<String> lines = Files.readAllLines(file);
        String state = lines.isEmpty() ? "" : lines.getFirst().strip();
        if (!state.equals(ENABLED) && !state.equals(DISABLED)) {
            unreadable.accept(state);
        }
        return state.equals(ENABLED);
    }

    // Written beside the file and moved over it, so a crash mid-write leaves the old one.
    static void save(Path file, boolean enabled) throws IOException {
        Files.createDirectories(file.getParent());
        Path written = file.resolveSibling(file.getFileName() + ".new");
        Files.write(written, List.of(enabled ? ENABLED : DISABLED));
        Files.move(written, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }
}
