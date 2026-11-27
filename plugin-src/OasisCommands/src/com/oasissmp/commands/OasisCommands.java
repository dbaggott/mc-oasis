package com.oasissmp.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.CommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import java.util.List;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.PluginIdentifiableCommand;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * {@code /commands}, for everyone: the Oasis commands its sender can use, each
 * with its description ({@link Listing}). Clicking one types it into chat.
 *
 * <p>Whether a sender can use a command is asked of the command's node in the
 * dispatcher that ran {@code /commands}, which applies the requirement its
 * plugin gave it. A command map's permission check doesn't: a plugin's
 * Brigadier command there carries no permission of its own.
 */
public final class OasisCommands extends JavaPlugin {

    @Override
    public void onEnable() {
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event ->
                event.registrar().register(Commands.literal(Listing.NAME)
                        .executes(context -> list(context.getSource(), context.getRootNode()))
                        .build(), "List the Oasis commands you can use"));
    }

    private int list(CommandSourceStack source, CommandNode<CommandSourceStack> root) {
        List<Listing.Known> known = getServer().getCommandMap().getKnownCommands().entrySet().stream()
                .filter(entry -> entry.getValue() instanceof PluginIdentifiableCommand)
                .map(entry -> new Listing.Known(entry.getKey(), entry.getValue().getName(),
                        ((PluginIdentifiableCommand) entry.getValue()).getPlugin().getName(),
                        entry.getValue().getDescription()))
                .toList();
        List<Listing.Entry> entries = Listing.select(known, name -> {
            CommandNode<CommandSourceStack> node = root.getChild(name);
            return node != null && node.canUse(source);
        });
        TextComponent.Builder message = Component.text().append(Component.text("Commands:", NamedTextColor.GOLD));
        for (Listing.Entry entry : entries) {
            String command = "/" + entry.name();
            message.appendNewline()
                    .append(Component.text(command, NamedTextColor.AQUA)
                            .clickEvent(ClickEvent.suggestCommand(command + " "))
                            .hoverEvent(HoverEvent.showText(Component.text("Click to type " + command))))
                    .append(Component.text(" - " + entry.description(), NamedTextColor.GRAY));
        }
        Audience sender = source.getSender();
        sender.sendMessage(message.build());
        return Command.SINGLE_SUCCESS;
    }
}
