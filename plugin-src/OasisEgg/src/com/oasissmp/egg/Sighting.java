package com.oasissmp.egg;

import java.time.Instant;

/**
 * {@code count} dragon eggs at block {@code x y z} in {@code world}, a world
 * key such as {@code minecraft:the_end}, seen at {@code time}. {@code place}
 * says what they're in or how they're there, as "in a shulker box in a chest";
 * for an egg a player has, the coordinates are the player's.
 */
record Sighting(Source source, String place, int count, String world, int x, int y, int z, Instant time) {

    Sighting {
        if (place.contains("\t") || place.contains("\n")) {
            throw new IllegalArgumentException("A place can't hold a tab or line break: " + place);
        }
        if (count < 1) {
            throw new IllegalArgumentException("A sighting is of at least one egg: " + count);
        }
    }

    /** One line, tab-separated, with the place last; {@link #parse} reads it back. */
    String line() {
        return String.join("\t", source.key(), Integer.toString(count), world,
                Integer.toString(x), Integer.toString(y), Integer.toString(z),
                Long.toString(time.getEpochSecond()), place);
    }

    static Sighting parse(String line) {
        String[] fields = line.split("\t", 8);
        if (fields.length != 8) {
            throw new IllegalArgumentException("Malformed sighting: " + line);
        }
        return new Sighting(Source.parse(fields[0]), fields[7], Integer.parseInt(fields[1]), fields[2],
                Integer.parseInt(fields[3]), Integer.parseInt(fields[4]), Integer.parseInt(fields[5]),
                Instant.ofEpochSecond(Long.parseLong(fields[6])));
    }
}
