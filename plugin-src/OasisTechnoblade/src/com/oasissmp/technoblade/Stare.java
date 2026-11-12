package com.oasissmp.technoblade;

import com.destroystokyo.paper.entity.ai.Goal;
import com.destroystokyo.paper.entity.ai.GoalKey;
import com.destroystokyo.paper.entity.ai.GoalType;
import java.util.EnumSet;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Pig;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * The death stare: the Technoblade that replaces one a player killed stands
 * still and looks at its killer for a while, with red eyes for Java players.
 * It ends early when the killer goes offline, changes world or gets too far
 * away, or when the pig stops being Technoblade. There is one stare at a time: a new one ends the last.
 */
final class Stare {

    private final Plugin plugin;
    private final Settings settings;
    private final Predicate<Entity> isTechnoblade;
    // Gives a pig the look its state calls for; called as a stare starts and ends.
    private final Consumer<Pig> dress;
    private Pig pig;
    private UUID killer;
    private int ends;
    private BukkitTask task;

    Stare(Plugin plugin, Settings settings, Predicate<Entity> isTechnoblade, Consumer<Pig> dress) {
        this.plugin = plugin;
        this.settings = settings;
        this.isTechnoblade = isTechnoblade;
        this.dress = dress;
    }

    void start(Pig staring, Player at) {
        end();
        pig = staring;
        killer = at.getUniqueId();
        ends = plugin.getServer().getCurrentTick() + (int) Settings.ticks(settings.stareFor());
        dress.accept(pig);
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 0, 1);
    }

    boolean isOn(Pig candidate) {
        return pig != null && pig.getUniqueId().equals(candidate.getUniqueId());
    }

    private void tick() {
        Player at = plugin.getServer().getPlayer(killer);
        if (plugin.getServer().getCurrentTick() >= ends || !pig.isValid() || !isTechnoblade.test(pig) || at == null
                || !at.getWorld().equals(pig.getWorld())
                || at.getLocation().distanceSquared(pig.getLocation()) > settings.stareReach() * settings.stareReach()) {
            end();
            return;
        }
        pig.lookAt(at);
    }

    private void end() {
        if (pig == null) {
            return;
        }
        Pig stared = pig;
        pig = null;
        killer = null;
        task.cancel();
        if (stared.isValid()) {
            dress.accept(stared);
        }
    }

    /**
     * The goal that keeps {@code staring} still and its head free for a stare.
     * While a stare is on, it holds the pig's moving and looking ahead of
     * every other goal, vanilla's among them: panicking, following food,
     * looking at players and looking around.
     */
    Goal<Pig> hold(Pig staring, GoalKey<Pig> key) {
        return new Goal<>() {
            @Override
            public boolean shouldActivate() {
                return isOn(staring);
            }

            @Override
            public void start() {
                staring.getPathfinder().stopPathfinding();
            }

            @Override
            public GoalKey<Pig> getKey() {
                return key;
            }

            @Override
            public EnumSet<GoalType> getTypes() {
                return EnumSet.of(GoalType.MOVE, GoalType.LOOK);
            }
        };
    }
}
