package com.oasissmp.technoblade;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Which pig is Technoblade, and where it was last seen and when. Saved as one
 * tab-separated line: the UUID, the world's key, x, y, z, and the time in
 * epoch seconds.
 */
record PigRecord(UUID pig, String world, Pos pos, Instant time) {

    static final String FILE_NAME = "pig.txt";

    String line() {
        return String.join("\t", pig.toString(), world, Integer.toString(pos.x()), Integer.toString(pos.y()),
                Integer.toString(pos.z()), Long.toString(time.getEpochSecond()));
    }

    static PigRecord parse(String line) {
        String[] fields = line.split("\t", -1);
        if (fields.length != 6) {
            throw new IllegalArgumentException("Not six tab-separated fields: " + line);
        }
        return new PigRecord(UUID.fromString(fields[0]), fields[1],
                new Pos(Integer.parseInt(fields[2]), Integer.parseInt(fields[3]), Integer.parseInt(fields[4])),
                Instant.ofEpochSecond(Long.parseLong(fields[5])));
    }

    /**
     * The record in {@code file}, or none if there is no file. A file that
     * doesn't read throws {@link IllegalArgumentException}.
     */
    static Optional<PigRecord> load(Path file) throws IOException {
        if (!Files.exists(file)) {
            return Optional.empty();
        }
        List<String> lines = Files.readAllLines(file).stream().filter(line -> !line.isBlank()).toList();
        if (lines.size() != 1) {
            throw new IllegalArgumentException(file + " has " + lines.size() + " lines, not one");
        }
        return Optional.of(parse(lines.getFirst()));
    }

    // Written beside the file and moved over it, so a crash mid-write leaves the old one.
    void save(Path file) throws IOException {
        Files.createDirectories(file.getParent());
        Path written = file.resolveSibling(file.getFileName() + ".new");
        Files.write(written, List.of(line()));
        Files.move(written, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }
}
