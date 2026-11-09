package com.oasissmp.technoblade;

import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Pig;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Technoblade's crown: an item display riding the pig, tagged {@value #TAG}.
 * A pig renders nothing on its head, so the crown is an entity of its own.
 *
 * <p>The item is a golden helmet whose {@code item_model} is
 * {@value #MODEL}, from the server's resource pack ({@code resourcepack/} in
 * the repo). The model itself sits the crown on the pig's head, measured from
 * where a passenger rides, so the display needs no transformation; a client
 * without the model sees the golden helmet on the pig's back. A passenger
 * doesn't turn with its vehicle, so the plugin turns the crown to where the
 * pig's head faces every tick, and a one-tick teleport duration smooths each
 * turn.
 *
 * <p>The API has no head yaw, so it is read from the server's own entity by
 * reflection: {@code getHandle()} on the Bukkit entity, then vanilla's
 * {@code Entity.getYHeadRot()}. If either is ever missing, the crown follows
 * the body instead, and the first failure is logged.
 */
final class Crown {

    static final String TAG = "technoblade_crown";
    static final NamespacedKey MODEL = new NamespacedKey("oasis", "technoblade_crown");

    private static final int TURN_TICKS = 1;

    private Crown() { }

    static boolean is(Entity entity) {
        return entity instanceof ItemDisplay && entity.getScoreboardTags().contains(TAG);
    }

    // Spawned at the pig and mounted on it straight away.
    static ItemDisplay place(Pig pig) {
        ItemDisplay crown = pig.getWorld().spawn(pig.getLocation(), ItemDisplay.class, display -> {
            display.addScoreboardTag(TAG);
            display.setPersistent(true);
            display.setItemStack(item());
            display.setTeleportDuration(TURN_TICKS);
            display.setRotation(pig.getBodyYaw(), 0);
            // The next tick turns it to the head.
        });
        pig.addPassenger(crown);
        return crown;
    }

    static void face(ItemDisplay crown, Pig pig, Logger logger) {
        float yaw = Location.normalizeYaw(headYaw(pig, logger));
        if (crown.getYaw() != yaw) {
            crown.setRotation(yaw, 0);
        }
    }

    private static boolean headYawFailed;

    private static float headYaw(Pig pig, Logger logger) {
        if (!headYawFailed) {
            try {
                Object handle = pig.getClass().getMethod("getHandle").invoke(pig);
                return (float) handle.getClass().getMethod("getYHeadRot").invoke(handle);
            } catch (ReflectiveOperationException | ClassCastException e) {
                headYawFailed = true;
                logger.log(Level.SEVERE, "Couldn't read Technoblade's head yaw, so its crown follows its body", e);
            }
        }
        return pig.getBodyYaw();
    }

    private static ItemStack item() {
        ItemStack item = ItemStack.of(Material.GOLDEN_HELMET);
        ItemMeta meta = item.getItemMeta();
        meta.setItemModel(MODEL);
        item.setItemMeta(meta);
        return item;
    }
}
