package com.oasissmp.technoblade;

import java.util.random.RandomGenerator;

/** A block position in one world. */
record Pos(int x, int y, int z) {

    Pos offset(int dx, int dy, int dz) {
        return new Pos(x + dx, y + dy, z + dz);
    }

    Pos above() {
        return offset(0, 1, 0);
    }

    Pos below() {
        return offset(0, -1, 0);
    }

    /** A random column {@code min} to {@code max} blocks away across, at this height. */
    Pos randomColumn(int min, int max, RandomGenerator random) {
        double angle = random.nextDouble(2 * Math.PI);
        int distance = random.nextInt(min, max + 1);
        return offset((int) Math.round(Math.cos(angle) * distance), 0, (int) Math.round(Math.sin(angle) * distance));
    }

    @Override
    public String toString() {
        return x + " " + y + " " + z;
    }
}
