package com.oasissmp.technoblade;

import java.util.Comparator;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import java.util.logging.Level;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.entity.Pig;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Rumors: every so often ({@link Rumor#delayTicks}), while anyone is online
 * and there is a Technoblade, every player is told where it was seen: near
 * the closest player to it, if one is near enough, or in which biome. Never
 * its coordinates.
 */
final class Rumors {

    private final Plugin plugin;
    private final Settings settings;
    private final Supplier<PigRecord> record;
    private final Supplier<Optional<Pig>> technoblade;
    private BukkitTask next;

    Rumors(Plugin plugin, Settings settings, Supplier<PigRecord> record, Supplier<Optional<Pig>> technoblade) {
        this.plugin = plugin;
        this.settings = settings;
        this.record = record;
        this.technoblade = technoblade;
    }

    /** Starts the wait for the next rumor, in place of any wait already on, by the settings as they are now. */
    void scheduleNext() {
        if (next != null) {
            next.cancel();
        }
        next = plugin.getServer().getScheduler().runTaskLater(plugin, this::spread,
                Rumor.delayTicks(ThreadLocalRandom.current(), settings.minutes(Setting.RUMOR_INTERVAL),
                        settings.minutes(Setting.RUMOR_JITTER)));
    }

    private void spread() {
        scheduleNext();
        PigRecord last = record.get();
        if (last == null || plugin.getServer().getOnlinePlayers().isEmpty()) {
            return;
        }
        Optional<Pig> pig = technoblade.get();
        Optional<Player> near = pig.flatMap(this::nearest);
        if (Rumor.choose(near.isPresent(), ThreadLocalRandom.current()) == Rumor.Hint.PLAYER) {
            tell(Component.text("Technoblade was seen near " + near.get().getName() + "."));
            return;
        }
        World world = pig.map(Pig::getWorld).orElseGet(() -> plugin.getServer().getWorld(Key.key(last.world())));
        if (world == null) {
            return;
        }
        Pos pos = pig.map(Pig::getLocation)
                .map(at -> new Pos(at.getBlockX(), at.getBlockY(), at.getBlockZ())).orElse(last.pos());
        // Loaded first, off the main thread: an unloaded pig's biome is read without stalling the server.
        world.getChunkAtAsync(pos.x() >> 4, pos.z() >> 4)
                .thenAccept(chunk -> {
                    Biome biome = world.getBiome(pos.x(), pos.y(), pos.z());
                    tell(Component.text("Technoblade was seen in ")
                            .append(Component.translatable(biome.translationKey(),
                                    Rumor.biomeName(biome.getKey().getKey())))
                            .append(Component.text(".")));
                })
                .exceptionally(e -> {
                    plugin.getLogger().log(Level.WARNING, "Couldn't read Technoblade's biome at " + pos
                            + " for a rumor, so none was told", e);
                    return null;
                });
    }

    // Not one in spectator: a staff member watching unseen is never named.
    private Optional<Player> nearest(Pig pig) {
        Location at = pig.getLocation();
        double near = settings.get(Setting.RUMOR_REACH);
        return pig.getWorld().getPlayers().stream()
                .filter(player -> player.getGameMode() != GameMode.SPECTATOR)
                .filter(player -> player.getLocation().distanceSquared(at) <= near * near)
                .min(Comparator.comparingDouble(player -> player.getLocation().distanceSquared(at)));
    }

    private void tell(Component rumor) {
        Component colored = rumor.color(NamedTextColor.LIGHT_PURPLE);
        for (Audience player : plugin.getServer().getOnlinePlayers()) {
            player.sendMessage(colored);
        }
    }
}
