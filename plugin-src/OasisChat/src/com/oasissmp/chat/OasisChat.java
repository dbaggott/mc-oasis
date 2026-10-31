package com.oasissmp.chat;

import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.platform.PlayerAdapter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Puts a player's LuckPerms prefix before each chat message they send, as TAB
 * puts it before their name in the tab list and over their head. It's read as
 * the message is sent, so it's always the current one, duty included.
 *
 * <p>A player with no prefix keeps the server's own renderer: Paper sends a
 * message another renderer formatted with that formatting as unsigned content.
 */
public final class OasisChat extends JavaPlugin implements Listener {

    private PlayerAdapter<Player> players;

    @Override
    public void onEnable() {
        LuckPerms luckPerms = getServer().getServicesManager().load(LuckPerms.class);
        if (luckPerms == null) {
            throw new IllegalStateException("LuckPerms' API isn't registered");
        }
        players = luckPerms.getPlayerAdapter(Player.class);
        getServer().getPluginManager().registerEvents(this, this);
    }

    @EventHandler(ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        String prefix = players.getMetaData(event.getPlayer()).getPrefix();
        if (prefix != null) {
            event.renderer(ChatRenderer.viewerUnaware((source, name, message) -> line(prefix, name, message)));
        }
    }

    // The prefixes are MiniMessage, as TAB reads them too. The rest is the line
    // Paper's own renderer gives.
    static Component line(String prefix, Component name, Component message) {
        return MiniMessage.miniMessage().deserialize(prefix)
                .append(Component.translatable("chat.type.text", name, message));
    }
}
