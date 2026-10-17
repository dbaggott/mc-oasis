package com.oasissmp.egg;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Checks what /egg answers from what's found live and what's logged: logged
 * sightings count only while their source is out of reach, and the last
 * known sighting stands in when nothing is found.
 */
public final class ReportTest {

    private static final Instant TIME = Instant.parse("2026-10-16T14:03:00Z");

    private ReportTest() { }

    public static void main(String[] args) {
        List<String> failures = new ArrayList<>();

        Source chunk = new Source.Blocks("minecraft:overworld", 250, 250);
        Source player = new Source.Player(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        Sighting live = new Sighting(chunk, "placed as a block", 1, "minecraft:overworld", 4000, 101, 4000, TIME);
        Sighting inChest = new Sighting(chunk, "in a chest", 2, "minecraft:overworld", 4002, 100, 4000, TIME);
        Sighting withPlayer = new Sighting(player, "in Someone's ender chest", 1, "minecraft:the_end",
                0, 66, 0, TIME);

        List<String> all = Report.lines(List.of(live), List.of(inChest, withPlayer), source -> false, Optional.empty());
        check(failures, all.equals(List.of(
                        "4 dragon eggs:",
                        "placed as a block at 4000 101 4000 in minecraft:overworld",
                        "2 in a chest at 4002 100 4000 in minecraft:overworld,"
                                + " as of 2026-10-16 14:03 UTC, when its chunk unloaded",
                        "in Someone's ender chest at 0 66 0 in minecraft:the_end,"
                                + " as of 2026-10-16 14:03 UTC, when they left")),
                "live and out-of-reach logged eggs not all listed: " + all);

        List<String> back = Report.lines(List.of(live), List.of(withPlayer), source -> true, Optional.of(withPlayer));
        check(failures, back.equals(List.of(
                        "1 dragon egg:",
                        "placed as a block at 4000 101 4000 in minecraft:overworld")),
                "a logged egg whose source is loaded again was listed: " + back);

        List<String> none = Report.lines(List.of(), List.of(), source -> true, Optional.of(withPlayer));
        check(failures, none.size() == 2 && none.get(0).startsWith("No dragon egg found.")
                        && none.get(1).equals("Last known: in Someone's ender chest at 0 66 0 in minecraft:the_end,"
                                + " as of 2026-10-16 14:03 UTC, when they left. It isn't there now."),
                "nothing found didn't fall back to the last known sighting: " + none);

        check(failures, Report.lines(List.of(), List.of(), source -> true, Optional.empty()).size() == 1,
                "nothing found or logged answered more than one line: "
                        + Report.lines(List.of(), List.of(), source -> true, Optional.empty()));

        if (!failures.isEmpty()) {
            failures.forEach(System.err::println);
            System.exit(1);
        }
        System.out.println("/egg reports live and logged eggs as expected");
    }

    private static void check(List<String> failures, boolean passed, String failure) {
        if (!passed) {
            failures.add(failure);
        }
    }
}
