package com.oasissmp.technoblade;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;
import java.util.function.Supplier;
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
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * A player who feeds Technoblade what a pig eats gets a golden apple back,
 * now and then with a gold nugget or a while of Luck or Speed
 * ({@link Gifts}), and is guarded by it ({@link Bodyguard}). They then wait
 * out a cooldown before the next. Technoblade eats the food rather than
 * breeding on it; while a player is waiting it doesn't take theirs.
 */
final class Feeding implements Listener {

    private final Predicate<Entity> isTechnoblade;
    private final Supplier<Settings> current;
    private final Guarding guarding;
    private final Cooldown cooldown = new Cooldown();

    Feeding(Predicate<Entity> isTechnoblade, Supplier<Settings> current, Guarding guarding) {
        this.isTechnoblade = isTechnoblade;
        this.current = current;
        this.guarding = guarding;
    }

    // First, and cancelling it: WorldGuard skips a cancelled interaction, so
    // Technoblade is fed even in a region whose interact flag would deny it, spawn among them.
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onFeed(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Pig pig) || !isTechnoblade.test(pig)) {
            return;
        }
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        ItemStack food = player.getInventory().getItem(event.getHand());
        if (!pig.isBreedItem(food)) {
            return;
        }
        event.setCancelled(true);
        // As an Audience: Player's own sendActionBar and sendMessage overloads need Bungee's chat API to resolve.
        Audience told = player;
        Settings settings = current.get();
        Instant now = Instant.now();
        Optional<Duration> wait = cooldown.take(player.getUniqueId(), now, settings.feedCooldown());
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
        // In chat: a held right-click repeats the interaction, and the refusal that follows takes the action bar.
        told.sendMessage(Component.text("Technoblade gave you a golden apple.", NamedTextColor.LIGHT_PURPLE));
        Gifts gifts = Gifts.roll(ThreadLocalRandom.current(), settings.nuggetOneIn(), settings.effectOneIn());
        if (gifts.nugget()) {
            player.give(ItemStack.of(Material.GOLD_NUGGET));
            told.sendMessage(Component.text("Technoblade gave you a gold nugget.", NamedTextColor.LIGHT_PURPLE));
        }
        gifts.effect().ifPresent(effect -> {
            // Level I. Over an effect the player already has, the longer or stronger of the two stays.
            player.addPotionEffect(new PotionEffect(type(effect), (int) Settings.ticks(settings.effectFor()), 0));
            told.sendMessage(Component.text("Technoblade gave you " + name(effect) + ".",
                    NamedTextColor.LIGHT_PURPLE));
        });
        guarding.start(player.getUniqueId(), now, settings.guardFor());
    }

    private static PotionEffectType type(Gifts.Effect effect) {
        return switch (effect) {
            case LUCK -> PotionEffectType.LUCK;
            case SPEED -> PotionEffectType.SPEED;
        };
    }

    private static String name(Gifts.Effect effect) {
        return switch (effect) {
            case LUCK -> "Luck";
            case SPEED -> "Speed";
        };
    }

    // Rounded up, so the last second reads 0:01 rather than 0:00.
    private static String minutes(Duration wait) {
        long seconds = wait.plusNanos(999_999_999).toSeconds();
        return seconds / 60 + ":" + String.format("%02d", seconds % 60);
    }
}
