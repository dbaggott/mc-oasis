package com.oasissmp.filter;

import io.papermc.paper.event.player.AsyncChatEvent;
import io.papermc.paper.event.player.PlayerNameEntityEvent;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerEditBookEvent;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Stops blocked words, as {@link WordFilter} finds them, everywhere a player
 * can put text in front of others: chat, commands, signs, books, anvil renames,
 * name tags and their own name.
 *
 * <p>What it stops is refused, never censored and let through. Each refusal is
 * logged in full and shown to online players with {@value #NOTIFY_PERMISSION};
 * an anvil rename is only refused, since nothing is shown to anyone until the
 * renamed item is taken.
 */
public final class OasisFilter extends JavaPlugin implements Listener {

    static final String NOTIFY_PERMISSION = "oasisfilter.notify";

    private static final int NOTICE_TEXT_LIMIT = 256;
    private static final Component REFUSAL =
            Component.text("That has a word that isn't allowed here.", NamedTextColor.RED);

    private WordFilter filter;

    @Override
    public void onEnable() {
        try {
            filter = WordFilter.load(getDataFolder().toPath());
        } catch (IOException e) {
            throw new UncheckedIOException("Couldn't read the word lists", e);
        }
        getServer().getPluginManager().registerEvents(this, this);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        String text = event.signedMessage().message();
        if (filter.blocks(text)) {
            event.setCancelled(true);
            refuse(event.getPlayer(), "chat", text);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        String text = event.getMessage();
        if (filter.blocks(text)) {
            event.setCancelled(true);
            refuse(event.getPlayer(), "a command", text);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onSign(SignChangeEvent event) {
        String text = plainText(event.lines().stream());
        if (filter.blocks(text)) {
            event.setCancelled(true);
            refuse(event.getPlayer(), "a sign", text);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBook(PlayerEditBookEvent event) {
        BookMeta book = event.getNewBookMeta();
        String text = plainText(Stream.concat(Stream.ofNullable(book.title()), book.pages().stream()));
        if (filter.blocks(text)) {
            event.setCancelled(true);
            refuse(event.getPlayer(), "a book", text);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onNameTag(PlayerNameEntityEvent event) {
        Component name = event.getName();
        if (name == null) {
            return;
        }
        String text = plainText(Stream.of(name));
        if (filter.blocks(text)) {
            event.setCancelled(true);
            refuse(event.getPlayer(), "a name tag", text);
        }
    }

    // Fires again as each letter of the new name is typed, so the player is
    // told in the action bar, which each telling replaces.
    @EventHandler(priority = EventPriority.LOWEST)
    public void onAnvil(PrepareAnvilEvent event) {
        String text = event.getView().getRenameText();
        if (text != null && filter.blocks(text)) {
            event.setResult(null);
            event.getView().getPlayer().sendActionBar(REFUSAL);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onLogin(AsyncPlayerPreLoginEvent event) {
        String name = event.getName();
        if (filter.blocks(name)) {
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, Component.text(
                    "Your name has a word that isn't allowed here. Ask an owner for help."));
            report(name, "a login", name);
        }
    }

    private void refuse(Player player, String where, String text) {
        ((Audience) player).sendMessage(REFUSAL);
        report(player.getName(), where, text);
    }

    private void report(String playerName, String where, String text) {
        getLogger().info("Blocked " + where + " from " + playerName + ": " + text);
        String shown = text.length() > NOTICE_TEXT_LIMIT
                ? text.substring(0, NOTICE_TEXT_LIMIT) + "…"
                : text;
        Component notice = Component.text("[Filter] " + playerName + ", " + where + ": ",
                        NamedTextColor.GOLD)
                .append(Component.text(shown, NamedTextColor.GRAY));
        // Chat and logins arrive off the main thread.
        getServer().getScheduler().runTask(this, () -> {
            for (Player staff : getServer().getOnlinePlayers()) {
                if (staff.hasPermission(NOTIFY_PERMISSION)) {
                    ((Audience) staff).sendMessage(notice);
                }
            }
        });
    }

    // One line per component, so a word split across a sign's lines or a
    // book's pages is still found.
    private static String plainText(Stream<Component> components) {
        return components
                .map(PlainTextComponentSerializer.plainText()::serialize)
                .collect(Collectors.joining("\n"));
    }
}
