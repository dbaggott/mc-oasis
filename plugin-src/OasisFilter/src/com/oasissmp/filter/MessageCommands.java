package com.oasissmp.filter;

import java.util.Locale;
import java.util.Set;

/**
 * The commands players message each other with, as opposed to ones that name
 * or set something. Only vanilla's: none of the server's plugins add one, and
 * a punishment's reason is a moderator's, not a message.
 */
final class MessageCommands {

    private static final Set<String> LABELS = Set.of("msg", "tell", "w", "me", "say", "teammsg", "tm");
    private static final String NAMESPACE = "minecraft:";

    private MessageCommands() { }

    /** Whether a command line as typed, slash and all, runs one of them. */
    static boolean sends(String commandLine) {
        String label = commandLine.substring(1).split(" ", 2)[0].toLowerCase(Locale.ROOT);
        return LABELS.contains(label.startsWith(NAMESPACE) ? label.substring(NAMESPACE.length()) : label);
    }
}
