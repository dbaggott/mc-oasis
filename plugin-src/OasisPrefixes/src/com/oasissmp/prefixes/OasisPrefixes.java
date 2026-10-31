package com.oasissmp.prefixes;

import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.event.context.ContextUpdateEvent;
import net.luckperms.api.event.user.UserDataRecalculateEvent;
import net.luckperms.api.platform.PlayerAdapter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Puts a player's LuckPerms prefix before their name in their display name, as
 * TAB puts it in the tab list and over their head. Chat shows the display name
 * through the server's own format, so messages go out exactly as signed.
 *
 * <p>The display name is set on joining and again whenever the prefix can
 * change: LuckPerms recalculating the player's data, or their contexts
 * changing, as going on or off duty (OasisStaff's /staff) changes them.
 */
public final class OasisPrefixes extends JavaPlugin implements Listener {

    private PlayerAdapter<Player> players;

    @Override
    public void onEnable() {
        LuckPerms luckPerms = getServer().getServicesManager().load(LuckPerms.class);
        if (luckPerms == null) {
            throw new IllegalStateException("LuckPerms' API isn't registered");
        }
        players = luckPerms.getPlayerAdapter(Player.class);
        // Subscribed as this plugin, so LuckPerms drops them when the plugin disables.
        luckPerms.getEventBus().subscribe(this, UserDataRecalculateEvent.class,
                event -> refresh(event.getUser().getUniqueId()));
        luckPerms.getEventBus().subscribe(this, ContextUpdateEvent.class,
                event -> event.getSubject(Player.class).ifPresent(player -> refresh(player.getUniqueId())));
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getOnlinePlayers().forEach(this::show);
    }

    // After OasisStaff, at LOWEST, has put a player who left off duty back off it.
    @EventHandler(priority = EventPriority.LOW)
    public void onJoin(PlayerJoinEvent event) {
        show(event.getPlayer());
    }

    // LuckPerms' events can fire off the main thread; the display name is set on it.
    private void refresh(UUID playerId) {
        getServer().getScheduler().runTask(this, () -> {
            Player player = getServer().getPlayer(playerId);
            if (player != null) {
                show(player);
            }
        });
    }

    // With no prefix, the display name goes back to Paper's own.
    private void show(Player player) {
        String prefix = players.getMetaData(player).getPrefix();
        player.displayName(prefix == null ? null : withPrefix(prefix, Component.text(player.getName())));
    }

    // The prefixes are MiniMessage, as TAB reads them too.
    static Component withPrefix(String prefix, Component name) {
        return MiniMessage.miniMessage().deserialize(prefix).append(name);
    }
}
