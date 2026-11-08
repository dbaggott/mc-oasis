package com.oasissmp.technoblade;

import com.destroystokyo.paper.entity.ai.Goal;
import com.destroystokyo.paper.entity.ai.GoalKey;
import com.destroystokyo.paper.entity.ai.GoalType;
import java.util.EnumSet;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Pig;

/**
 * Technoblade's wandering, in place of vanilla's random stroll, which only
 * picks targets on land. This one picks the surface of any column nearby,
 * water included, so the pig swims off islands and across rivers, lakes and
 * the sea.
 */
final class Roam implements Goal<Pig> {

    // Vanilla's random stroll starts, on average, once in this many ticks.
    private static final int ONE_IN = 120;
    private static final int MIN_DISTANCE = 4;
    private static final int MAX_DISTANCE = 14;
    private static final double SPEED = 1.0;

    private final Pig pig;
    private final GoalKey<Pig> key;
    private Location target;

    Roam(Pig pig, GoalKey<Pig> key) {
        this.pig = pig;
        this.key = key;
    }

    @Override
    public boolean shouldActivate() {
        if (ThreadLocalRandom.current().nextInt(ONE_IN) != 0) {
            return false;
        }
        target = pickTarget();
        return target != null;
    }

    @Override
    public void start() {
        pig.getPathfinder().moveTo(target, SPEED);
    }

    @Override
    public boolean shouldStayActive() {
        return pig.getPathfinder().hasPath();
    }

    @Override
    public void stop() {
        target = null;
    }

    @Override
    public GoalKey<Pig> getKey() {
        return key;
    }

    @Override
    public EnumSet<GoalType> getTypes() {
        return EnumSet.of(GoalType.MOVE);
    }

    // The top of a loaded column inside the border: on the water if it's water, never in lava.
    private Location pickTarget() {
        Location from = pig.getLocation();
        World world = pig.getWorld();
        Pos column = new Pos(from.getBlockX(), from.getBlockY(), from.getBlockZ())
                .randomColumn(MIN_DISTANCE, MAX_DISTANCE, ThreadLocalRandom.current());
        if (!world.isChunkLoaded(column.x() >> 4, column.z() >> 4)) {
            return null;
        }
        Block top = world.getHighestBlockAt(column.x(), column.z(), HeightMap.MOTION_BLOCKING_NO_LEAVES);
        if (top.getType() == Material.LAVA) {
            return null;
        }
        Location target = top.getType() == Material.WATER
                ? top.getLocation().add(0.5, 0, 0.5)
                : top.getLocation().add(0.5, 1, 0.5);
        return world.getWorldBorder().isInside(target) ? target : null;
    }
}
