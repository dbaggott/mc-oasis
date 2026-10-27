package com.oasissmp.regions;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Checks how Regions reads regions.txt, and that the repo's
 * plugins/OasisRegions/regions.txt loads. Runs from the repo root.
 */
public final class RegionsTest {

    private RegionsTest() { }

    public static void main(String[] args) throws IOException {
        List<String> failures = new ArrayList<>();

        Regions regions = Regions.parse(List.of(
                "# a comment", "",
                "[spawn]", "  corners -512,-64,846 -429,319,936  ", "pvp allow", "tnt deny",
                "[egg-pedestal]", "corners 1,2,3 1,2,3"));
        check(failures, regions.regions().equals(List.of(
                        new Regions.Region("spawn", new Regions.Corner(-512, -64, 846),
                                new Regions.Corner(-429, 319, 936), Map.of("pvp", "allow", "tnt", "deny")),
                        new Regions.Region("egg-pedestal", new Regions.Corner(1, 2, 3),
                                new Regions.Corner(1, 2, 3), Map.of()))),
                "regions read with their corners and flags: " + regions.regions());
        check(failures, List.copyOf(regions.regions().getFirst().flags().keySet()).equals(List.of("pvp", "tnt")),
                "flags not kept in file order: " + regions.regions().getFirst().flags());

        rejects(failures, "a flag before any region", List.of("pvp allow"));
        rejects(failures, "a region with no corners", List.of("[spawn]", "pvp allow"));
        rejects(failures, "a second corners line", List.of("[spawn]", "corners 0,0,0 1,1,1", "corners 0,0,0 2,2,2"));
        rejects(failures, "a flag set twice", List.of("[spawn]", "corners 0,0,0 1,1,1", "pvp allow", "pvp deny"));
        rejects(failures, "a region named twice", List.of("[spawn]", "corners 0,0,0 1,1,1", "[spawn]", "corners 0,0,0 1,1,1"));
        rejects(failures, "a flag value other than allow or deny", List.of("[spawn]", "corners 0,0,0 1,1,1", "pvp none"));
        rejects(failures, "a corner without three coordinates", List.of("[spawn]", "corners 0,0 1,1,1"));

        List<String> repoRegions = Regions.load(Path.of("plugins/OasisRegions")).regions().stream()
                .map(Regions.Region::name).toList();

        if (!failures.isEmpty()) {
            failures.forEach(System.err::println);
            System.exit(1);
        }
        System.out.println("regions.txt reads as expected; the repo's has " + repoRegions);
    }

    private static void rejects(List<String> failures, String what, List<String> lines) {
        try {
            Regions.parse(lines);
            failures.add(what + " was accepted");
        } catch (IllegalArgumentException expected) {
            // The plugin refuses to enable with a region it can't set up as written.
        }
    }

    private static void check(List<String> failures, boolean passed, String failure) {
        if (!passed) {
            failures.add(failure);
        }
    }
}
