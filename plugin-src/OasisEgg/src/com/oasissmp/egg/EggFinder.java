package com.oasissmp.egg;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.BundleContents;
import io.papermc.paper.datacomponent.item.ItemContainerContents;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Keyed;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Item;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.BlockInventoryHolder;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

/**
 * Finds dragon eggs in what the server has loaded: placed as a block, in a
 * container block, in or held by an entity, dropped or falling, or with a
 * player, and in any shulker box or bundle inside those, however deep.
 */
final class EggFinder {

    private static final BlockData EGG = Material.DRAGON_EGG.createBlockData();

    private final Server server;

    EggFinder(Server server) {
        this.server = server;
    }

    /** Every source loaded now, each with the eggs in it, which may be none. */
    Map<Source, List<Sighting>> inLoaded() {
        Map<Source, List<Sighting>> found = new LinkedHashMap<>();
        for (World world : server.getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                found.put(blocksOf(chunk), inBlocks(chunk));
                if (chunk.isEntitiesLoaded()) {
                    found.put(entitiesOf(chunk), inEntities(chunk, List.of(chunk.getEntities())));
                }
            }
        }
        for (Player player : server.getOnlinePlayers()) {
            found.put(new Source.Player(player.getUniqueId()), inPlayer(player));
        }
        return found;
    }

    static Source.Blocks blocksOf(Chunk chunk) {
        return new Source.Blocks(worldKey(chunk.getWorld()), chunk.getX(), chunk.getZ());
    }

    static Source.Entities entitiesOf(Chunk chunk) {
        return new Source.Entities(worldKey(chunk.getWorld()), chunk.getX(), chunk.getZ());
    }

    static String worldKey(World world) {
        return world.getKey().asString();
    }

    List<Sighting> inBlocks(Chunk chunk) {
        Sightings found = new Sightings(blocksOf(chunk));
        // The palette check is cheap; walking the chunk's blocks isn't, so only a chunk with an egg in it.
        if (chunk.contains(EGG)) {
            World world = chunk.getWorld();
            ChunkSnapshot blocks = chunk.getChunkSnapshot();
            for (int y = world.getMinHeight(); y < world.getMaxHeight(); y++) {
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        if (blocks.getBlockType(x, y, z) == Material.DRAGON_EGG) {
                            found.add("placed as a block", 1, world,
                                    chunk.getX() * 16 + x, y, chunk.getZ() * 16 + z);
                        }
                    }
                }
            }
        }
        for (BlockState state : chunk.getTileEntities(false)) {
            if (state instanceof BlockInventoryHolder holder) {
                // One half of a double chest, not the pair, so each egg is found once.
                Inventory inventory = state instanceof Chest chest ? chest.getBlockInventory() : holder.getInventory();
                found.inInventory(inventory, "in " + named(state.getType()), state.getLocation());
            }
        }
        return found.list;
    }

    // Players aren't counted here: they come and go on their own, as Source.Player.
    List<Sighting> inEntities(Chunk chunk, Collection<Entity> entities) {
        Sightings found = new Sightings(entitiesOf(chunk));
        for (Entity entity : entities) {
            if (entity instanceof Player) {
                continue;
            }
            Location at = entity.getLocation();
            switch (entity) {
                case Item item -> found.inItem(item.getItemStack(), "dropped on the ground", at);
                case FallingBlock falling when falling.getBlockData().getMaterial() == Material.DRAGON_EGG ->
                        found.add("falling as a block", 1, at);
                case ItemFrame frame -> found.inItem(frame.getItem(), "in " + named(entity.getType()), at);
                default -> {
                    String carried = (entity instanceof LivingEntity ? "carried by " : "in ") + named(entity.getType());
                    if (entity instanceof InventoryHolder holder) {
                        found.inInventory(holder.getInventory(), carried, at);
                    }
                    if (entity instanceof LivingEntity living && living.getEquipment() != null) {
                        EntityEquipment equipment = living.getEquipment();
                        for (EquipmentSlot slot : EquipmentSlot.values()) {
                            if (living.canUseEquipmentSlot(slot)) {
                                found.inItem(equipment.getItem(slot), carried, at);
                            }
                        }
                    }
                }
            }
        }
        return found.list;
    }

    List<Sighting> inPlayer(Player player) {
        Sightings found = new Sightings(new Source.Player(player.getUniqueId()));
        String name = player.getName() + "'s";
        Location at = player.getLocation();
        found.inInventory(player.getInventory(), "in " + name + " inventory", at);
        found.inItem(player.getItemOnCursor(), "on " + name + " cursor", at);
        found.inInventory(player.getEnderChest(), "in " + name + " ender chest", at);
        // The crafting grid and the like: slots belonging to no block or entity.
        // A container's window is that container, which is searched as a block.
        Inventory window = player.getOpenInventory().getTopInventory();
        InventoryHolder windowHolder = window.getHolder(false);
        if (windowHolder == null || windowHolder == player) {
            found.inInventory(window, "in the " + words(window.getType().name()) + " window "
                    + player.getName() + " has open", at);
        }
        return found.list;
    }

    // "an item frame", "a chest minecart": the thing's id as words, with its article.
    private static String named(Keyed thing) {
        return named(thing.getKey().getKey());
    }

    private static String named(String id) {
        String words = words(id);
        return ("aeiou".indexOf(words.charAt(0)) >= 0 ? "an " : "a ") + words;
    }

    private static String words(String id) {
        return id.toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    /** Eggs from one source, each stack of them and each container holding them its own sighting. */
    private static final class Sightings {

        private final Source source;
        private final Instant now = Instant.now();
        private final List<Sighting> list = new ArrayList<>();

        Sightings(Source source) {
            this.source = source;
        }

        void add(String place, int count, World world, int x, int y, int z) {
            list.add(new Sighting(source, place, count, worldKey(world), x, y, z, now));
        }

        void add(String place, int count, Location at) {
            add(place, count, at.getWorld(), at.getBlockX(), at.getBlockY(), at.getBlockZ());
        }

        void inInventory(Inventory inventory, String place, Location at) {
            for (ItemStack item : inventory.getContents()) {
                inItem(item, place, at);
            }
        }

        // `place` is where the item is; eggs inside it are "in a shulker box " + place.
        void inItem(ItemStack item, String place, Location at) {
            if (item == null || item.isEmpty()) {
                return;
            }
            if (item.getType() == Material.DRAGON_EGG) {
                add(place, item.getAmount(), at);
            }
            String inside = "in " + named(item.getType()) + " " + place;
            ItemContainerContents container = item.getData(DataComponentTypes.CONTAINER);
            if (container != null) {
                container.contents().forEach(content -> inItem(content, inside, at));
            }
            BundleContents bundle = item.getData(DataComponentTypes.BUNDLE_CONTENTS);
            if (bundle != null) {
                bundle.contents().forEach(content -> inItem(content, inside, at));
            }
        }
    }
}
