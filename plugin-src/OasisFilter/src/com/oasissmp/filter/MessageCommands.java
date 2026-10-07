package com.oasissmp.filter;

import java.util.Locale;
import java.util.Set;

/**
 * The commands that send a message to other players, as opposed to ones that
 * name or set something. Only the server's vanilla ones: none of its plugins
 * add any.
 */
final class MessageCommands {

    private static final Set<String> LABELS = Set.of("msg", "tell", "w", "me", "say", "teammsg", "tm");

    private MessageCommands() { }

    /** Whether a command line as typed, slash and all, runs one of them. */
    static boolean sends(String commandLine) {
        String label = commandLine.substring(1).split(" ", 2)[0].toLowerCase(Locale.ROOT);
        return LABELS.contains(label.substring(label.indexOf(':') + 1));
    }
}
