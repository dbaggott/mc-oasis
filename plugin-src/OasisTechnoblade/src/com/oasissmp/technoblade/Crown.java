package com.oasissmp.technoblade;

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
 * doesn't turn with its vehicle, so the plugin turns the crown to the pig's
 * body yaw every tick, and the teleport duration smooths each turn.
 */
final class Crown {

    static final String TAG = "technoblade_crown";
    static final NamespacedKey MODEL = new NamespacedKey("oasis", "technoblade_crown");

    private static final int TURN_TICKS = 2;

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
        });
        pig.addPassenger(crown);
        return crown;
    }

    static void face(ItemDisplay crown, Pig pig) {
        float yaw = Location.normalizeYaw(pig.getBodyYaw());
        if (crown.getYaw() != yaw) {
            crown.setRotation(yaw, 0);
        }
    }

    private static ItemStack item() {
        ItemStack item = ItemStack.of(Material.GOLDEN_HELMET);
        ItemMeta meta = item.getItemMeta();
        meta.setItemModel(MODEL);
        item.setItemMeta(meta);
        return item;
    }
}
