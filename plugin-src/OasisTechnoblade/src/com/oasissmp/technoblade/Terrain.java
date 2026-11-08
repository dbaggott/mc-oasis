package com.oasissmp.technoblade;

/** What a pig meets in each block of one world, as far as {@link Walk} cares. */
@FunctionalInterface
interface Terrain {

    enum Cell {
        /** Nothing to bump into or be hurt by: air, grass, flowers. */
        OPEN,
        /** Solid and harmless, at most a block tall, so a pig can stand on it or step up onto it. */
        FLOOR,
        /** Taller than a block, as fences and walls are, or past the world border: no way through or onto. */
        WALL,
        /** Water a pig can swim in, kelp and seagrass included. */
        WATER,
        /** Lava, fire, powder snow, or a block that hurts, such as magma or cactus. */
        DANGER
    }

    Cell at(int x, int y, int z);

    default Cell at(Pos pos) {
        return at(pos.x(), pos.y(), pos.z());
    }
}
