package com.oasissmp.technoblade;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Checks what counts as a trap for the pig, by the flood fill over the
 * positions it can walk to, and where the nearest safe spot is.
 */
public final class WalkTest {

    private static final int THRESHOLD = 24;

    private WalkTest() { }

    /**
     * Flat ground: floor at and below {@code ground}, then water up to
     * {@code waterTop} if that is higher, and open above, with some blocks set
     * otherwise.
     */
    private static final class Blocks implements Terrain {

        private final int ground;
        private final int waterTop;
        private final Map<Pos, Cell> set = new HashMap<>();

        Blocks(int ground) {
            this(ground, ground);
        }

        Blocks(int ground, int waterTop) {
            this.ground = ground;
            this.waterTop = waterTop;
        }

        Blocks with(Cell cell, Pos... positions) {
            for (Pos pos : positions) {
                set.put(pos, cell);
            }
            return this;
        }

        // Cells at height y on the edge of the square from (x1, z1) to (x2, z2), corners included.
        Blocks ring(Cell cell, int y, int x1, int z1, int x2, int z2) {
            for (int x = x1; x <= x2; x++) {
                for (int z = z1; z <= z2; z++) {
                    if (x == x1 || x == x2 || z == z1 || z == z2) {
                        with(cell, new Pos(x, y, z));
                    }
                }
            }
            return this;
        }

        @Override
        public Cell at(int x, int y, int z) {
            Cell otherwise = y <= ground ? Cell.FLOOR : y <= waterTop ? Cell.WATER : Cell.OPEN;
            return set.getOrDefault(new Pos(x, y, z), otherwise);
        }
    }

    public static void main(String[] args) {
        List<String> failures = new ArrayList<>();
        Pos standing = new Pos(0, 1, 0);

        check(failures, !Walk.trapped(new Blocks(0), standing, THRESHOLD), "an open field is a trap");

        Blocks hole = new Blocks(0).with(Terrain.Cell.OPEN, new Pos(0, 0, 0), new Pos(0, -1, 0));
        check(failures, Walk.trapped(hole, new Pos(0, -1, 0), THRESHOLD), "a 1x1 hole two deep isn't a trap");

        Blocks step = new Blocks(0).with(Terrain.Cell.OPEN, new Pos(0, 0, 0));
        check(failures, !Walk.trapped(step, new Pos(0, 0, 0), THRESHOLD),
                "a 1x1 hole one deep, which a pig jumps out of, is a trap");

        Blocks pen = new Blocks(0).ring(Terrain.Cell.WALL, 1, -2, -2, 2, 2);
        check(failures, Walk.trapped(pen, standing, THRESHOLD), "a 3x3 fenced pen isn't a trap");

        Blocks bigPen = new Blocks(0).ring(Terrain.Cell.WALL, 1, -1, -1, 6, 6);
        check(failures, !Walk.trapped(bigPen, standing, THRESHOLD), "a 6x6 fenced pen, over the threshold, is a trap");

        Blocks gap = new Blocks(0).ring(Terrain.Cell.WALL, 1, -2, -2, 2, 2).with(Terrain.Cell.OPEN, new Pos(2, 1, 0));
        check(failures, !Walk.trapped(gap, standing, THRESHOLD), "a pen with a gap in its fence is a trap");

        Blocks lowWall = new Blocks(0).ring(Terrain.Cell.FLOOR, 1, -2, -2, 2, 2);
        check(failures, !Walk.trapped(lowWall, standing, THRESHOLD), "a wall one block high, which a pig steps over, is a trap");

        Blocks highWall = new Blocks(0).ring(Terrain.Cell.FLOOR, 1, -2, -2, 2, 2).ring(Terrain.Cell.FLOOR, 2, -2, -2, 2, 2);
        check(failures, Walk.trapped(highWall, standing, THRESHOLD), "a wall two blocks high isn't a trap");

        // A pig on a pillar, its top at y 4, over ground at y 1 (a 3-block drop) or y 0 (4 blocks).
        Pos onPillar = new Pos(0, 5, 0);
        Pos[] pillar = {new Pos(0, 1, 0), new Pos(0, 2, 0), new Pos(0, 3, 0), new Pos(0, 4, 0)};
        List<Pos> threeDown = Walk.steps(new Blocks(1).with(Terrain.Cell.FLOOR, pillar), onPillar);
        check(failures, threeDown.contains(new Pos(1, 2, 0)), "a 3-block drop isn't walkable: " + threeDown);
        List<Pos> fourDown = Walk.steps(new Blocks(0).with(Terrain.Cell.FLOOR, pillar), onPillar);
        check(failures, fourDown.isEmpty(), "a 4-block drop is walkable: " + fourDown);

        Pos floating = new Pos(0, 3, 0);
        check(failures, !Walk.trapped(new Blocks(0, 2), floating, THRESHOLD), "open water is a trap");
        check(failures, Walk.start(new Blocks(0, 2), new Pos(0, 1, 0)).equals(new Pos(0, 2, 0))
                        && Walk.start(new Blocks(0, 2), new Pos(0, 3, 0)).equals(new Pos(0, 2, 0)),
                "a pig under water or floating on it doesn't start at the surface");

        // Flooded to y 0 inside a fence, ground at y 0 outside it.
        Blocks floodedPen = new Blocks(0).ring(Terrain.Cell.WALL, 1, -2, -2, 2, 2);
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                floodedPen.with(Terrain.Cell.WATER, new Pos(x, 0, z));
            }
        }
        check(failures, Walk.trapped(floodedPen, new Pos(0, 0, 0), THRESHOLD), "a flooded 3x3 pen isn't a trap");

        Blocks wetHole = new Blocks(0).with(Terrain.Cell.OPEN, new Pos(0, 0, 0)).with(Terrain.Cell.WATER, new Pos(0, -1, 0));
        check(failures, Walk.trapped(wetHole, new Pos(0, -1, 0), THRESHOLD),
                "a 1x1 hole two deep with water in the bottom isn't a trap");

        Blocks shaft = new Blocks(0).with(Terrain.Cell.OPEN, new Pos(0, 0, 0));
        for (int y = -10; y <= -1; y++) {
            shaft.with(Terrain.Cell.WATER, new Pos(0, y, 0));
        }
        check(failures, Walk.trapped(shaft, Walk.start(shaft, new Pos(0, -6, 0)), THRESHOLD),
                "a 1x1 water shaft ten deep isn't a trap: its depth counted as room");

        Blocks flushPool = new Blocks(0).with(Terrain.Cell.WATER, new Pos(0, 0, 0));
        check(failures, !Walk.trapped(flushPool, new Pos(0, 0, 0), THRESHOLD),
                "a 1x1 pool level with the ground, which a pig climbs out of, is a trap");

        List<Pos> intoWater = Walk.steps(new Blocks(-10, -8).with(Terrain.Cell.FLOOR, pillar), onPillar);
        check(failures, intoWater.contains(new Pos(1, -8, 0)), "a long drop into water isn't reachable: " + intoWater);

        Blocks lava = new Blocks(0);
        for (int x = -3; x <= 3; x++) {
            for (int z = -3; z <= 3; z++) {
                lava.with(Terrain.Cell.DANGER, new Pos(x, 0, z), new Pos(x, 1, z));
            }
        }
        check(failures, !Walk.safe(lava, new Pos(0, 1, 0)), "standing in lava is safe");
        Optional<Pos> out = Walk.nearest(lava, new Pos(0, 1, 0), 48, 8, pos -> true);
        check(failures, out.isPresent() && Math.max(Math.abs(out.get().x()), Math.abs(out.get().z())) == 4
                        && out.get().y() == 1,
                "the nearest safe spot to the middle of a 7x7 lava pool isn't just past its edge: " + out);
        check(failures, Walk.nearest(lava, new Pos(0, 1, 0), 3, 8, pos -> true).isEmpty(),
                "a safe spot was found inside the lava pool");

        if (!failures.isEmpty()) {
            failures.forEach(System.err::println);
            System.exit(1);
        }
        System.out.println("WalkTest passed");
    }

    private static void check(List<String> failures, boolean ok, String failure) {
        if (!ok) {
            failures.add(failure);
        }
    }
}
