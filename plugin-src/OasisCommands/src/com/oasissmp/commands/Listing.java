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
 * {@link #OTHERS}, each only where its sender can use it. {@code /commands}
 * itself is left out.
 */
final class Listing {

    /** A command the server knows, by the plugin that registered it. */
    record Known(String name, String plugin, String description) { }

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
     * can use the command of that name. A command is listed by its plain
     * name only: a command map also knows each by its namespaced label, as a
     * command of its own named {@code plugin:name}. A name known more than
     * once is listed once.
     */
    static List<Entry> select(Collection<Known> known, Predicate<String> usable) {
        Map<String, Entry> entries = new LinkedHashMap<>();
        for (Known command : known) {
            if (command.name().contains(":")) {
                continue;
            }
            Other other = OTHERS.get(command.name());
            Entry entry;
            if (other != null && other.plugin().equals(command.plugin())) {
                entry = new Entry(command.name(), other.description());
            } else if (command.plugin().startsWith(PREFIX) && !command.name().equals(OasisCommands.NAME)) {
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
