package com.oasissmp.egg;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * What {@code /egg} answers: a heading line, then one line per place eggs are.
 * Those are the eggs seen in what's loaded now, and the logged ones whose
 * source is still out of reach. A logged sighting whose source has loaded
 * again is out of date, since anything there now was seen live; it's shown
 * only when no egg is found, as the last place one was known to be.
 */
final class Report {

    private static final DateTimeFormatter TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'").withZone(ZoneOffset.UTC);

    private Report() { }

    static List<String> lines(List<Sighting> live, List<Sighting> logged, Predicate<Source> loaded) {
        List<Sighting> away = logged.stream().filter(sighting -> !loaded.test(sighting.source())).toList();
        List<String> lines = new ArrayList<>();
        int eggs = Stream.concat(live.stream(), away.stream()).mapToInt(Sighting::count).sum();
        if (eggs == 0) {
            lines.add("No dragon egg found. Searched what's loaded now, and where eggs were"
                    + " when their chunk unloaded or the player holding one left.");
            logged.stream().max(Comparator.comparing(Sighting::time)).ifPresent(last ->
                    lines.add("Last known: " + describe(last) + ", " + since(last) + ". It isn't there now."));
            return lines;
        }
        lines.add(eggs == 1 ? "1 dragon egg:" : eggs + " dragon eggs:");
        live.forEach(sighting -> lines.add(describe(sighting)));
        away.forEach(sighting -> lines.add(describe(sighting) + ", " + since(sighting)));
        return lines;
    }

    private static String describe(Sighting sighting) {
        return (sighting.count() == 1 ? "" : sighting.count() + " ") + sighting.place()
                + " at " + sighting.x() + " " + sighting.y() + " " + sighting.z() + " in " + sighting.world();
    }

    private static String since(Sighting sighting) {
        return "as of " + TIME.format(sighting.time()) + (sighting.source() instanceof Source.Player
                ? ", when they left"
                : ", when its chunk unloaded");
    }
}
