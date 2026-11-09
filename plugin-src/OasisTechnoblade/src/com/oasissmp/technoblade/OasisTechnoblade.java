package com.oasissmp.technoblade;

import com.destroystokyo.paper.entity.ai.GoalKey;
import com.destroystokyo.paper.entity.ai.MobGoals;
import com.destroystokyo.paper.entity.ai.VanillaGoal;
import com.destroystokyo.paper.event.entity.EntityAddToWorldEvent;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.RemoteConsoleCommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Pig;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.world.EntitiesUnloadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Technoblade: one pig in the overworld that never stays dead. It respawns
 * where it dies, every player is told "Technoblade never dies.", and whoever
 * killed it loses half of each food stack and has every empty slot filled
 * with poisonous potatoes ({@link FoodRule}). There is only ever one, and a
 * trapped one teleports out. It wears Technoblade's crown ({@link Crown}),
 * which is how players know it: it has no name label, and any name it gets is
 * taken off.
 *
 * <p>The pig is the one whose UUID is in {@value PigRecord#FILE_NAME}; it also
 * carries the scoreboard tag {@value #TAG} so vanilla selectors find it. A
 * new pig is placed only when the recorded one dies, is removed for good, or
 * staff run {@code /technoblade respawn}: an unloaded pig is still alive.
 * Any other pig with the tag is removed as it enters the world.
 */
public final class OasisTechnoblade extends JavaPlugin implements Listener {

    static final String PERMISSION = "oasistechnoblade.manage";
    static final String TAG = "technoblade";

    private static final Component NEVER_DIES = Component.text("Technoblade never dies.", NamedTextColor.LIGHT_PURPLE);
    private static final String SPAWN_REGION = "spawn";
    // Removals that leave the pig alive somewhere, or that the death handler deals with.
    private static final Set<EntityRemoveEvent.Cause> NOT_GONE =
            Set.of(EntityRemoveEvent.Cause.DEATH, EntityRemoveEvent.Cause.UNLOAD);
    private static final long TRAP_CHECK_TICKS = 200;
    private static final long RETRY_TICKS = 1200;
    private static final int TRAP_THRESHOLD = 24;
    private static final int SEARCH_RADIUS = 48;
    private static final int SEARCH_HEIGHT = 8;
    private static final int EXIT_MIN_DISTANCE = 16;
    private static final int EXIT_TRIES = 32;
    // Exit candidates before this many tries are in loaded chunks only.
    private static final int EXIT_LOADED_TRIES = 24;
    // Vanilla's priority for a pig's random stroll, which Roam replaces.
    private static final int ROAM_PRIORITY = 6;

    private Path recordFile;
    private FirstSpawn firstSpawn;
    private PigRecord record;
    private GoalKey<Pig> roamKey;
    private boolean spawning;
    private BukkitTask retry;
    // Where the pig was last found trapped with no way out, so the search isn't repeated there.
    private Pos noExitFrom;

    @Override
    public void onEnable() {
        recordFile = getDataFolder().toPath().resolve(PigRecord.FILE_NAME);
        roamKey = GoalKey.of(Pig.class, new NamespacedKey(this, "roam"));
        try {
            firstSpawn = FirstSpawn.load(getDataFolder().toPath());
        } catch (IOException e) {
            throw new UncheckedIOException("Couldn't read " + FirstSpawn.FILE_NAME, e);
        }
        try {
            record = PigRecord.load(recordFile).orElse(null);
        } catch (IOException e) {
            throw new UncheckedIOException("Couldn't read " + recordFile, e);
        } catch (IllegalArgumentException e) {
            // Placing a new pig is the way back: the old one is a duplicate when it loads.
            getLogger().warning("Couldn't read " + recordFile + ", so a new Technoblade is placed: " + e.getMessage());
        }
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(new Guard(this::isTechnoblade, Crown::is), this);
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event ->
                event.registrar().register(Commands.literal("technoblade")
                        // The console and RCON have it without the permission, which ops don't get by default.
                        .requires(source -> source.getSender() instanceof ConsoleCommandSender
                                || source.getSender() instanceof RemoteConsoleCommandSender
                                || source.getSender().hasPermission(PERMISSION))
                        .executes(context -> report(context.getSource()))
                        .then(Commands.literal("respawn")
                                .then(Commands.argument("x", IntegerArgumentType.integer())
                                        .then(Commands.argument("z", IntegerArgumentType.integer())
                                                .executes(context -> respawn(context.getSource(),
                                                        IntegerArgumentType.getInteger(context, "x"),
                                                        IntegerArgumentType.getInteger(context, "z"))))))
                        .build(), "Where Technoblade is, or place a new one"));
        // On the first tick, after every plugin has enabled and OasisRegions has set up the spawn region.
        getServer().getScheduler().runTask(this, () -> {
            overworld().getEntitiesByClass(Pig.class).forEach(this::admit);
            if (record == null) {
                placeOnColumn(firstSpawn.x(), firstSpawn.z());
            }
        });
        getServer().getScheduler().runTaskTimer(this, () -> checkTrap(false), TRAP_CHECK_TICKS, TRAP_CHECK_TICKS);
        getServer().getScheduler().runTaskTimer(this, this::tend, 1, 1);
    }

    @Override
    public void onDisable() {
        if (recordFile != null) {
            loaded().ifPresent(this::recordWhere);
        }
    }

    private boolean isTechnoblade(Entity entity) {
        return record != null && entity != null && entity.getUniqueId().equals(record.pig());
    }

    // WorldGuard's spawn listener, at HIGH, can cancel plugin spawns in a region that denies mob spawning.
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSpawn(CreatureSpawnEvent event) {
        if (spawning && isTechnoblade(event.getEntity())) {
            event.setCancelled(false);
        }
    }

    // Chunk loads and fresh spawns alike.
    @EventHandler
    public void onAdd(EntityAddToWorldEvent event) {
        if (event.getEntity() instanceof Pig pig) {
            admit(pig);
        } else if (Crown.is(event.getEntity())) {
            checkCrownLater(event.getEntity());
        }
    }

    // The pig's death and removal included; an unload dismounts too, but the crown is unloaded with it.
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDismount(EntityDismountEvent event) {
        if (Crown.is(event.getEntity())) {
            checkCrownLater(event.getEntity());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDeath(EntityDeathEvent event) {
        if (!isTechnoblade(event.getEntity())) {
            return;
        }
        event.getDrops().clear();
        event.setDroppedExp(0);
        Player killer = event.getEntity().getKiller();
        if (killer != null) {
            punish(killer);
        }
    }

    // Only once no plugin can still cancel the death.
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDied(EntityDeathEvent event) {
        if (!isTechnoblade(event.getEntity())) {
            return;
        }
        UUID dead = event.getEntity().getUniqueId();
        Location where = event.getEntity().getLocation();
        // Now, not when the body is removed a second later, by which time the new pig wears its own.
        event.getEntity().getPassengers().stream().filter(Crown::is).forEach(Entity::remove);
        getServer().getScheduler().runTask(this, () -> {
            if (record != null && record.pig().equals(dead)) {
                placeNear(where);
                ((Audience) getServer()).sendMessage(NEVER_DIES);
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRemove(EntityRemoveEvent event) {
        if (!isTechnoblade(event.getEntity()) || NOT_GONE.contains(event.getCause())) {
            return;
        }
        UUID gone = event.getEntity().getUniqueId();
        Location where = event.getEntity().getLocation();
        getLogger().info("Technoblade was removed (" + event.getCause() + ") at " + describe(where) + "; placing a new one");
        getServer().getScheduler().runTask(this, () -> {
            if (record != null && record.pig().equals(gone)) {
                placeNear(where);
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntitiesUnload(EntitiesUnloadEvent event) {
        event.getEntities().stream().filter(this::isTechnoblade).findFirst().ifPresent(this::recordWhere);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getCause() == EntityDamageEvent.DamageCause.SUFFOCATION && isTechnoblade(event.getEntity())) {
            getServer().getScheduler().runTask(this, () -> checkTrap(true));
        }
    }

    // A tagged pig that isn't the recorded one is removed; the recorded one gets its tag and wandering.
    private void admit(Pig pig) {
        if (isTechnoblade(pig)) {
            pig.addScoreboardTag(TAG);
            unname(pig);
            MobGoals goals = getServer().getMobGoals();
            goals.removeGoal(pig, VanillaGoal.WATER_AVOIDING_RANDOM_STROLL);
            if (!goals.hasGoal(pig, roamKey)) {
                goals.addGoal(pig, ROAM_PRIORITY, new Roam(pig, roamKey));
            }
        } else if (pig.getScoreboardTags().contains(TAG)) {
            getLogger().info("Removing a pig tagged " + TAG + " that isn't Technoblade, at " + describe(pig.getLocation()));
            pig.getScheduler().run(this, task -> pig.remove(), null);
        }
    }

    private Optional<Pig> placeOnColumn(int x, int z) {
        return placeNear(surface(overworld(), x, z));
    }

    /**
     * Places Technoblade at {@code target} if a pig can stand there, otherwise
     * at the nearest spot one can, and otherwise on the first-spawn column. If
     * the pig doesn't stay in the world, it tries the first-spawn column, and
     * failing that tries it again every minute until it does.
     */
    private Optional<Pig> placeNear(Location target) {
        World world = target.getWorld();
        Terrain terrain = new WorldTerrain(world);
        Pos pos = pos(target);
        Optional<Location> spot = Walk.safe(terrain, pos) ? Optional.of(target)
                : Walk.nearest(terrain, pos, SEARCH_RADIUS, SEARCH_HEIGHT, candidate -> true)
                        .map(found -> centre(world, found));
        Optional<Pig> pig = spawn(spot.orElseGet(this::firstSpawnSurface));
        if (pig.isEmpty() && spot.isPresent()) {
            pig = spawn(firstSpawnSurface());
        }
        if (pig.isEmpty() && loaded().isPresent()) {
            getLogger().severe("Couldn't place Technoblade near " + describe(target)
                    + " or on the first-spawn column, so Technoblade " + record.pig() + " stays where it is");
        } else if (pig.isEmpty()) {
            getLogger().severe("Couldn't place Technoblade near " + describe(target)
                    + " or on the first-spawn column; trying the first-spawn column again every minute");
            // With no record, a restart places a pig too, which a retry timer wouldn't survive.
            clearRecord();
            if (retry == null) {
                retry = getServer().getScheduler().runTaskTimer(this, () -> spawn(firstSpawnSurface()),
                        RETRY_TICKS, RETRY_TICKS);
            }
        }
        return pig;
    }

    /**
     * Spawns and records a pig at {@code spot}. It is recorded before it enters
     * the world, so it isn't taken for a duplicate and WorldGuard's cancel is
     * undone; if it doesn't stay, the record goes back to what it was.
     */
    private Optional<Pig> spawn(Location spot) {
        Optional<PigRecord> previous = Optional.ofNullable(record);
        spawning = true;
        Pig pig;
        try {
            pig = spot.getWorld().spawn(spot, Pig.class, spawned -> {
                spawned.addScoreboardTag(TAG);
                spawned.setPersistent(true);
                spawned.setRemoveWhenFarAway(false);
                saveRecord(new PigRecord(spawned.getUniqueId(), spot.getWorld().getKey().asString(),
                        pos(spot), Instant.now()));
            });
        } finally {
            spawning = false;
        }
        if (!pig.isValid()) {
            previous.ifPresentOrElse(this::saveRecord, this::clearRecord);
            return Optional.empty();
        }
        Crown.place(pig);
        getLogger().info("Placed Technoblade " + pig.getUniqueId() + " at " + describe(spot));
        if (retry != null) {
            retry.cancel();
            retry = null;
        }
        return Optional.of(pig);
    }

    /**
     * Every tick the pig is loaded: exactly one crown rides it, turned the way
     * the pig faces, and it has no name.
     */
    private void tend() {
        Optional<Pig> loaded = loaded();
        if (loaded.isEmpty()) {
            return;
        }
        Pig pig = loaded.get();
        unname(pig);
        List<ItemDisplay> crowns = pig.getPassengers().stream()
                .filter(Crown::is).map(ItemDisplay.class::cast).toList();
        if (crowns.isEmpty()) {
            Crown.place(pig);
            return;
        }
        crowns.stream().skip(1).forEach(Entity::remove);
        Crown.face(crowns.getFirst(), pig, getLogger());
    }

    // A tick on, once a chunk's passengers have mounted: a crown not riding Technoblade is removed.
    private void checkCrownLater(Entity crown) {
        crown.getScheduler().run(this, task -> {
            if (crown.isValid() && !isTechnoblade(crown.getVehicle())) {
                getLogger().info("Removing a crown that isn't on Technoblade, at " + describe(crown.getLocation()));
                crown.remove();
            }
        }, null);
    }

    // By a name tag, which Guard refuses, or by anything else.
    private static void unname(Pig pig) {
        if (pig.customName() != null || pig.isCustomNameVisible()) {
            pig.customName(null);
            pig.setCustomNameVisible(false);
        }
    }

    private void checkTrap(boolean suffocating) {
        Optional<Pig> loaded = loaded();
        if (loaded.isEmpty()) {
            return;
        }
        Pig pig = loaded.get();
        recordWhere(pig);
        if (!trapped(pig, suffocating)) {
            noExitFrom = null;
            return;
        }
        Location from = pig.getLocation();
        // Where the trap check starts from, which a pig bobbing on water doesn't move off.
        Pos at = Walk.start(new WorldTerrain(pig.getWorld()), pos(from));
        if (at.equals(noExitFrom)) {
            return;
        }
        pig.getPassengers().stream().filter(passenger -> !Crown.is(passenger)).forEach(pig::removePassenger);
        pig.leaveVehicle();
        if (pig.isLeashed()) {
            pig.setLeashHolder(null);
        }
        Optional<Location> exit = exit(from);
        if (exit.isEmpty()) {
            getLogger().warning("Technoblade is trapped at " + describe(from)
                    + " and no way out was found nearby; /technoblade respawn <x> <z> moves it");
            noExitFrom = at;
            return;
        }
        effects(from);
        // Its crown comes along: Paper teleports passengers with their vehicle.
        pig.teleport(exit.get());
        effects(exit.get());
        recordWhere(pig);
        getLogger().info("Technoblade teleported out of a trap at " + describe(from) + " to " + describe(exit.get()));
    }

    // Mid-jump or falling, where it is isn't a place it stays: the next check sees where it lands.
    private boolean trapped(Pig pig, boolean suffocating) {
        if (suffocating || pig.isInsideVehicle() || pig.isLeashed()) {
            return true;
        }
        if (!pig.isOnGround() && !pig.isInWater()) {
            return false;
        }
        Terrain terrain = new WorldTerrain(pig.getWorld());
        return Walk.trapped(terrain, Walk.start(terrain, pos(pig.getLocation())), TRAP_THRESHOLD);
    }

    /**
     * A random column {@value #EXIT_MIN_DISTANCE} to {@value #SEARCH_RADIUS}
     * blocks away whose surface is safe, outside the spawn region, and not
     * itself a trap; failing that, the nearest such spot.
     */
    private Optional<Location> exit(Location from) {
        World world = from.getWorld();
        Terrain terrain = new WorldTerrain(world);
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int tries = 0; tries < EXIT_TRIES; tries++) {
            Pos column = pos(from).randomColumn(EXIT_MIN_DISTANCE, SEARCH_RADIUS, random);
            if (tries < EXIT_LOADED_TRIES && !world.isChunkLoaded(column.x() >> 4, column.z() >> 4)) {
                continue;
            }
            Location spot = surface(world, column.x(), column.z());
            if (isWayOut(world, terrain, pos(spot))) {
                return Optional.of(spot);
            }
        }
        return Walk.nearest(terrain, pos(from), SEARCH_RADIUS, SEARCH_HEIGHT,
                candidate -> isWayOut(world, terrain, candidate)).map(found -> centre(world, found));
    }

    private boolean isWayOut(World world, Terrain terrain, Pos pos) {
        return Walk.safe(terrain, pos) && !inSpawnRegion(world, pos) && !Walk.trapped(terrain, pos, TRAP_THRESHOLD);
    }

    // Looked up each time: OasisRegions replaces the region at start.
    private static boolean inSpawnRegion(World world, Pos pos) {
        RegionManager regions = WorldGuard.getInstance().getPlatform().getRegionContainer()
                .get(BukkitAdapter.adapt(world));
        ProtectedRegion region = regions == null ? null : regions.getRegion(SPAWN_REGION);
        return region != null && region.contains(pos.x(), pos.y(), pos.z());
    }

    private static void effects(Location at) {
        at.getWorld().playSound(at, Sound.ENTITY_ENDERMAN_TELEPORT, 1, 1);
        at.getWorld().spawnParticle(Particle.PORTAL, at, 32);
    }

    // The 36 storage slots and the offhand; armor and containers held are left alone.
    private static void punish(Player killer) {
        PlayerInventory inventory = killer.getInventory();
        ItemStack[] storage = inventory.getStorageContents();
        List<ItemStack> items = new ArrayList<>(Arrays.asList(storage));
        items.add(inventory.getItemInOffHand());
        List<FoodRule.Slot> before = items.stream().map(OasisTechnoblade::slot).toList();
        List<FoodRule.Slot> after = FoodRule.punish(before);
        ItemStack[] punished = new ItemStack[items.size()];
        for (int i = 0; i < items.size(); i++) {
            punished[i] = stack(items.get(i), before.get(i), after.get(i));
        }
        inventory.setStorageContents(Arrays.copyOf(punished, storage.length));
        inventory.setItemInOffHand(punished[storage.length]);
    }

    private static FoodRule.Slot slot(ItemStack item) {
        if (item == null || item.isEmpty()) {
            return FoodRule.Slot.EMPTY;
        }
        Material type = item.getType();
        return new FoodRule.Slot(type.getKey().asString(), item.getAmount(),
                type.isEdible() && type != Material.POISONOUS_POTATO);
    }

    // Food kept keeps its own item, enchantments and name included.
    private static ItemStack stack(ItemStack item, FoodRule.Slot before, FoodRule.Slot after) {
        if (after.equals(before)) {
            return item;
        }
        if (after.item().equals(before.item())) {
            ItemStack kept = item.clone();
            kept.setAmount(after.count());
            return kept;
        }
        Material type = Material.matchMaterial(after.item());
        if (type == null) {
            throw new IllegalStateException("No item " + after.item());
        }
        return ItemStack.of(type, after.count());
    }

    private int report(CommandSourceStack source) {
        Audience sender = source.getSender();
        if (record == null) {
            sender.sendMessage(Component.text("No Technoblade is recorded.", NamedTextColor.RED));
            return Command.SINGLE_SUCCESS;
        }
        Optional<Pig> loaded = loaded();
        String where = loaded.map(pig -> "is at " + describe(pig.getLocation()))
                .orElseGet(() -> "was last at " + record.pos() + " in " + record.world() + ", as of " + record.time()
                        + "; its chunk isn't loaded");
        sender.sendMessage(Component.text("Technoblade " + record.pig() + " " + where + ".",
                NamedTextColor.LIGHT_PURPLE));
        return Command.SINGLE_SUCCESS;
    }

    // The new pig is recorded before the old one goes, so removing the old one isn't taken for a loss.
    private int respawn(CommandSourceStack source, int x, int z) {
        Audience sender = source.getSender();
        Optional<UUID> old = Optional.ofNullable(record).map(PigRecord::pig);
        Optional<Pig> placed = placeOnColumn(x, z);
        old.map(getServer()::getEntity).filter(entity -> !isTechnoblade(entity)).ifPresent(Entity::remove);
        sender.sendMessage(placed.map(pig -> Component.text("Placed Technoblade " + pig.getUniqueId() + " at "
                                + describe(pig.getLocation()) + ".", NamedTextColor.LIGHT_PURPLE))
                .orElseGet(() -> Component.text("Couldn't place Technoblade; see the server log.",
                        NamedTextColor.RED)));
        return Command.SINGLE_SUCCESS;
    }

    private Optional<Pig> loaded() {
        if (record == null) {
            return Optional.empty();
        }
        return getServer().getEntity(record.pig()) instanceof Pig pig && pig.isValid()
                ? Optional.of(pig) : Optional.empty();
    }

    private void recordWhere(Entity pig) {
        saveRecord(new PigRecord(pig.getUniqueId(), pig.getWorld().getKey().asString(),
                pos(pig.getLocation()), Instant.now()));
    }

    // The record in memory is what the plugin goes by; the file only carries it across a restart.
    private void saveRecord(PigRecord changed) {
        record = changed;
        try {
            changed.save(recordFile);
        } catch (IOException e) {
            getLogger().log(Level.SEVERE, "Couldn't save " + recordFile + "; after a restart it names an older pig", e);
        }
    }

    private void clearRecord() {
        record = null;
        try {
            Files.deleteIfExists(recordFile);
        } catch (IOException e) {
            getLogger().log(Level.SEVERE, "Couldn't delete " + recordFile + "; after a restart it names an older pig", e);
        }
    }

    private World overworld() {
        return getServer().getWorlds().getFirst();
    }

    private Location firstSpawnSurface() {
        return surface(overworld(), firstSpawn.x(), firstSpawn.z());
    }

    // Where a pig standing on the column's top block, ignoring leaves, has its feet.
    private static Location surface(World world, int x, int z) {
        return centre(world, new Pos(x, world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES) + 1, z));
    }

    private static Location centre(World world, Pos pos) {
        return new Location(world, pos.x() + 0.5, pos.y(), pos.z() + 0.5);
    }

    private static Pos pos(Location location) {
        return new Pos(location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    private static String describe(Location location) {
        return pos(location) + " in " + location.getWorld().getKey().asString();
    }
}
