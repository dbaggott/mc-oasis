package com.oasissmp.voice;

import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;
import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.UUID;
import java.util.logging.Level;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import space.arim.libertybans.api.LibertyBans;
import space.arim.libertybans.api.NetworkAddress;
import space.arim.libertybans.api.PlayerVictim;
import space.arim.libertybans.api.PunishmentType;
import space.arim.libertybans.api.event.PostPardonEvent;
import space.arim.libertybans.api.event.PostPunishEvent;
import space.arim.libertybans.api.punish.Punishment;
import space.arim.omnibus.events.ListenerPriorities;
import space.arim.omnibus.events.RegisteredListener;

/**
 * A player muted in LibertyBans is muted in voice chat too: nobody hears them,
 * nearby or in the group. It ends when their mute does.
 */
final class VoiceMutes implements Listener {

    private final JavaPlugin plugin;
    private final LibertyBans libertyBans;
    private final Mutes mutes = new Mutes();
    private RegisteredListener punishListener;
    private RegisteredListener pardonListener;

    VoiceMutes(JavaPlugin plugin, LibertyBans libertyBans) {
        this.plugin = plugin;
        this.libertyBans = libertyBans;
    }

    void enable() {
        var eventBus = libertyBans.getOmnibus().getEventBus();
        punishListener = eventBus.registerListener(PostPunishEvent.class, ListenerPriorities.NORMAL,
                event -> onMute(event.getPunishment()));
        pardonListener = eventBus.registerListener(PostPardonEvent.class, ListenerPriorities.NORMAL,
                event -> onUnmute(event.getPunishment()));
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        plugin.getServer().getOnlinePlayers().forEach(this::loadMute);
    }

    void disable() {
        var eventBus = libertyBans.getOmnibus().getEventBus();
        if (punishListener != null) {
            eventBus.unregisterListener(punishListener);
        }
        if (pardonListener != null) {
            eventBus.unregisterListener(pardonListener);
        }
    }

    // Dropping a muted player's microphone packets silences them everywhere:
    // what others hear, nearby or in the group, is made from these.
    void onMicrophone(MicrophonePacketEvent event) {
        VoicechatConnection sender = event.getSenderConnection();
        if (sender == null) {
            return;
        }
        UUID playerId = sender.getPlayer().getUuid();
        if (!mutes.isMuted(playerId, Instant.now())) {
            return;
        }
        event.cancel();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        loadMute(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        mutes.unmute(event.getPlayer().getUniqueId());
    }

    private void loadMute(Player player) {
        // None only once their connection is closing, when there's nobody to silence.
        InetSocketAddress address = player.getAddress();
        if (address == null) {
            return;
        }
        libertyBans.getSelector()
                .getCachedMute(player.getUniqueId(), NetworkAddress.of(address.getAddress()))
                .whenComplete((mute, failure) -> {
                    if (failure != null) {
                        plugin.getLogger().log(Level.SEVERE, "Couldn't look up whether "
                                + player.getName() + " is muted, so they can speak in voice chat",
                                failure);
                        return;
                    }
                    mute.ifPresent(this::onMute);
                });
    }

    private void onMute(Punishment punishment) {
        if (punishment.getType() == PunishmentType.MUTE
                && punishment.getVictim() instanceof PlayerVictim victim) {
            mutes.mute(victim.getUUID(), punishment.getEndDate());
        }
    }

    private void onUnmute(Punishment punishment) {
        if (punishment.getType() == PunishmentType.MUTE
                && punishment.getVictim() instanceof PlayerVictim victim) {
            mutes.unmute(victim.getUUID());
        }
    }
}
