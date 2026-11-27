package com.oasissmp.commands;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Checks that the listing takes the repo's plugins' commands and the other
 * plugins' commands it names, such as Graves' /graves, once each, as first known, by plain name, in name order; leaves out
 * namespaced labels, aliases, /commands, other plugins' commands and a
 * /graves some other plugin registered; and leaves out what the sender can't
 * use.
 */
public final class ListingTest {

    private ListingTest() { }

    public static void main(String[] args) {
        List<String> failures = new ArrayList<>();
        List<Listing.Known> known = List.of(
                new Listing.Known("rules", "rules", "OasisRules", "Shows the server rules"),
                new Listing.Known("end", "end", "OasisEnd", "Whether the End is enabled"),
                new Listing.Known("end", "end", "OasisEnd", "Listed again"),
                new Listing.Known("oasisend:end", "oasisend:end", "OasisEnd", "Whether the End is enabled"),
                new Listing.Known("commands", "commands", "OasisCommands", "List the Oasis commands you can use"),
                new Listing.Known("graves", "graves", "Graves", "Graves command"),
                new Listing.Known("graves:graves", "graves", "Graves", "Graves command"),
                new Listing.Known("grave", "graves", "Graves", "Graves command"),
                new Listing.Known("graveyards", "graveyards", "Graves", "Graveyards command"),
                new Listing.Known("mute", "mute", "LibertyBans", ""),
                new Listing.Known("ban", "ban", "LibertyBans", ""),
                new Listing.Known("kick", "kick", "LibertyBans", ""),
                new Listing.Known("lp", "lp", "LuckPerms", "Manage permissions"));

        List<Listing.Entry> all = Listing.select(known, name -> true);
        check(failures, all.equals(List.of(
                new Listing.Entry("ban", Listing.OTHERS.get("ban").description()),
                new Listing.Entry("end", "Whether the End is enabled"),
                new Listing.Entry("graves", Listing.OTHERS.get("graves").description()),
                new Listing.Entry("mute", Listing.OTHERS.get("mute").description()),
                new Listing.Entry("rules", "Shows the server rules"))),
                "a sender who can use everything got " + all);

        Set<String> usable = Set.of("rules", "graves", "mute", "lp");
        List<Listing.Entry> some = Listing.select(known, usable::contains);
        check(failures, some.stream().map(Listing.Entry::name).toList().equals(List.of("graves", "mute", "rules")),
                "a sender who can't use /end or /ban got " + some);

        List<Listing.Entry> impostor = Listing.select(
                List.of(new Listing.Known("graves", "graves", "SomethingElse", "Not Graves")), name -> true);
        check(failures, impostor.isEmpty(), "another plugin's /graves was listed: " + impostor);

        if (!failures.isEmpty()) {
            failures.forEach(System.err::println);
            System.exit(1);
        }
        System.out.println("ListingTest passed");
    }

    private static void check(List<String> failures, boolean passed, String failure) {
        if (!passed) {
            failures.add(failure);
        }
    }
}
