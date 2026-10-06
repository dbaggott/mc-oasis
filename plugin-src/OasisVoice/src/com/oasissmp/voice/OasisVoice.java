package com.oasissmp.voice;

import de.maxhenkel.voicechat.api.BukkitVoicechatService;
import de.maxhenkel.voicechat.api.Group;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.events.CreateGroupEvent;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.VoicechatServerStartedEvent;
import java.util.UUID;
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

    private final UUID groupId = UUID.randomUUID();

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
        registration.registerEvent(CreateGroupEvent.class, this::refuseOtherGroups);
    }

    private void createGroup(VoicechatServerStartedEvent event) {
        event.getVoicechat().groupBuilder()
                .setId(groupId)
                .setName(GROUP_NAME)
                .setType(Group.Type.OPEN)
                .setPersistent(true)
                .build();
        // build() adds the group only if the voice server is running and no
        // listener cancels it, and says nothing either way.
        if (event.getVoicechat().getGroup(groupId) == null) {
            getLogger().severe("The open voice group " + GROUP_NAME + " wasn't created");
            return;
        }
        getLogger().info("Created the open voice group " + GROUP_NAME);
    }

    // Simple Voice Chat tells a player nothing when their group is refused, so
    // this does. A group made by another plugin comes with no connection.
    private void refuseOtherGroups(CreateGroupEvent event) {
        if (groupId.equals(event.getGroup().getId())) {
            return;
        }
        event.cancel();
        VoicechatConnection connection = event.getConnection();
        if (connection == null) {
            return;
        }
        Audience player = getServer().getPlayer(connection.getPlayer().getUuid());
        if (player != null) {
            player.sendMessage(Component.text(
                    "Voice chat has one group, " + GROUP_NAME + ". Join it from the group screen.",
                    NamedTextColor.YELLOW));
        }
    }
}
