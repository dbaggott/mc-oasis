package com.oasissmp.staff;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.context.ContextCalculator;
import net.luckperms.api.context.ContextConsumer;
import net.luckperms.api.context.ContextSet;
import net.luckperms.api.context.ImmutableContextSet;
import net.luckperms.api.event.user.UserDataRecalculateEvent;
import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * {@code /staff onduty} and {@code /staff offduty}, for players with
 * {@value #DUTY_PERMISSION}. Duty only decides whether a player's group prefix
 * shows: their permissions are the same either way.
 *
 * <p>Every player is on duty unless they've gone off it. LuckPerms gets that as
 * the {@value #CONTEXT} context, {@code on} or {@code off}, and the staff
 * groups' prefixes apply only where it's {@code on}. Going off duty is saved
 * with the player, so it lasts through leaving and restarts.
 */
public final class OasisStaff extends JavaPlugin implements Listener {

    static final String DUTY_PERMISSION = "oasisstaff.duty";
    static final String CONTEXT = "staff-duty";

    private NamespacedKey offDutyKey;
    private LuckPerms luckPerms;
    private final DutyContext dutyContext = new DutyContext();
    // Online players who are off duty. LuckPerms reads it off the main thread,
    // where a player's saved data can't be.
    private final Set<UUID> offDuty = ConcurrentHashMap.newKeySet();

    @Override
    public void onEnable() {
        offDutyKey = new NamespacedKey(this, "off_duty");
        luckPerms = getServer().getServicesManager().load(LuckPerms.class);
        if (luckPerms == null) {
            throw new IllegalStateException("LuckPerms' API isn't registered");
        }
        luckPerms.getContextManager().registerCalculator(dutyContext);
        // Subscribed as this plugin, so LuckPerms drops it when the plugin disables.
        luckPerms.getEventBus().subscribe(this, UserDataRecalculateEvent.class,
                event -> onRecalculate(event.getUser().getUniqueId()));
        getServer().getPluginManager().registerEvents(this, this);
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event ->
                event.registrar().register(staffCommand(),
                        "Go on or off duty: off duty hides your staff prefix"));
        getServer().getOnlinePlayers().forEach(this::loadDuty);
    }

    @Override
    public void onDisable() {
        if (luckPerms != null) {
            luckPerms.getContextManager().unregisterCalculator(dutyContext);
        }
        offDuty.clear();
    }

    // Before other plugins see the player, so nothing shows them on duty when
    // they're not.
    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        loadDuty(event.getPlayer());
    }

    // A player who's lost the permission while off duty is put back on: they'd
    // have no way to do it themselves.
    private void loadDuty(Player player) {
        if (!player.getPersistentDataContainer().has(offDutyKey)) {
            return;
        }
        if (!player.hasPermission(DUTY_PERMISSION)) {
            player.getPersistentDataContainer().remove(offDutyKey);
            return;
        }
        offDuty.add(player.getUniqueId());
        luckPerms.getContextManager().signalContextUpdate(player);
    }

    // The same for a player who loses the permission while online. LuckPerms
    // recalculates off the main thread; the player is checked on it.
    private void onRecalculate(UUID playerId) {
        if (!offDuty.contains(playerId)) {
            return;
        }
        getServer().getScheduler().runTask(this, () -> {
            Player player = getServer().getPlayer(playerId);
            if (player != null && offDuty.contains(playerId)
                    && !player.hasPermission(DUTY_PERMISSION)) {
                setOnDuty(player, true);
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        offDuty.remove(event.getPlayer().getUniqueId());
    }

    private void setOnDuty(Player player, boolean onDuty) {
        if (onDuty) {
            player.getPersistentDataContainer().remove(offDutyKey);
            offDuty.remove(player.getUniqueId());
        } else {
            player.getPersistentDataContainer().set(offDutyKey, PersistentDataType.BOOLEAN, true);
            offDuty.add(player.getUniqueId());
        }
        luckPerms.getContextManager().signalContextUpdate(player);
    }

    private final class DutyContext implements ContextCalculator<Player> {

        @Override
        public void calculate(Player player, ContextConsumer consumer) {
            consumer.accept(CONTEXT, offDuty.contains(player.getUniqueId()) ? "off" : "on");
        }

        @Override
        public ContextSet estimatePotentialContexts() {
            return ImmutableContextSet.builder().add(CONTEXT, "on").add(CONTEXT, "off").build();
        }
    }

    // Built from fixed words rather than free text, so the game itself answers
    // a missing or wrong one, in its own words.
    private LiteralCommandNode<CommandSourceStack> staffCommand() {
        return Commands.literal("staff")
                // Players need the permission even to see the command. The console
                // doesn't, so it's told why it can't use it rather than that it's unknown.
                .requires(source -> !(source.getSender() instanceof Player)
                        || source.getSender().hasPermission(DUTY_PERMISSION))
                .then(Commands.literal("onduty").executes(context -> setDuty(context.getSource(), true)))
                .then(Commands.literal("offduty").executes(context -> setDuty(context.getSource(), false)))
                .build();
    }

    private int setDuty(CommandSourceStack source, boolean onDuty) {
        // Messages go to it as an Audience: CommandSender's own sendMessage
        // overloads reach for BungeeCord's chat classes, which aren't here.
        Audience sender = source.getSender();
        if (!(source.getSender() instanceof Player player)) {
            sender.sendMessage(Component.text(
                    "Only a player can go on or off duty.", NamedTextColor.RED));
            return 0;
        }
        setOnDuty(player, onDuty);
        sender.sendMessage(Component.text(
                onDuty ? "Staff prefix shown." : "Staff prefix hidden.", NamedTextColor.GREEN));
        return Command.SINGLE_SUCCESS;
    }
}
