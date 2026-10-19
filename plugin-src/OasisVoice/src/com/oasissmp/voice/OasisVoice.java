package com.oasissmp.voice;

import de.maxhenkel.voicechat.api.BukkitVoicechatService;
import de.maxhenkel.voicechat.api.Group;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.events.CreateGroupEvent;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.JoinGroupEvent;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;
import de.maxhenkel.voicechat.api.events.VoicechatServerStartedEvent;
import java.util.UUID;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.luckperms.api.LuckPerms;
import org.bukkit.plugin.java.JavaPlugin;
import space.arim.libertybans.api.LibertyBans;
import space.arim.omnibus.OmnibusProvider;

/**
 * Makes the server's Simple Voice Chat groups and refuses any other, and keeps
 * players muted in LibertyBans silent in voice chat.
 *
 * <p>{@value #GROUP_NAME} is open to everyone: its members hear each other
 * wherever they are, and still hear and are heard by anyone near them by
 * proximity. {@link AdminGroup} is the admins'. Both are persistent, so they
 * stay listed for players to join while they're empty.
 */
public final class OasisVoice extends JavaPlugin implements VoicechatPlugin {

    static final String GROUP_NAME = "Group VC";

    private final UUID groupId = UUID.randomUUID();
    private VoiceMutes voiceMutes;
    private AdminGroup adminGroup;

    @Override
    public void onEnable() {
        LibertyBans libertyBans = OmnibusProvider.getOmnibus().getRegistry()
                .getProvider(LibertyBans.class)
                .orElseThrow(() -> new IllegalStateException("LibertyBans' API isn't registered"));
        voiceMutes = new VoiceMutes(this, libertyBans);
        voiceMutes.enable();
        LuckPerms luckPerms = getServer().getServicesManager().load(LuckPerms.class);
        if (luckPerms == null) {
            throw new IllegalStateException("LuckPerms' API isn't registered");
        }
        adminGroup = new AdminGroup(this, luckPerms);
        adminGroup.enable();
        BukkitVoicechatService service =
                getServer().getServicesManager().load(BukkitVoicechatService.class);
        if (service == null) {
            throw new IllegalStateException("Simple Voice Chat's service isn't registered");
        }
        service.registerPlugin(this);
    }

    @Override
    public void onDisable() {
        if (voiceMutes != null) {
            voiceMutes.disable();
        }
    }

    @Override
    public String getPluginId() {
        return "oasis_voice";
    }

    @Override
    public void registerEvents(EventRegistration registration) {
        registration.registerEvent(VoicechatServerStartedEvent.class, this::createGroups);
        registration.registerEvent(CreateGroupEvent.class, this::refuseOtherGroups);
        registration.registerEvent(JoinGroupEvent.class, adminGroup::onJoin);
        registration.registerEvent(MicrophonePacketEvent.class, voiceMutes::onMicrophone);
    }

    private void createGroups(VoicechatServerStartedEvent event) {
        createGroup(event.getVoicechat(), groupId, GROUP_NAME, Group.Type.OPEN);
        adminGroup.create(event.getVoicechat());
    }

    void createGroup(VoicechatServerApi voicechat, UUID id, String name, Group.Type type) {
        voicechat.groupBuilder()
                .setId(id)
                .setName(name)
                .setType(type)
                .setPersistent(true)
                .build();
        // build() adds the group only if the voice server is running and no
        // listener cancels it, and says nothing either way.
        if (voicechat.getGroup(id) == null) {
            getLogger().severe("The voice group " + name + " wasn't created");
            return;
        }
        getLogger().info("Created the voice group " + name);
    }

    // Simple Voice Chat tells a player nothing when their group is refused, so
    // this does. A group made by another plugin comes with no connection.
    private void refuseOtherGroups(CreateGroupEvent event) {
        UUID id = event.getGroup().getId();
        if (groupId.equals(id) || adminGroup.id().equals(id)) {
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
                    "Voice chat has its own groups. Join " + GROUP_NAME + " from the group screen.",
                    NamedTextColor.YELLOW));
        }
    }
}
