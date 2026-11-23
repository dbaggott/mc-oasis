package com.oasissmp.grimfluid;

import ac.grim.grimac.shaded.com.github.retrooper.packetevents.PacketEvents;
import ac.grim.grimac.shaded.com.github.retrooper.packetevents.PacketEventsAPI;
import ac.grim.grimac.shaded.com.github.retrooper.packetevents.injector.ChannelInjector;
import ac.grim.grimac.shaded.com.github.retrooper.packetevents.manager.player.PlayerManager;
import ac.grim.grimac.shaded.com.github.retrooper.packetevents.manager.protocol.ProtocolManager;
import ac.grim.grimac.shaded.com.github.retrooper.packetevents.manager.server.ServerManager;
import ac.grim.grimac.shaded.com.github.retrooper.packetevents.manager.server.ServerVersion;
import ac.grim.grimac.shaded.com.github.retrooper.packetevents.netty.NettyManager;
import ac.grim.grimac.shaded.com.github.retrooper.packetevents.protocol.player.ClientVersion;
import ac.grim.grimac.shaded.com.github.retrooper.packetevents.protocol.world.chunk.impl.v_1_18.Chunk_v1_18;
import ac.grim.grimac.shaded.com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState;
import ac.grim.grimac.shaded.com.github.retrooper.packetevents.protocol.world.states.type.StateType;
import ac.grim.grimac.shaded.com.github.retrooper.packetevents.protocol.world.states.type.StateTypes;

/**
 * Runs against the patched GrimAC jar, on the classpath in place of the
 * original: a 26.1+ chunk section keeps counting its water as the water
 * becomes each block that always holds it, and stops when it becomes air. It
 * is packetevents' own ChunkFluidCountTest, on Grim's copy of packetevents.
 */
public final class FluidCountTest {

    public static void main(String[] args) {
        // Block mappings load through the API's settings and server version;
        // nothing else of a running server is needed.
        PacketEvents.setAPI(new MappingsOnlyApi());
        ClientVersion version = ClientVersion.V_26_1;
        Chunk_v1_18 section = new Chunk_v1_18(version);
        // A new section's palette is empty, so palette id 0 is set to air first.
        section.set(0, 0, 0, WrappedBlockState.getDefaultState(version, StateTypes.AIR));

        section.set(0, 0, 0, WrappedBlockState.getDefaultState(version, StateTypes.WATER));
        expectFluidCount(section, 1, "water");
        for (StateType type : new StateType[] {StateTypes.BUBBLE_COLUMN, StateTypes.KELP,
                StateTypes.KELP_PLANT, StateTypes.SEAGRASS, StateTypes.TALL_SEAGRASS}) {
            section.set(0, 0, 0, WrappedBlockState.getDefaultState(version, type));
            expectFluidCount(section, 1, type.getName());
        }
        section.set(0, 0, 0, WrappedBlockState.getDefaultState(version, StateTypes.AIR));
        expectFluidCount(section, 0, "air");
        System.out.println("FluidCountTest passed");
    }

    private static void expectFluidCount(Chunk_v1_18 section, int expected, String block) {
        if (section.getFluidCount() != expected) {
            throw new AssertionError("fluid count " + section.getFluidCount() + " after " + block
                    + ", expected " + expected);
        }
    }

    private static final class MappingsOnlyApi extends PacketEventsAPI<Object> {
        @Override public boolean isLoaded() { return true; }
        @Override public void init() {}
        @Override public boolean isInitialized() { return true; }
        @Override public boolean isTerminated() { return false; }
        @Override public Object getPlugin() { return null; }
        @Override public ServerManager getServerManager() { return ServerVersion::getLatest; }
        @Override public ProtocolManager getProtocolManager() { return null; }
        @Override public PlayerManager getPlayerManager() { return null; }
        @Override public NettyManager getNettyManager() { return null; }
        @Override public ChannelInjector getInjector() { return null; }
    }
}
