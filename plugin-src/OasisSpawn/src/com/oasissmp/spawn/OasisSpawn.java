package com.oasissmp.spawn;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.session.ClipboardHolder;
import com.sk89q.worldedit.util.SideEffect;
import com.sk89q.worldedit.util.SideEffectSet;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Pastes {@value #SCHEMATIC}, the spawn built on the live server, into a newly
 * generated overworld whose seed hashes to {@value #SEED_HASH}, once per
 * world: {@link SpawnDecision} says when. A marker in the world's folder
 * records that the world was decided, so edits made in game afterwards stick.
 * The seed itself is never in the image, and neither it nor its hash is logged.
 */
public final class OasisSpawn extends JavaPlugin {

    private static final String WORLD = "world";
    private static final String SCHEMATIC = "spawn.schem";
    private static final String SEED_HASH = "spawn.seed-sha256";
    private static final String MARKER = "oasis-spawn-pasted";

    /**
     * Lighting, and registering points of interest (beds, bells, job sites,
     * portals), which changes no block; {@code none()} also keeps sending
     * the changes to players, as every set does. Neighbor and shape updates stay off,
     * so every block is set in exactly the state captured, the pedestal's
     * debug-stick states included, rather than reconnected or broken.
     */
    static final SideEffectSet PASTE_SIDE_EFFECTS = SideEffectSet.none()
            .with(SideEffect.LIGHTING, SideEffect.State.ON)
            .with(SideEffect.POI_UPDATE, SideEffect.State.ON);

    @Override
    public void onEnable() {
        World world = getServer().getWorld(WORLD);
        if (world == null) {
            throw new IllegalStateException("There's no world named " + WORLD);
        }
        Path marker = world.getWorldFolder().toPath().resolve(MARKER);
        String spawnSeedHash;
        try {
            spawnSeedHash = SpawnDecision.parseSeedHash(
                    Files.readString(getDataFolder().toPath().resolve(SEED_HASH)));
        } catch (IOException e) {
            throw new UncheckedIOException("Couldn't read " + SEED_HASH, e);
        }
        long gameTime = world.getGameTime();
        SpawnDecision decision = SpawnDecision.decide(
                Files.exists(marker), gameTime, world.getSeed(), spawnSeedHash);
        if (decision == SpawnDecision.ALREADY_DECIDED) {
            getLogger().info("Spawn was already pasted or skipped in this world");
            return;
        }
        if (decision == SpawnDecision.PASTE) {
            paste(world);
        }
        try {
            Files.writeString(marker, decision.name() + "\n");
        } catch (IOException e) {
            throw new UncheckedIOException("Couldn't write " + marker, e);
        }
        getLogger().info(switch (decision) {
            case NOT_NEW -> "Not pasting spawn: the world isn't new (game time " + gameTime + ")";
            case OTHER_SEED -> "Not pasting spawn: the world's seed isn't the one " + SCHEMATIC + " was captured in";
            case PASTE -> "Pasted spawn from " + SCHEMATIC;
            case ALREADY_DECIDED -> throw new IllegalStateException("Handled above");
        });
    }

    /** At the coordinates it was captured from: the clipboard keeps its origin. */
    private void paste(World world) {
        Path schematic = getDataFolder().toPath().resolve(SCHEMATIC);
        ClipboardFormat format = ClipboardFormats.findByPath(schematic);
        if (format == null) {
            throw new IllegalStateException(SCHEMATIC + " isn't a schematic WorldEdit can read");
        }
        Clipboard clipboard;
        try (InputStream in = Files.newInputStream(schematic); ClipboardReader reader = format.getReader(in)) {
            clipboard = reader.read();
        } catch (IOException e) {
            throw new UncheckedIOException("Couldn't read " + SCHEMATIC, e);
        }
        try (EditSession session = WorldEdit.getInstance().newEditSessionBuilder()
                .world(BukkitAdapter.adapt(world)).maxBlocks(-1).build()) {
            session.setSideEffectApplier(PASTE_SIDE_EFFECTS);
            Operations.complete(new ClipboardHolder(clipboard).createPaste(session)
                    .to(clipboard.getOrigin())
                    .ignoreAirBlocks(false)
                    .copyEntities(true)
                    .copyBiomes(true)
                    .build());
        } catch (WorldEditException e) {
            throw new IllegalStateException("Couldn't paste " + SCHEMATIC, e);
        }
    }
}
