package com.oasissmp.egg;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Where dragon eggs were when the {@link Source} holding them last went out
 * of reach: a player leaving, or a chunk's blocks or entities unloading.
 * Nothing in an unloaded source moves, so its sightings stay true until it
 * loads again. Saved to a file on every change, one {@link Sighting#line()}
 * per line. A line that doesn't read as one is set aside in
 * {@link #unreadable()} and gone from the file at the next save.
 */
final class SightingLog {

    private final Path file;
    private final Map<Source, List<Sighting>> bySource = new LinkedHashMap<>();
    private final List<String> unreadable = new ArrayList<>();

    private SightingLog(Path file) {
        this.file = file;
    }

    static SightingLog load(Path file) throws IOException {
        SightingLog log = new SightingLog(file);
        if (Files.exists(file)) {
            for (String line : Files.readAllLines(file)) {
                if (line.isEmpty()) {
                    continue;
                }
                try {
                    Sighting sighting = Sighting.parse(line);
                    log.bySource.computeIfAbsent(sighting.source(), source -> new ArrayList<>())
                            .add(sighting);
                } catch (IllegalArgumentException e) {
                    log.unreadable.add(line);
                }
            }
        }
        return log;
    }

    /** Makes {@code sightings}, which may be none, all that's known of {@code source}. */
    void replace(Source source, List<Sighting> sightings) throws IOException {
        if (sightings.stream().anyMatch(sighting -> !sighting.source().equals(source))) {
            throw new IllegalArgumentException("Sightings from another source than " + source.key());
        }
        if (sightings.isEmpty()) {
            if (bySource.remove(source) == null) {
                return;
            }
        } else {
            bySource.put(source, List.copyOf(sightings));
        }
        save();
    }

    List<String> unreadable() {
        return List.copyOf(unreadable);
    }

    List<Sighting> all() {
        return bySource.values().stream().flatMap(List::stream).toList();
    }

    // Written beside the file and moved over it, so a crash mid-write leaves the old one.
    private void save() throws IOException {
        Files.createDirectories(file.getParent());
        Path written = file.resolveSibling(file.getFileName() + ".new");
        Files.write(written, all().stream().map(Sighting::line).toList());
        Files.move(written, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }
}
