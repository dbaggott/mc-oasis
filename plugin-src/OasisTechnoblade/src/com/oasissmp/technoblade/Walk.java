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
 * Where a pig can stand and where it can walk or swim to from there, over a
 * {@link Terrain}. A position is the block a pig's feet are in. In water that
 * is the top water block of its column, where a pig floats, so a pool counts
 * its surface, not its depth.
 */
final class Walk {

    /** The most a pig can fall onto ground without taking damage. */
    static final int MAX_DROP = 3;

    // How far up a column of water is followed to its surface.
    private static final int MAX_RISE = 384;

    private static final int[][] SIDES = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    private Walk() { }

    /**
     * Whether a pig can stand at {@code pos}: on a harmless floor, its feet in
     * the open, and no water, fire or harmful block over its head.
     */
    static boolean safe(Terrain terrain, Pos pos) {
        Terrain.Cell head = terrain.at(pos.above());
        return terrain.at(pos) == Terrain.Cell.OPEN
                && terrain.at(pos.below()) == Terrain.Cell.FLOOR
                && head != Terrain.Cell.DANGER && head != Terrain.Cell.WATER;
    }

    /**
     * The position of a pig whose feet are in the block at {@code feet}: the
     * water's surface if it's in water or floating just above it, and the block
     * above if it's standing on something lower than a full block, such as a
     * slab, carpet or farmland.
     */
    static Pos start(Terrain terrain, Pos feet) {
        Terrain.Cell cell = terrain.at(feet);
        if (cell == Terrain.Cell.WATER) {
            return surface(terrain, feet);
        }
        if (cell != Terrain.Cell.OPEN) {
            return feet.above();
        }
        return terrain.at(feet.below()) == Terrain.Cell.WATER ? feet.below() : feet;
    }

    /**
     * The positions a pig at {@code from} can reach in one move to each side:
     * up onto a block if there's room to jump, level, down a drop of at most
     * {@value #MAX_DROP} onto ground or any drop into water, or swimming.
     */
    static List<Pos> steps(Terrain terrain, Pos from) {
        List<Pos> steps = new ArrayList<>();
        for (int[] side : SIDES) {
            Pos next = from.offset(side[0], 0, side[1]);
            Terrain.Cell cell = terrain.at(next);
            if (cell == Terrain.Cell.WATER) {
                steps.add(surface(terrain, next));
            } else if (cell == Terrain.Cell.FLOOR) {
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
     * Whether a pig at {@code from} can reach fewer than {@code threshold}
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

    // The top water block of the column of water at pos.
    private static Pos surface(Terrain terrain, Pos pos) {
        Pos surface = pos;
        for (int rise = 0; rise < MAX_RISE && terrain.at(surface.above()) == Terrain.Cell.WATER; rise++) {
            surface = surface.above();
        }
        return surface;
    }

    // Walking into an open block and falling until something is underneath: ground, or water at any depth.
    private static Optional<Pos> landing(Terrain terrain, Pos from, Pos next) {
        Pos landing = next;
        while (terrain.at(landing.below()) == Terrain.Cell.OPEN) {
            landing = landing.below();
            if (from.y() - landing.y() > MAX_RISE) {
                return Optional.empty();
            }
        }
        if (terrain.at(landing.below()) == Terrain.Cell.WATER) {
            return Optional.of(landing.below());
        }
        return from.y() - landing.y() <= MAX_DROP && safe(terrain, landing) ? Optional.of(landing) : Optional.empty();
    }
}
