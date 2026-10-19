package com.oasissmp.voice;

import de.maxhenkel.voicechat.api.Group;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.events.JoinGroupEvent;
import java.util.UUID;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.event.user.UserDataRecalculateEvent;
import org.bukkit.entity.Player;

/**
 * The voice group only players with {@value #PERMISSION} can join. Everyone
 * sees it listed. It's a normal group: its members hear the players near them,
 * who don't hear them.
 */
final class AdminGroup {

    static final String NAME = "Admin VC";
    static final String PERMISSION = "oasisvoice.admin-group";

    private final OasisVoice plugin;
    private final LuckPerms luckPerms;
    private final UUID id = UUID.randomUUID();
    private volatile VoicechatServerApi voicechat;

    AdminGroup(OasisVoice plugin, LuckPerms luckPerms) {
        this.plugin = plugin;
        this.luckPerms = luckPerms;
    }

    void enable() {
        // Subscribed as the plugin, so LuckPerms drops it when the plugin disables.
        luckPerms.getEventBus().subscribe(plugin, UserDataRecalculateEvent.class,
                event -> onRecalculate(event.getUser().getUniqueId()));
    }

    UUID id() {
        return id;
    }

    void create(VoicechatServerApi voicechat) {
        this.voicechat = voicechat;
        plugin.createGroup(voicechat, id, NAME, Group.Type.NORMAL);
    }

    // Simple Voice Chat checks a group's password before this, and there isn't one.
    void onJoin(JoinGroupEvent event) {
        if (!id.equals(event.getGroup().getId())) {
            return;
        }
        // Without a connection there's no player to check, so the join is refused.
        VoicechatConnection connection = event.getConnection();
        Player player = connection == null
                ? null : plugin.getServer().getPlayer(connection.getPlayer().getUuid());
        if (player != null && player.hasPermission(PERMISSION)) {
            return;
        }
        event.cancel();
        if (player != null) {
            ((Audience) player).sendMessage(
                    Component.text(NAME + " is for admins.", NamedTextColor.RED));
        }
    }

    // A member who loses the permission is taken out of the group. LuckPerms
    // recalculates off the main thread; the player is checked on it.
    private void onRecalculate(UUID playerId) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            Player player = plugin.getServer().getPlayer(playerId);
            if (player == null || voicechat == null || player.hasPermission(PERMISSION)) {
                return;
            }
            VoicechatConnection connection = voicechat.getConnectionOf(playerId);
            if (connection == null || connection.getGroup() == null
                    || !id.equals(connection.getGroup().getId())) {
                return;
            }
            connection.setGroup(null);
            ((Audience) player).sendMessage(Component.text(
                    "You've left " + NAME + ": it's for admins.", NamedTextColor.YELLOW));
        });
    }
}
