package com.oasissmp.filter;

import io.papermc.paper.event.player.AsyncChatEvent;
import io.papermc.paper.event.player.PlayerNameEntityEvent;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerEditBookEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Stops blocked words, as {@link WordFilter} finds them, everywhere a player
 * can put text in front of others: chat, commands, signs, books, anvil renames
 * and name tags. Players' names, approved with the allowlist, are neither
 * checked nor found in the text around them.
 *
 * <p>What it stops is refused, never censored and let through. Each refusal is
 * logged in full and shown to online players with {@value #NOTIFY_PERMISSION};
 * an anvil rename is only refused, since nothing is shown to anyone until the
 * renamed item is taken.
 */
public final class OasisFilter extends JavaPlugin implements Listener {

    static final String NOTIFY_PERMISSION = "oasisfilter.notify";

    private static final int NOTICE_TEXT_LIMIT = 256;
    private static final Component SHORT_REFUSAL =
            Component.text("That has a word that isn't allowed here.", NamedTextColor.RED);
    private static final Component MESSAGE_REFUSAL = Component.text(
            "Your message wasn't sent. Some words can hurt or put people down, or aren't right"
                    + " for the younger players here, and this looks like it might be one of them."
                    + " We want everyone to be and feel welcome here.",
            NamedTextColor.RED);

    private WordFilter filter;
    // Replaced, never changed, as players join: chat reads it off the main thread.
    private volatile PlayerNames playerNames;

    @Override
    public void onEnable() {
        try {
            filter = WordFilter.load(getDataFolder().toPath());
        } catch (IOException e) {
            throw new UncheckedIOException("Couldn't read the word lists", e);
        }
        playerNames = new PlayerNames(getServer().getWhitelistedPlayers().stream()
                .map(OfflinePlayer::getName)
                .filter(Objects::nonNull)
                .toList());
        getServer().getPluginManager().registerEvents(this, this);
    }

    // The allowlist is read at start; this adds anyone allowlisted in game since.
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        playerNames = playerNames.with(event.getPlayer().getName());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        String text = plainText(Stream.of(event.message()));
        if (blocks(text)) {
            event.setCancelled(true);
            refuse(event.getPlayer(), MESSAGE_REFUSAL, "chat", text);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        String text = event.getMessage();
        if (blocks(text)) {
            event.setCancelled(true);
            refuse(event.getPlayer(),
                    MessageCommands.sends(text) ? MESSAGE_REFUSAL : SHORT_REFUSAL, "a command", text);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSign(SignChangeEvent event) {
        String text = plainText(event.lines().stream());
        if (blocksAcrossLines(text)) {
            event.setCancelled(true);
            refuse(event.getPlayer(), SHORT_REFUSAL, "a sign", text);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBook(PlayerEditBookEvent event) {
        BookMeta book = event.getNewBookMeta();
        String text = plainText(Stream.concat(Stream.ofNullable(book.title()), book.pages().stream()));
        if (blocksAcrossLines(text)) {
            event.setCancelled(true);
            refuse(event.getPlayer(), SHORT_REFUSAL, "a book", text);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onNameTag(PlayerNameEntityEvent event) {
        Component name = event.getName();
        if (name == null) {
            return;
        }
        String text = plainText(Stream.of(name));
        if (blocks(text)) {
            event.setCancelled(true);
            refuse(event.getPlayer(), SHORT_REFUSAL, "a name tag", text);
        }
    }

    // Fires again as each letter of the new name is typed, so the player is
    // told in the action bar, which each telling replaces.
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onAnvil(PrepareAnvilEvent event) {
        String text = event.getView().getRenameText();
        if (text != null && blocks(text)) {
            event.setResult(null);
            event.getView().getPlayer().sendActionBar(SHORT_REFUSAL);
        }
    }

    private void refuse(Player player, Component refusal, String where, String text) {
        ((Audience) player).sendMessage(refusal);
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
        // Chat arrives off the main thread.
        getServer().getScheduler().runTask(this, () -> {
            for (Player staff : getServer().getOnlinePlayers()) {
                if (staff.hasPermission(NOTIFY_PERMISSION)) {
                    ((Audience) staff).sendMessage(notice);
                }
            }
        });
    }

    private boolean blocks(String text) {
        return filter.blocks(playerNames.removeFrom(text));
    }

    // A sign's lines and a book's pages are read apart and then run together,
    // for a word split across two of them.
    private boolean blocksAcrossLines(String text) {
        return blocks(text) || blocks(text.replace("\n", ""));
    }

    // One line per component.
    private static String plainText(Stream<Component> components) {
        return components
                .map(PlainTextComponentSerializer.plainText()::serialize)
                .collect(Collectors.joining("\n"));
    }
}
