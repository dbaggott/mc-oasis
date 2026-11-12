package com.oasissmp.technoblade;

import com.destroystokyo.paper.entity.ai.GoalKey;
import com.destroystokyo.paper.entity.ai.MobGoals;
import com.destroystokyo.paper.entity.ai.VanillaGoal;
import com.destroystokyo.paper.event.entity.EntityAddToWorldEvent;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;
import java.util.stream.Stream;
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
import org.bukkit.entity.Pig;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.world.EntitiesUnloadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Technoblade: one pig in the overworld that never stays dead. A world has
 * none until staff run {@code /technoblade add}, and has one from then until
 * {@code /technoblade remove}. It respawns where it dies. A player who kills
 * it is told "Technoblade never dies.", is stared at by the new one
 * ({@link Stare}), and loses half of each food stack but golden apples, with
 * every empty slot filled with poisonous potatoes ({@link FoodRule}). Hitting
 * it hurts and throws the attacker back ({@link Hitting}). Feeding it gives a
 * golden apple and its protection from monsters ({@link Feeding},
 * {@link Bodyguard}). Now and then players are told where it was seen
 * ({@link Rumors}). There is only ever one, and a trapped one teleports out.
 * It wears Technoblade's crown, painted on its head for Java players and
 * modelled on it for Bedrock players ({@link BedrockCrown}), which is how
 * players know it: it has no name label, and any name it gets is taken off.
 *
 * <p>The pig is the one whose UUID is in {@value PigRecord#FILE_NAME}, and
 * with no such file there is no Technoblade. It also carries the scoreboard
 * tag {@value #TAG} so vanilla selectors find it. A new pig is placed only
 * when the recorded one dies, is removed other than by
 * {@code /technoblade remove}, or staff run {@code /technoblade respawn}: an
 * unloaded pig is still alive. Any other pig with the tag is removed as it
 * enters the world.
 */
public final class OasisTechnoblade extends JavaPlugin implements Listener {

    static final String PERMISSION = "oasistechnoblade.manage";
    static final String TAG = "technoblade";

    private static final Component NEVER_DIES = Component.text("Technoblade never dies.", NamedTextColor.LIGHT_PURPLE);
    private static final String NOT_IN_WORLD = "Technoblade isn't in the world; /technoblade add places it.";
    private static final String SPAWN_REGION = "spawn";
    private static final NamespacedKey CROWNED = new NamespacedKey("oasis", "technoblade");
    private static final NamespacedKey GLARING = new NamespacedKey("oasis", "technoblade_glaring");
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
    // Ahead of every vanilla goal that moves a pig or turns its head.
    private static final int STARE_PRIORITY = 0;
    private static final int TEND_TICKS = 20;

    private Path recordFile;
    // The pig variants, each looked up until found.
    private Pig.Variant crowned;
    private Pig.Variant glaring;
    private final Set<NamespacedKey> warnedNoVariant = new HashSet<>();
    private Stare stare;
    private FirstSpawn firstSpawn;
    private Path settingsFile;
    private Settings settings;
    private Rumors rumors;
    // Volatile: BedrockCrown reads it on Geyser's threads.
    private volatile PigRecord record;
    private GoalKey<Pig> roamKey;
    private GoalKey<Pig> stareKey;
    private boolean spawning;
    private BukkitTask retry;
    // Where the pig was last found trapped with no way out, so the search isn't repeated there.
    private Pos noExitFrom;

    @Override
    public void onLoad() {
        if (getServer().getPluginManager().getPlugin("Geyser-Spigot") == null) {
            return;
        }
        try {
            BedrockCrown.register(this, pig -> {
                PigRecord current = record;
                return current != null && current.pig().equals(pig);
            }, getLogger());
        } catch (RuntimeException | LinkageError e) {
            // A Geyser that failed to load, or one whose API changed: everything but the Bedrock crown still works.
            getLogger().log(Level.SEVERE, "Couldn't set up Technoblade's crown for Bedrock players", e);
        }
    }

    @Override
    public void onEnable() {
        recordFile = getDataFolder().toPath().resolve(PigRecord.FILE_NAME);
        roamKey = GoalKey.of(Pig.class, new NamespacedKey(this, "roam"));
        stareKey = GoalKey.of(Pig.class, new NamespacedKey(this, "stare"));
        try {
            firstSpawn = FirstSpawn.load(getDataFolder().toPath());
        } catch (IOException e) {
            throw new UncheckedIOException("Couldn't read " + FirstSpawn.FILE_NAME, e);
        }
        settingsFile = getDataFolder().toPath().resolve(Settings.FILE_NAME);
        try {
            settings = Settings.load(settingsFile, line -> getLogger().warning(settingsFile
                    + " has a line that sets nothing, which is left out: " + line));
        } catch (IOException e) {
            throw new UncheckedIOException("Couldn't read " + settingsFile, e);
        }
        stare = new Stare(this, settings, this::isTechnoblade, this::crownHead);
        try {
            record = PigRecord.load(recordFile).orElse(null);
        } catch (IOException e) {
            throw new UncheckedIOException("Couldn't read " + recordFile, e);
        } catch (IllegalArgumentException e) {
            // Placing a new pig is the way back: the old one is a duplicate when it loads.
            getLogger().warning("Couldn't read " + recordFile + ", so a new Technoblade is placed: " + e.getMessage());
        }
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(new Guard(this::isTechnoblade), this);
        Guarding guarding = new Guarding();
        getServer().getPluginManager().registerEvents(new Feeding(this::isTechnoblade, settings, guarding), this);
        getServer().getPluginManager().registerEvents(new Bodyguard(guarding, settings, this::loaded), this);
        getServer().getPluginManager().registerEvents(new Hitting(this, this::isTechnoblade, settings), this);
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event ->
                event.registrar().register(Commands.literal("technoblade")
                        // The console and RCON have it without the permission, which ops don't get by default.
                        .requires(source -> source.getSender() instanceof ConsoleCommandSender
                                || source.getSender() instanceof RemoteConsoleCommandSender
                                || source.getSender().hasPermission(PERMISSION))
                        .executes(context -> report(context.getSource()))
                        .then(Commands.literal("add")
                                .executes(context -> add(context.getSource(), firstSpawn.x(), firstSpawn.z()))
                                .then(column(this::add)))
                        .then(Commands.literal("remove").executes(context -> remove(context.getSource())))
                        .then(Commands.literal("respawn").then(column(this::respawn)))
                        .then(Commands.literal("config")
                                .then(Commands.literal("get").then(subject()
                                        .executes(context -> reportSettings(context.getSource(),
                                                StringArgumentType.getString(context, "subject")))))
                                .then(Commands.literal("set").then(subject()
                                        .then(Commands.argument("setting", StringArgumentType.word())
                                                .suggests((context, builder) -> suggest(builder,
                                                        subject(StringArgumentType.getString(context, "subject"))
                                                                .map(Setting.Subject::settings).orElse(List.of())
                                                                .stream().map(Setting::commandName)))
                                                .then(Commands.argument("value", IntegerArgumentType.integer())
                                                        .executes(context -> set(context.getSource(),
                                                                StringArgumentType.getString(context, "subject"),
                                                                StringArgumentType.getString(context, "setting"),
                                                                IntegerArgumentType.getInteger(context, "value"))))))))
                        .build(), "Where Technoblade is, or add it to the world, remove it, place a new one, or"
                                + " change how it behaves"));
        // On the first tick, after every plugin has enabled.
        getServer().getScheduler().runTask(this,
                () -> overworld().getEntitiesByClass(Pig.class).forEach(this::admit));
        getServer().getScheduler().runTaskTimer(this, () -> checkTrap(false), TRAP_CHECK_TICKS, TRAP_CHECK_TICKS);
        getServer().getScheduler().runTaskTimer(this, this::tend, TEND_TICKS, TEND_TICKS);
        rumors = new Rumors(this, settings, () -> record, this::loaded);
        rumors.scheduleNext();
    }

    private static RequiredArgumentBuilder<CommandSourceStack, String> subject() {
        return Commands.argument("subject", StringArgumentType.word()).suggests((context, builder) ->
                suggest(builder, Arrays.stream(Setting.Subject.values()).map(Setting.Subject::commandName)));
    }

    private static CompletableFuture<Suggestions> suggest(SuggestionsBuilder builder, Stream<String> names) {
        names.filter(name -> name.startsWith(builder.getRemainingLowerCase())).forEach(builder::suggest);
        return builder.buildFuture();
    }

    private static Optional<Setting.Subject> subject(String name) {
        return Arrays.stream(Setting.Subject.values()).filter(subject -> subject.commandName().equals(name))
                .findFirst();
    }

    private interface ColumnCommand {
        int run(CommandSourceStack source, int x, int z);
    }

    private static RequiredArgumentBuilder<CommandSourceStack, Integer> column(ColumnCommand command) {
        return Commands.argument("x", IntegerArgumentType.integer())
                .then(Commands.argument("z", IntegerArgumentType.integer())
                        .executes(context -> command.run(context.getSource(),
                                IntegerArgumentType.getInteger(context, "x"),
                                IntegerArgumentType.getInteger(context, "z"))));
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
        Player killer = event.getEntity().getKiller();
        getLogger().info("Technoblade died at " + describe(where)
                + (killer == null ? "" : ", killed by " + killer.getName()));
        UUID killerId = killer == null ? null : killer.getUniqueId();
        getServer().getScheduler().runTask(this, () -> {
            if (record == null || !record.pig().equals(dead)) {
                return;
            }
            Optional<Pig> placed = placeNear(where);
            // Looked up again: only a killer who is still online is told and stared at.
            Player told = killerId == null ? null : getServer().getPlayer(killerId);
            if (told != null) {
                ((Audience) told).sendMessage(NEVER_DIES);
                placed.ifPresent(pig -> stare.start(pig, told));
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
            crownHead(pig);
            MobGoals goals = getServer().getMobGoals();
            goals.removeGoal(pig, VanillaGoal.WATER_AVOIDING_RANDOM_STROLL);
            if (!goals.hasGoal(pig, roamKey)) {
                goals.addGoal(pig, ROAM_PRIORITY, new Roam(pig, roamKey));
            }
            if (!goals.hasGoal(pig, stareKey)) {
                goals.addGoal(pig, STARE_PRIORITY, stare.hold(pig, stareKey));
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
                    + " or on the first-spawn column; trying the first-spawn column again every minute until the"
                    + " server stops, and after that /technoblade add places it");
            // The dead pig's record would read as a Technoblade in a chunk that never loads.
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
                crownHead(spawned);
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
        getLogger().info("Placed Technoblade " + pig.getUniqueId() + " at " + describe(spot));
        if (retry != null) {
            retry.cancel();
            retry = null;
        }
        return Optional.of(pig);
    }

    // Every second the pig is loaded, since a name or variant can be set on it by commands.
    private void tend() {
        loaded().ifPresent(pig -> {
            unname(pig);
            crownHead(pig);
        });
    }

    /**
     * Java players see the crown painted on Technoblade's head: the pig
     * variant {@code oasis:technoblade}, from the repo's datapack
     * {@code datapacks/80-oasis-technoblade/}, with its texture in the resource
     * pack. During a death stare it is {@code oasis:technoblade_glaring}, the
     * same with red eyes, and without that variant the stare has the usual
     * eyes. It can't breed, so no piglet inherits the variant.
     */
    private void crownHead(Pig pig) {
        if (crowned == null) {
            crowned = variant(CROWNED, "so Java players see Technoblade without its crown");
        }
        if (glaring == null) {
            glaring = variant(GLARING, "so Java players see its death stare without red eyes");
        }
        Pig.Variant worn = glaring != null && stare.isOn(pig) ? glaring : crowned;
        if (worn != null && !worn.equals(pig.getVariant())) {
            pig.setVariant(worn);
        }
        if (pig.canBreed()) {
            pig.setBreed(false);
        }
    }

    // Says once that a variant is missing.
    private Pig.Variant variant(NamespacedKey key, String consequence) {
        Pig.Variant found = RegistryAccess.registryAccess().getRegistry(RegistryKey.PIG_VARIANT).get(key);
        if (found == null && warnedNoVariant.add(key)) {
            getLogger().severe("No pig variant " + key.asString() + ", " + consequence
                    + "; is the datapack missing?");
        }
        return found;
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
        pig.eject();
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
        return new FoodRule.Slot(type.getKey().asString(), item.getAmount(), type.isEdible());
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
            sender.sendMessage(Component.text(retry == null ? NOT_IN_WORLD
                    : "Technoblade couldn't be placed; the first-spawn column is tried again every minute.",
                    NamedTextColor.RED));
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

    // Awaiting a placement that failed counts: the retry is about to put it there.
    private boolean inWorld() {
        return record != null || retry != null;
    }

    private int add(CommandSourceStack source, int x, int z) {
        Audience sender = source.getSender();
        if (inWorld()) {
            sender.sendMessage(Component.text("Technoblade is already in the world; /technoblade respawn <x> <z>"
                    + " moves it.", NamedTextColor.RED));
            return Command.SINGLE_SUCCESS;
        }
        sender.sendMessage(placement(placeOnColumn(x, z)));
        return Command.SINGLE_SUCCESS;
    }

    // The record goes before the pig does, so removing the pig isn't taken for a loss.
    private int remove(CommandSourceStack source) {
        Audience sender = source.getSender();
        if (!inWorld()) {
            sender.sendMessage(Component.text(NOT_IN_WORLD, NamedTextColor.RED));
            return Command.SINGLE_SUCCESS;
        }
        Optional<Pig> pig = loaded();
        boolean unloaded = record != null && pig.isEmpty();
        clearRecord();
        if (retry != null) {
            retry.cancel();
            retry = null;
        }
        pig.ifPresent(Entity::remove);
        getLogger().info("Technoblade was removed from the world by " + source.getSender().getName());
        sender.sendMessage(Component.text(unloaded
                ? "Removed Technoblade. Its chunk isn't loaded, so its pig goes when the chunk next loads."
                : "Removed Technoblade.", NamedTextColor.LIGHT_PURPLE));
        return Command.SINGLE_SUCCESS;
    }

    // The new pig is recorded before the old one goes, so removing the old one isn't taken for a loss.
    private int respawn(CommandSourceStack source, int x, int z) {
        Audience sender = source.getSender();
        if (!inWorld()) {
            sender.sendMessage(Component.text(NOT_IN_WORLD, NamedTextColor.RED));
            return Command.SINGLE_SUCCESS;
        }
        Optional<UUID> old = Optional.ofNullable(record).map(PigRecord::pig);
        Optional<Pig> placed = placeOnColumn(x, z);
        old.map(getServer()::getEntity).filter(entity -> !isTechnoblade(entity)).ifPresent(Entity::remove);
        sender.sendMessage(placement(placed));
        return Command.SINGLE_SUCCESS;
    }

    private int reportSettings(CommandSourceStack source, String subjectName) {
        Audience sender = source.getSender();
        Optional<Setting.Subject> subject = subject(subjectName);
        if (subject.isEmpty()) {
            sender.sendMessage(Component.text("Nothing configurable is called " + subjectName + ".",
                    NamedTextColor.RED));
            return Command.SINGLE_SUCCESS;
        }
        for (Setting setting : subject.get().settings()) {
            int value = settings.get(setting);
            sender.sendMessage(Component.text(subjectName + " " + setting.commandName() + " = " + value + " "
                    + setting.unit() + (value == setting.defaultValue() ? ""
                            : " (" + setting.defaultValue() + " by default)"), NamedTextColor.LIGHT_PURPLE));
        }
        return Command.SINGLE_SUCCESS;
    }

    // Takes effect at once, and is saved.
    private int set(CommandSourceStack source, String subjectName, String settingName, int value) {
        Audience sender = source.getSender();
        Optional<Setting> named = subject(subjectName).flatMap(subject -> subject.settings().stream()
                .filter(setting -> setting.commandName().equals(settingName)).findFirst());
        if (named.isEmpty()) {
            sender.sendMessage(Component.text("There is no setting " + subjectName + " " + settingName + ".",
                    NamedTextColor.RED));
            return Command.SINGLE_SUCCESS;
        }
        Setting setting = named.get();
        try {
            settings.set(setting, value);
        } catch (IllegalArgumentException e) {
            sender.sendMessage(Component.text(e.getMessage() + ".", NamedTextColor.RED));
            return Command.SINGLE_SUCCESS;
        }
        if (setting.subject() == Setting.Subject.RUMOR) {
            rumors.scheduleNext();
        }
        String done = subjectName + " " + settingName + " set to " + value + " " + setting.unit() + ".";
        getLogger().info(source.getSender().getName() + ": " + done);
        try {
            settings.save(settingsFile);
            sender.sendMessage(Component.text(done, NamedTextColor.LIGHT_PURPLE));
        } catch (IOException e) {
            getLogger().log(Level.SEVERE, "Couldn't save " + settingsFile + "; the change lasts until a restart", e);
            sender.sendMessage(Component.text(done + " It couldn't be saved, so it lasts until a restart; see the"
                    + " server log.", NamedTextColor.RED));
        }
        return Command.SINGLE_SUCCESS;
    }

    private static Component placement(Optional<Pig> placed) {
        return placed.map(pig -> Component.text("Placed Technoblade " + pig.getUniqueId() + " at "
                        + describe(pig.getLocation()) + ".", NamedTextColor.LIGHT_PURPLE))
                .orElseGet(() -> Component.text("Couldn't place Technoblade; see the server log.",
                        NamedTextColor.RED));
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
