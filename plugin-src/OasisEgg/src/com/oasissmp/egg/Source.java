package com.oasissmp.egg;

import java.util.UUID;

/**
 * What a dragon egg was found in, as one thing that loads and unloads: a
 * player, who is online or not, or a chunk's blocks or its entities, which
 * the server loads and unloads separately.
 */
sealed interface Source {

    record Player(UUID id) implements Source { }

    record Blocks(String world, int chunkX, int chunkZ) implements Source { }

    record Entities(String world, int chunkX, int chunkZ) implements Source { }

    /** One word per field, space-separated; {@link #parse} reads it back. */
    default String key() {
        return switch (this) {
            case Player player -> "player " + player.id();
            case Blocks blocks -> "blocks " + blocks.world() + " " + blocks.chunkX() + " " + blocks.chunkZ();
            case Entities entities -> "entities " + entities.world() + " " + entities.chunkX() + " " + entities.chunkZ();
        };
    }

    static Source parse(String key) {
        String[] words = key.split(" ");
        return switch (words[0]) {
            case "player" -> {
                requireWords(key, words, 2);
                yield new Player(UUID.fromString(words[1]));
            }
            case "blocks" -> {
                requireWords(key, words, 4);
                yield new Blocks(words[1], Integer.parseInt(words[2]), Integer.parseInt(words[3]));
            }
            case "entities" -> {
                requireWords(key, words, 4);
                yield new Entities(words[1], Integer.parseInt(words[2]), Integer.parseInt(words[3]));
            }
            default -> throw new IllegalArgumentException("Unknown source: " + key);
        };
    }

    private static void requireWords(String key, String[] words, int count) {
        if (words.length != count) {
            throw new IllegalArgumentException("Malformed source: " + key);
        }
    }
}
