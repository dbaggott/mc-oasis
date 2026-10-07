package com.oasissmp.rules;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.io.IOException;
import java.io.UncheckedIOException;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * {@code /rules} shows anyone the server rules, read from {@code rules.txt} at
 * start. A player is shown them with a welcome on joining until they've seen
 * the rules as they are now: which rules each player last saw is saved with
 * them, as the rules' {@link Rules#fingerprint() fingerprint}.
 */
public final class OasisRules extends JavaPlugin implements Listener {

    // Long enough for the rules to land after the join messages, not among them.
    private static final long JOIN_DELAY_TICKS = 40;

    private Rules rules;
    private NamespacedKey seenKey;

    @Override
    public void onEnable() {
        try {
            rules = Rules.load(getDataFolder().toPath());
        } catch (IOException e) {
            throw new UncheckedIOException("Couldn't read " + Rules.FILE_NAME, e);
        }
        seenKey = new NamespacedKey(this, "rules_seen");
        getServer().getPluginManager().registerEvents(this, this);
        registerCommand("rules", "Show the server rules", new RulesCommand());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        String fingerprint = rules.fingerprint();
        if (fingerprint.equals(player.getPersistentDataContainer()
                .get(seenKey, PersistentDataType.STRING))) {
            return;
        }
        getServer().getScheduler().runTaskLater(this, () -> {
            if (!player.isOnline()) {
                return;
            }
            sendWelcome(player);
            // Saved only once sent, so a player who leaves first is shown them next time.
            player.getPersistentDataContainer().set(seenKey, PersistentDataType.STRING, fingerprint);
        }, JOIN_DELAY_TICKS);
    }

    // Messages go out through Audience: Player's and CommandSender's own
    // sendMessage overloads reach for BungeeCord's chat classes, which aren't here.
    private void sendWelcome(Audience audience) {
        audience.sendMessage(Component.text(
                "Welcome to The Oasis SMP!  Please make sure to follow the rules:",
                NamedTextColor.GOLD));
        audience.sendMessage(Component.empty());
        sendRules(audience);
        audience.sendMessage(Component.empty());
        audience.sendMessage(Component.text(
                "Type /rules any time to see these again.", NamedTextColor.GRAY));
    }

    private void sendRules(Audience audience) {
        rules.numbered().forEach(rule -> audience.sendMessage(Component.text(rule)));
    }

    private final class RulesCommand implements BasicCommand {

        @Override
        public void execute(CommandSourceStack source, String[] args) {
            Audience sender = source.getSender();
            sender.sendMessage(Component.text("The Oasis SMP rules:", NamedTextColor.GOLD));
            sendRules(sender);
        }
    }
}
