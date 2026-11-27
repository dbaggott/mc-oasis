package com.oasissmp.commands;

import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Which commands {@code /commands} lists: every command of the repo's own
 * plugins, each named Oasis-something, and the few other plugins' commands in
 * {@link #OTHERS}, each only where its sender passes the command's own
 * requirement. A subcommand that refuses the sender past that, as
 * {@code /locatorbar} refuses the console, doesn't keep it out.
 * {@code /commands} itself is left out.
 */
final class Listing {

    static final String NAME = "commands";

    /**
     * A command the server knows, by the label it's known by, its own name,
     * and the plugin that registered it.
     */
    record Known(String label, String name, String plugin, String description) { }

    /** A line of the listing. */
    record Entry(String name, String description) { }

    /**
     * Another plugin's command players should know about, with a description
     * of its own: the plugin's is no help.
     */
    record Other(String plugin, String description) { }

    static final Map<String, Other> OTHERS = Map.of(
            "graves", new Other("Graves", "List your graves and where they are"));

    private static final String PREFIX = "Oasis";

    private Listing() { }

    /**
     * The commands to list, by name. {@code usable} says whether the sender
     * can use the command known by that label. A command is listed only under
     * the plain label that is its own name, so not under an alias, a
     * namespaced label such as {@code oasisend:end}, or a label another
     * plugin's command took from it. A name known more than once is listed
     * once, as first known.
     */
    static List<Entry> select(Collection<Known> known, Predicate<String> usable) {
        Map<String, Entry> entries = new LinkedHashMap<>();
        for (Known command : known) {
            if (!command.label().equals(command.name()) || command.name().contains(":")) {
                continue;
            }
            Other other = OTHERS.get(command.name());
            Entry entry;
            if (other != null && other.plugin().equals(command.plugin())) {
                entry = new Entry(command.name(), other.description());
            } else if (command.plugin().startsWith(PREFIX) && !command.name().equals(NAME)) {
                entry = new Entry(command.name(), command.description());
            } else {
                continue;
            }
            if (!entries.containsKey(entry.name()) && usable.test(entry.name())) {
                entries.put(entry.name(), entry);
            }
        }
        return entries.values().stream().sorted(Comparator.comparing(Entry::name)).toList();
    }
}
