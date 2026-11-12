package com.oasissmp.technoblade;

import java.util.function.Predicate;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

/**
 * A player who hits Technoblade, by hand or with a projectile, is hurt and
 * thrown back ({@link Retaliation}), and the fall from the throw doesn't hurt
 * them ({@link Throws}). The pig takes the hit all the same.
 */
final class Hitting implements Listener {

    // The blocks a player can fall unhurt: further than a jump takes them.
    private static final float OWN_FALL = 3;

    private final Plugin plugin;
    private final Predicate<Entity> isTechnoblade;
    private final Settings settings;
    private final Throws thrown = new Throws();
    // Runs every tick while anyone is mid-throw.
    private BukkitTask landing;

    Hitting(Plugin plugin, Predicate<Entity> isTechnoblade, Settings settings) {
        this.plugin = plugin;
        this.isTechnoblade = isTechnoblade;
        this.settings = settings;
    }

    // Only a hit that landed: one a protected region cancelled costs the attacker nothing.
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        // A sweep that caught it wasn't aimed at it.
        if (!isTechnoblade.test(event.getEntity())
                || event.getCause() == EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK) {
            return;
        }
        Player attacker = attacker(event.getDamager());
        if (attacker == null || attacker.isDead() || attacker.getGameMode() == GameMode.CREATIVE
                || attacker.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        // Set rather than dealt as damage, which armor would reduce and which could kill.
        attacker.setHealth(Retaliation.healthAfter(attacker.getHealth(), settings.get(Setting.HIT_DAMAGE)));
        attacker.playHurtAnimation(0);
        attacker.getWorld().playSound(attacker, Sound.ENTITY_PLAYER_HURT, 1, 1);
        // A fall the attacker was already in when they hit it is their own, and still hurts.
        if (attacker.getFallDistance() <= OWN_FALL) {
            thrown.start(attacker.getUniqueId(), plugin.getServer().getCurrentTick());
            if (landing == null) {
                landing = plugin.getServer().getScheduler().runTaskTimer(plugin, this::land, 1, 1);
            }
        }
        Location pig = event.getEntity().getLocation();
        // On the next tick: the hit's own knockback and velocity updates come after this event and would overwrite it.
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!attacker.isValid() || !attacker.getWorld().equals(pig.getWorld())) {
                return;
            }
            Location at = attacker.getLocation();
            Retaliation.Heading away = Retaliation.away(at.getX() - pig.getX(), at.getZ() - pig.getZ(), at.getYaw());
            double speed = settings.get(Setting.HIT_SPEED) / 100.0;
            attacker.setVelocity(new Vector(away.x() * speed, settings.get(Setting.HIT_LIFT) / 100.0,
                    away.z() * speed));
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onFall(EntityDamageEvent event) {
        if (event.getCause() == EntityDamageEvent.DamageCause.FALL && event.getEntity() instanceof Player player
                && thrown.endsWithFall(player.getUniqueId(), plugin.getServer().getCurrentTick())) {
            event.setCancelled(true);
        }
    }

    private static Player attacker(Entity damager) {
        if (damager instanceof Projectile projectile) {
            return projectile.getShooter() instanceof Player shooter ? shooter : null;
        }
        return damager instanceof Player player ? player : null;
    }

    private void land() {
        thrown.land(plugin.getServer().getCurrentTick(), player -> {
            Entity thrownPlayer = plugin.getServer().getPlayer(player);
            return thrownPlayer == null || thrownPlayer.isOnGround();
        });
        if (thrown.isEmpty()) {
            landing.cancel();
            landing = null;
        }
    }
}
