package com.oasissmp.technoblade;

import java.time.Instant;
import java.util.Optional;
import java.util.function.Supplier;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Pig;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;

/**
 * Technoblade guards whoever fed it ({@link Guarding}): a monster within
 * reach of it that goes for a guarded player goes for it instead. One already on the player when the guarding starts stays on them
 * until it next picks a target.
 */
final class Bodyguard implements Listener {

    private final Guarding guarding;
    private final Supplier<Optional<Pig>> technoblade;
    private final double reach;

    Bodyguard(Guarding guarding, double reach, Supplier<Optional<Pig>> technoblade) {
        this.guarding = guarding;
        this.reach = reach;
        this.technoblade = technoblade;
    }

    @EventHandler(ignoreCancelled = true)
    public void onTarget(EntityTargetLivingEntityEvent event) {
        if (!(event.getEntity() instanceof Monster monster) || !(event.getTarget() instanceof Player player)
                || !guarding.guards(player.getUniqueId(), Instant.now())) {
            return;
        }
        technoblade.get()
                .filter(pig -> pig.getWorld().equals(monster.getWorld())
                        && pig.getLocation().distanceSquared(monster.getLocation()) <= reach * reach)
                .ifPresent(event::setTarget);
    }
}
