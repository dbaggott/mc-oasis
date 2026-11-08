package com.oasissmp.technoblade;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Where a pig can stand and where it can walk to from there, over a
 * {@link Terrain}. A position is the block a pig's feet are in.
 */
final class Walk {

    /** The most a pig can fall without taking damage. */
    static final int MAX_DROP = 3;

    private static final int[][] SIDES = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    private Walk() { }

    /**
     * Whether a pig can stand at {@code pos}: on a harmless floor, its feet in
     * the open, and no fluid, fire or harmful block over its head.
     */
    static boolean safe(Terrain terrain, Pos pos) {
        return terrain.at(pos) == Terrain.Cell.OPEN
                && terrain.at(pos.below()) == Terrain.Cell.FLOOR
                && terrain.at(pos.above()) != Terrain.Cell.DANGER;
    }

    /**
     * The safe positions a pig at {@code from} can reach in one step to each
     * side: up onto a block if there's room to jump, level, or down a drop of
     * at most {@value #MAX_DROP}.
     */
    static List<Pos> steps(Terrain terrain, Pos from) {
        List<Pos> steps = new ArrayList<>();
        for (int[] side : SIDES) {
            Pos next = from.offset(side[0], 0, side[1]);
            Terrain.Cell cell = terrain.at(next);
            if (cell == Terrain.Cell.FLOOR) {
                if (terrain.at(from.above()) == Terrain.Cell.OPEN && safe(terrain, next.above())) {
                    steps.add(next.above());
                }
            } else if (cell == Terrain.Cell.OPEN) {
                landing(terrain, from, next).ifPresent(steps::add);
            }
        }
        return steps;
    }

    /**
     * Whether a pig at {@code from} can walk to fewer than {@code threshold}
     * positions, itself included. Stops looking once it has found that many.
     */
    static boolean trapped(Terrain terrain, Pos from, int threshold) {
        Set<Pos> seen = new HashSet<>(List.of(from));
        Deque<Pos> next = new ArrayDeque<>(seen);
        while (!next.isEmpty() && seen.size() < threshold) {
            for (Pos step : steps(terrain, next.removeFirst())) {
                if (seen.add(step)) {
                    next.addLast(step);
                }
            }
        }
        return seen.size() < threshold;
    }

    /**
     * The nearest safe position to {@code from} that {@code wanted} accepts,
     * looking in square rings out to {@code radius} blocks, and in each column
     * up to {@code height} blocks above and below, nearest first.
     */
    static Optional<Pos> nearest(Terrain terrain, Pos from, int radius, int height, Predicate<Pos> wanted) {
        for (int ring = 0; ring <= radius; ring++) {
            for (int dx = -ring; dx <= ring; dx++) {
                for (int dz = -ring; dz <= ring; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) {
                        continue;
                    }
                    for (int dy = 0; dy <= height; dy++) {
                        for (int sign : dy == 0 ? new int[] {1} : new int[] {1, -1}) {
                            Pos pos = from.offset(dx, sign * dy, dz);
                            if (safe(terrain, pos) && wanted.test(pos)) {
                                return Optional.of(pos);
                            }
                        }
                    }
                }
            }
        }
        return Optional.empty();
    }

    // Walking into an open block and falling until something is underneath.
    private static Optional<Pos> landing(Terrain terrain, Pos from, Pos next) {
        Pos landing = next;
        while (terrain.at(landing.below()) == Terrain.Cell.OPEN) {
            if (from.y() - landing.y() >= MAX_DROP) {
                return Optional.empty();
            }
            landing = landing.below();
        }
        return safe(terrain, landing) ? Optional.of(landing) : Optional.empty();
    }
}
