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
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

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
 * <p>A passenger doesn't turn with its vehicle, so every tick the plugin turns
 * the crown twice over, as a pig's head turns. The display's yaw is the body's,
 * which swings the crown round the pig's centre with its body. Its
 * transformation then turns it by the head's yaw relative to the body, round
 * the pig's neck, {@value #NECK} blocks ahead of its centre, where the head
 * model pivots. Each change is interpolated over one tick, the shortest there
 * is. The crown doesn't follow the head's pitch.
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
    // The pig model's head pivot: 6 pixels ahead of the body's centre.
    private static final float NECK = 0.375f;
    // Below this, a change in the head's turn isn't sent.
    private static final float TURN_STEP = 0.5f;

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
            display.setInterpolationDuration(TURN_TICKS);
            display.setRotation(pig.getBodyYaw(), 0);
            // The next tick turns it to the head.
        });
        pig.addPassenger(crown);
        return crown;
    }

    static void face(ItemDisplay crown, Pig pig, Logger logger) {
        float body = Location.normalizeYaw(pig.getBodyYaw());
        if (crown.getYaw() != body) {
            crown.setRotation(body, 0);
        }
        float turn = Location.normalizeYaw(headYaw(pig, logger) - body);
        float shown = Location.normalizeYaw(turn(crown.getTransformation()));
        if (Math.abs(Location.normalizeYaw(turn - shown)) >= TURN_STEP) {
            crown.setTransformation(turnedAtNeck(turn));
            crown.setInterpolationDelay(0);
        }
    }

    /**
     * A turn of {@code degrees} of yaw round the neck. The display turns by
     * yaw y as a rotation of -y about its up axis, so the head's turn does
     * too; the translation keeps the neck where it is.
     */
    static Transformation turnedAtNeck(float degrees) {
        float radians = (float) Math.toRadians(-degrees);
        Quaternionf rotation = new Quaternionf().rotationY(radians);
        Vector3f neck = new Vector3f(0, 0, NECK);
        Vector3f translation = new Vector3f(neck).sub(rotation.transform(new Vector3f(neck)));
        return new Transformation(translation, rotation, new Vector3f(1, 1, 1), new Quaternionf());
    }

    // The yaw a transformation from turnedAtNeck turns by.
    static float turn(Transformation transformation) {
        Vector3f forward = transformation.getLeftRotation().transform(new Vector3f(0, 0, 1));
        return (float) -Math.toDegrees(Math.atan2(forward.x, forward.z));
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
