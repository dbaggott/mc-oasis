package com.oasissmp.technoblade;

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

    @Override
    public String toString() {
        return x + " " + y + " " + z;
    }
}
