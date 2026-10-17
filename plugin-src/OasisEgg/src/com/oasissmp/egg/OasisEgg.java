package com.oasissmp.egg;

import com.mojang.brigadier.Command;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.EntitiesUnloadEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * {@code /egg}, for players with {@value #PERMISSION} and the console: where
 * every dragon egg is, and what it's in or who has it. It searches everything
 * loaded, and logs where eggs are as each place goes out of reach, so an egg
 * in an unloaded chunk or with an offline player is found where it was left.
 */
public final class OasisEgg extends JavaPlugin implements Listener {

    static final String PERMISSION = "oasisegg.locate";

    private EggFinder finder;
    private SightingLog log;

    @Override
    public void onEnable() {
        finder = new EggFinder(getServer());
        try {
            log = SightingLog.load(getDataFolder().toPath().resolve("sightings.txt"));
        } catch (IOException e) {
            throw new UncheckedIOException("Couldn't read the egg sightings", e);
        }
        getServer().getPluginManager().registerEvents(this, this);
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event ->
                event.registrar().register(Commands.literal("egg")
                        // Players need the permission even to see the command.
                        .requires(source -> !(source.getSender() instanceof Player)
                                || source.getSender().hasPermission(PERMISSION))
                        .executes(context -> locate(context.getSource()))
                        .build(), "Find the dragon egg"));
    }

    // Everything still loaded is about to go out of reach without an event of its own.
    @Override
    public void onDisable() {
        if (log != null) {
            finder.inLoaded().forEach(this::record);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        record(new Source.Player(event.getPlayer().getUniqueId()), finder.inPlayer(event.getPlayer()));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkUnload(ChunkUnloadEvent event) {
        record(EggFinder.blocksOf(event.getChunk()), finder.inBlocks(event.getChunk()));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntitiesUnload(EntitiesUnloadEvent event) {
        record(EggFinder.entitiesOf(event.getChunk()), finder.inEntities(event.getChunk(), event.getEntities()));
    }

    private void record(Source source, List<Sighting> sightings) {
        try {
            log.replace(source, sightings);
        } catch (IOException e) {
            throw new UncheckedIOException("Couldn't save the egg sightings", e);
        }
    }

    private int locate(CommandSourceStack source) {
        List<Sighting> live = finder.inLoaded().values().stream().flatMap(List::stream).toList();
        List<String> lines = Report.lines(live, log.all(), this::isLoaded);
        // Messages go to it as an Audience: CommandSender's own sendMessage
        // overloads reach for BungeeCord's chat classes, which aren't here.
        Audience sender = source.getSender();
        sender.sendMessage(Component.text(lines.getFirst(), NamedTextColor.GOLD));
        lines.subList(1, lines.size()).forEach(line -> sender.sendMessage(Component.text(line)));
        return Command.SINGLE_SUCCESS;
    }

    private boolean isLoaded(Source source) {
        return switch (source) {
            case Source.Player player -> getServer().getPlayer(player.id()) != null;
            case Source.Blocks blocks -> loadedChunk(blocks.world(), blocks.chunkX(), blocks.chunkZ()) != null;
            case Source.Entities entities -> {
                Chunk chunk = loadedChunk(entities.world(), entities.chunkX(), entities.chunkZ());
                yield chunk != null && chunk.isEntitiesLoaded();
            }
        };
    }

    // Null when it isn't loaded: getChunkAt would load it.
    private Chunk loadedChunk(String worldKey, int chunkX, int chunkZ) {
        World world = getServer().getWorld(Key.key(worldKey));
        return world != null && world.isChunkLoaded(chunkX, chunkZ) ? world.getChunkAt(chunkX, chunkZ) : null;
    }
}
