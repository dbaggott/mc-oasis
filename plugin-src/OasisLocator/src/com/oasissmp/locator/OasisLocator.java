package com.oasissmp.locator;

import com.destroystokyo.paper.event.player.PlayerPostRespawnEvent;
import com.mojang.brigadier.Command;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import java.util.List;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * {@code /locatorbar on} and {@code /locatorbar off}, for every player. Every
 * player is on the locator bar until they opt out. One who has opted out is
 * on nobody's bar and has nobody on theirs.
 *
 * <p>Vanilla shows a player on another's bar only while they are within the
 * smaller of the first's waypoint transmit range and the second's waypoint
 * receive range. Opting out brings both of the player's ranges to 0 with a
 * modifier, which works in both directions. The modifier is transient, so it
 * is put back on joining and respawning; the opt-out itself is saved with the
 * player, so it lasts through leaving and restarts.
 */
public final class OasisLocator extends JavaPlugin implements Listener {

    private static final List<Attribute> RANGES =
            List.of(Attribute.WAYPOINT_TRANSMIT_RANGE, Attribute.WAYPOINT_RECEIVE_RANGE);

    private static final Component NOT_IN_CONSOLE = Component.text("Not available in console.", NamedTextColor.RED);

    private NamespacedKey hiddenKey;
    // Multiplies the range by 1 + -1.
    private AttributeModifier hiding;

    @Override
    public void onEnable() {
        hiddenKey = new NamespacedKey(this, "hidden");
        hiding = new AttributeModifier(hiddenKey, -1, AttributeModifier.Operation.MULTIPLY_SCALAR_1);
        getServer().getPluginManager().registerEvents(this, this);
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event ->
                event.registrar().register(Commands.literal("locatorbar")
                        .executes(context -> report(context.getSource()))
                        .then(Commands.literal("on").executes(context -> set(context.getSource(), true)))
                        .then(Commands.literal("off").executes(context -> set(context.getSource(), false)))
                        .build(), "Enable or disable your participation in the locator bar."));
        getServer().getOnlinePlayers().forEach(this::apply);
    }

    // Before other plugins see the player, so nothing sees their ranges before the opt-out is applied.
    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        apply(event.getPlayer());
    }

    // A respawned player's attributes keep only their base values.
    @EventHandler
    public void onRespawn(PlayerPostRespawnEvent event) {
        apply(event.getPlayer());
    }

    private boolean isHidden(Player player) {
        return player.getPersistentDataContainer().has(hiddenKey);
    }

    private void apply(Player player) {
        boolean hidden = isHidden(player);
        for (Attribute range : RANGES) {
            AttributeInstance instance = player.getAttribute(range);
            if (instance == null) {
                throw new IllegalStateException(player.getName() + " has no " + range.getKey() + " attribute");
            }
            boolean has = instance.getModifier(hiddenKey) != null;
            if (hidden && !has) {
                instance.addTransientModifier(hiding);
            } else if (!hidden && has) {
                instance.removeModifier(hiddenKey);
            }
        }
    }

    private int report(CommandSourceStack source) {
        Audience sender = source.getSender();
        if (!(source.getSender() instanceof Player player)) {
            sender.sendMessage(NOT_IN_CONSOLE);
            return 0;
        }
        sender.sendMessage(describe(!isHidden(player)));
        return Command.SINGLE_SUCCESS;
    }

    private int set(CommandSourceStack source, boolean on) {
        Audience sender = source.getSender();
        if (!(source.getSender() instanceof Player player)) {
            sender.sendMessage(NOT_IN_CONSOLE);
            return 0;
        }
        if (on) {
            player.getPersistentDataContainer().remove(hiddenKey);
        } else {
            player.getPersistentDataContainer().set(hiddenKey, PersistentDataType.BOOLEAN, true);
        }
        apply(player);
        sender.sendMessage(describe(on));
        return Command.SINGLE_SUCCESS;
    }

    private static Component describe(boolean on) {
        return on
                ? Component.text("Your locator bar is enabled.", NamedTextColor.GREEN)
                : Component.text("Your locator bar is disabled (and you won't show up for other players).",
                        NamedTextColor.GREEN);
    }
}
