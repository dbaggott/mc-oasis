package com.oasissmp.voice;

import de.maxhenkel.voicechat.api.BukkitVoicechatService;
import de.maxhenkel.voicechat.api.Group;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.events.CreateGroupEvent;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.VoicechatServerStartedEvent;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Makes the server's one Simple Voice Chat group and refuses any other.
 *
 * <p>The group is open: its members hear each other wherever they are, and still
 * hear and are heard by anyone near them by proximity. It is persistent, so it
 * stays listed for players to join while it's empty.
 */
public final class OasisVoice extends JavaPlugin implements VoicechatPlugin {

    static final String GROUP_NAME = "Oasis";

    @Override
    public void onEnable() {
        BukkitVoicechatService service =
                getServer().getServicesManager().load(BukkitVoicechatService.class);
        if (service == null) {
            throw new IllegalStateException("Simple Voice Chat's service isn't registered");
        }
        service.registerPlugin(this);
    }

    @Override
    public String getPluginId() {
        return "oasis_voice";
    }

    @Override
    public void registerEvents(EventRegistration registration) {
        registration.registerEvent(VoicechatServerStartedEvent.class, this::createGroup);
        registration.registerEvent(CreateGroupEvent.class, this::refusePlayerGroup);
    }

    private void createGroup(VoicechatServerStartedEvent event) {
        event.getVoicechat().groupBuilder()
                .setName(GROUP_NAME)
                .setType(Group.Type.OPEN)
                .setPersistent(true)
                .build();
        getLogger().info("Created the open voice group " + GROUP_NAME);
    }

    // A group a player creates comes with their connection; this plugin's own
    // comes with none. Simple Voice Chat tells the player nothing when the
    // creation is cancelled, so this does.
    private void refusePlayerGroup(CreateGroupEvent event) {
        VoicechatConnection connection = event.getConnection();
        if (connection == null) {
            return;
        }
        event.cancel();
        Audience player = getServer().getPlayer(connection.getPlayer().getUuid());
        if (player != null) {
            player.sendMessage(Component.text(
                    "Voice chat has one group, " + GROUP_NAME + ". Join it from the group screen.",
                    NamedTextColor.YELLOW));
        }
    }
}
