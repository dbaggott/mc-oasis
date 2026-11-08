package com.oasissmp.technoblade;

import java.util.Set;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.util.BoundingBox;

/**
 * A world's blocks as {@link Terrain} cells, by each block's collision shape.
 * Looking up a block in a chunk that isn't loaded loads it.
 */
final class WorldTerrain implements Terrain {

    // Blocks with no collision, or that a pig can stand on, that still hurt or hold it.
    private static final Set<Material> DANGEROUS = Set.of(
            Material.MAGMA_BLOCK, Material.CACTUS, Material.CAMPFIRE, Material.SOUL_CAMPFIRE,
            Material.FIRE, Material.SOUL_FIRE, Material.POWDER_SNOW, Material.SWEET_BERRY_BUSH,
            Material.WITHER_ROSE, Material.POINTED_DRIPSTONE, Material.COBWEB, Material.BUBBLE_COLUMN,
            Material.KELP, Material.KELP_PLANT, Material.SEAGRASS, Material.TALL_SEAGRASS,
            Material.NETHER_PORTAL, Material.END_PORTAL, Material.END_GATEWAY);

    private final World world;

    WorldTerrain(World world) {
        this.world = world;
    }

    @Override
    public Cell at(int x, int y, int z) {
        if (y < world.getMinHeight()) {
            return Cell.DANGER;
        }
        if (!world.getWorldBorder().isInside(new Location(world, x + 0.5, y, z + 0.5))) {
            return Cell.WALL;
        }
        if (y >= world.getMaxHeight()) {
            return Cell.OPEN;
        }
        Block block = world.getBlockAt(x, y, z);
        if (block.isLiquid() || DANGEROUS.contains(block.getType())
                || (block.getBlockData() instanceof Waterlogged waterlogged && waterlogged.isWaterlogged())) {
            return Cell.DANGER;
        }
        double top = block.getCollisionShape().getBoundingBoxes().stream()
                .mapToDouble(BoundingBox::getMaxY).max().orElse(0);
        if (top == 0) {
            return Cell.OPEN;
        }
        return top > 1 ? Cell.WALL : Cell.FLOOR;
    }
}
