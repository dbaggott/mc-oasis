package com.oasissmp.technoblade;

import io.papermc.paper.event.player.PlayerNameEntityEvent;
import java.util.function.Predicate;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDispenseArmorEvent;
import org.bukkit.event.entity.EntityMountEvent;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.entity.EntityTransformEvent;
import org.bukkit.event.entity.PlayerLeashEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.vehicle.VehicleEnterEvent;

/**
 * Turns away every vanilla mechanic that would take Technoblade away without
 * killing it: turning into a zombified piglin, a name tag, a lead, a boat or
 * minecart, a portal, a saddle, and anything climbing on.
 */
final class Guard implements Listener {

    private final Predicate<Entity> isTechnoblade;

    Guard(Predicate<Entity> isTechnoblade) {
        this.isTechnoblade = isTechnoblade;
    }

    // Lightning turning it into a zombified piglin among them.
    @EventHandler(ignoreCancelled = true)
    public void onTransform(EntityTransformEvent event) {
        refuse(event.getEntity(), event);
    }

    @EventHandler(ignoreCancelled = true)
    public void onName(PlayerNameEntityEvent event) {
        refuse(event.getEntity(), event);
    }

    @EventHandler(ignoreCancelled = true)
    public void onLeash(PlayerLeashEntityEvent event) {
        refuse(event.getEntity(), event);
    }

    @EventHandler(ignoreCancelled = true)
    public void onVehicle(VehicleEnterEvent event) {
        refuse(event.getEntered(), event);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPortal(EntityPortalEvent event) {
        refuse(event.getEntity(), event);
    }

    @EventHandler(ignoreCancelled = true)
    public void onMount(EntityMountEvent event) {
        refuse(event.getMount(), event);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDispenseSaddle(BlockDispenseArmorEvent event) {
        refuse(event.getTargetEntity(), event);
    }

    @EventHandler(ignoreCancelled = true)
    public void onSaddle(PlayerInteractEntityEvent event) {
        if (event.getPlayer().getInventory().getItem(event.getHand()).getType() == Material.SADDLE) {
            refuse(event.getRightClicked(), event);
        }
    }

    private void refuse(Entity entity, Cancellable event) {
        if (isTechnoblade.test(entity)) {
            event.setCancelled(true);
        }
    }
}
