package com.oasissmp.end;

import com.mojang.brigadier.Command;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.logging.Level;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.PortalType;
import org.bukkit.World;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.RemoteConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPortalEnterEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * {@code /end enable} and {@code /end disable}, for owners. While the End is
 * disabled nothing goes through an end portal into it: players, and every
 * entity that can use a portal, ender pearls, TNT and minecarts among them.
 * Leaving the End through its exit portal, and the gateways between its
 * islands, still work, so no one is shut in. It starts disabled
 * ({@link EndState}).
 *
 * <p>Paper asks with {@link EntityPortalEnterEvent} each tick an entity is
 * in an end portal block, before marking it as in the portal; a cancelled one
 * is never marked, so nothing is teleported.
 */
public final class OasisEnd extends JavaPlugin implements Listener {

    static final String PERMISSION = "oasisend.toggle";

    private static final Component DISABLED_NOTICE = Component.text("The End is disabled.", NamedTextColor.RED);

    private Path stateFile;
    private boolean enabled;

    @Override
    public void onEnable() {
        stateFile = getDataFolder().toPath().resolve(EndState.FILE_NAME);
        try {
            enabled = EndState.load(stateFile, line -> getLogger().warning(stateFile
                    + " says neither enabled nor disabled, so the End is disabled: " + line));
        } catch (IOException e) {
            throw new UncheckedIOException("Couldn't read " + stateFile, e);
        }
        getLogger().info("The End is " + describe(enabled));
        getServer().getPluginManager().registerEvents(this, this);
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event ->
                event.registrar().register(Commands.literal("end")
                        // The console and RCON have it without the permission, which ops don't get by default.
                        .requires(source -> source.getSender() instanceof ConsoleCommandSender
                                || source.getSender() instanceof RemoteConsoleCommandSender
                                || source.getSender().hasPermission(PERMISSION))
                        .executes(context -> report(context.getSource()))
                        .then(Commands.literal("enable").executes(context -> set(context.getSource(), true)))
                        .then(Commands.literal("disable").executes(context -> set(context.getSource(), false)))
                        .build(), "Whether the End is enabled, or enable or disable it"));
    }

    @EventHandler(ignoreCancelled = true)
    public void onEnterPortal(EntityPortalEnterEvent event) {
        if (enabled || event.getPortalType() != PortalType.ENDER
                || event.getLocation().getWorld().getEnvironment() == World.Environment.THE_END) {
            return;
        }
        event.setCancelled(true);
        if (event.getEntity() instanceof Player player) {
            ((Audience) player).sendActionBar(DISABLED_NOTICE);
        }
    }

    private int report(CommandSourceStack source) {
        ((Audience) source.getSender()).sendMessage(Component.text("The End is " + describe(enabled) + ".",
                NamedTextColor.LIGHT_PURPLE));
        return Command.SINGLE_SUCCESS;
    }

    // Takes effect at once, and is saved.
    private int set(CommandSourceStack source, boolean enable) {
        Audience sender = source.getSender();
        enabled = enable;
        String done = "The End is " + describe(enable) + ".";
        getLogger().info(source.getSender().getName() + ": " + done);
        try {
            EndState.save(stateFile, enable);
            sender.sendMessage(Component.text(done, NamedTextColor.LIGHT_PURPLE));
        } catch (IOException e) {
            getLogger().log(Level.SEVERE, "Couldn't save " + stateFile + "; the change lasts until a restart", e);
            sender.sendMessage(Component.text(done + " It couldn't be saved, so it lasts until a restart; see the"
                    + " server log.", NamedTextColor.RED));
        }
        return Command.SINGLE_SUCCESS;
    }

    private static String describe(boolean enabled) {
        return enabled ? "enabled" : "disabled";
    }
}
