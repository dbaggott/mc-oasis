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
 * Technoblade's crown as Bedrock players see it: an item display riding the
 * pig, tagged {@value #TAG}. Java players see the crown painted on the pig's
 * head instead ({@link OasisTechnoblade}'s pig variant), which moves exactly
 * with the head; a crown riding the pig lags the head, so Java players don't
 * see this one.
 *
 * <p>The item is a golden helmet whose {@code item_model} is {@value #MODEL}.
 * The server's Java resource pack ({@code resourcepack/}) makes that model
 * empty. Geyser maps it to a Bedrock item ({@code
 * plugins/Geyser-Spigot/custom_mappings/}) whose attachable, in {@code
 * resourcepack-bedrock/}, draws the crown, and the GeyserDisplayEntity
 * extension shows the display to Bedrock players. A Java client too old for
 * {@code item_model} sees the golden helmet on the pig's back.
 *
 * <p>A passenger doesn't turn with its vehicle, so every tick the plugin
 * turns the crown to the pig's body yaw, smoothed over a one-tick teleport
 * duration. The crown doesn't follow the head when it turns from the body.
 * GeyserDisplayEntity turns the Bedrock entity about its own origin, the
 * pig's centre, so following the head's yaw would swing the crown further off
 * the head than staying with the body does.
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
        });
        pig.addPassenger(crown);
        return crown;
    }

    static void face(ItemDisplay crown, Pig pig) {
        float body = Location.normalizeYaw(pig.getBodyYaw());
        if (crown.getYaw() != body) {
            crown.setRotation(body, 0);
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
