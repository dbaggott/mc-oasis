package com.oasissmp.technoblade;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.function.Predicate;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Pig;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.ItemStack;

/**
 * A player who feeds Technoblade what a pig eats gets a golden apple back,
 * once every {@link #COOLDOWN} each. Technoblade eats the food itself rather
 * than breeding on it; while a player is waiting it doesn't take theirs.
 */
final class Feeding implements Listener {

    static final Duration COOLDOWN = Duration.ofMinutes(5);

    private final Predicate<Entity> isTechnoblade;
    private final Cooldown cooldown = new Cooldown(COOLDOWN);

    Feeding(Predicate<Entity> isTechnoblade) {
        this.isTechnoblade = isTechnoblade;
    }

    @EventHandler(ignoreCancelled = true)
    public void onFeed(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Pig pig) || !isTechnoblade.test(pig)) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack food = player.getInventory().getItem(event.getHand());
        if (!pig.isBreedItem(food)) {
            return;
        }
        event.setCancelled(true);
        // As an Audience: Player's own sendActionBar overloads need Bungee's chat API to resolve.
        Audience told = player;
        Optional<Duration> wait = cooldown.take(player.getUniqueId(), Instant.now());
        if (wait.isPresent()) {
            told.sendActionBar(Component.text("Technoblade isn't hungry. Try again in " + minutes(wait.get()) + ".",
                    NamedTextColor.LIGHT_PURPLE));
            return;
        }
        if (player.getGameMode() != GameMode.CREATIVE) {
            player.getInventory().setItem(event.getHand(), food.subtract());
        }
        player.swingHand(event.getHand());
        pig.getWorld().playSound(pig, Sound.ENTITY_GENERIC_EAT, 1, 1);
        // Into the inventory, or dropped at the player's feet when it is full.
        player.give(ItemStack.of(Material.GOLDEN_APPLE));
        told.sendActionBar(Component.text("Technoblade gave you a golden apple.", NamedTextColor.LIGHT_PURPLE));
    }

    // Rounded up, so the last second reads 0:01 rather than 0:00.
    private static String minutes(Duration wait) {
        long seconds = wait.plusNanos(999_999_999).toSeconds();
        return seconds / 60 + ":" + String.format("%02d", seconds % 60);
    }
}
