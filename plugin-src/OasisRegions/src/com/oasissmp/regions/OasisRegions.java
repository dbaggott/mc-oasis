package com.oasissmp.regions;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.flags.Flag;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Sets up the WorldGuard regions in {@code regions.txt} in the overworld at
 * every start. A region that already exists gets the file's corners and flags
 * and keeps its owners, members and any other flags, as {@code /rg redefine}
 * and {@code /rg flag} would leave it. Done through WorldGuard's API rather
 * than those commands: they add a region in the background, so a flag set
 * right after can miss it.
 */
public final class OasisRegions extends JavaPlugin {

    private static final String WORLD = "world";

    @Override
    public void onEnable() {
        Regions regions;
        try {
            regions = Regions.load(getDataFolder().toPath());
        } catch (IOException e) {
            throw new UncheckedIOException("Couldn't read " + Regions.FILE_NAME, e);
        }
        World world = getServer().getWorld(WORLD);
        if (world == null) {
            throw new IllegalStateException("There's no world named " + WORLD);
        }
        RegionManager manager = WorldGuard.getInstance().getPlatform().getRegionContainer()
                .get(BukkitAdapter.adapt(world));
        if (manager == null) {
            throw new IllegalStateException("WorldGuard has no regions for " + WORLD);
        }
        // Every flag looked up before any region is added, so a flag WorldGuard
        // doesn't have stops the plugin with none of the file applied rather than some.
        Map<Regions.Region, Map<StateFlag, StateFlag.State>> flags = new LinkedHashMap<>();
        for (Regions.Region region : regions.regions()) {
            Map<StateFlag, StateFlag.State> regionFlags = new LinkedHashMap<>();
            region.flags().forEach((name, value) ->
                    regionFlags.put(stateFlag(name), StateFlag.State.valueOf(value.toUpperCase(Locale.ROOT))));
            flags.put(region, regionFlags);
        }
        // Each region built only once those before it are added: adding a
        // region repoints the regions in the index whose parent it replaces,
        // and one built earlier would copy the old parent, which adding it
        // would put back.
        flags.forEach((region, regionFlags) ->
                manager.addRegion(protectedRegion(region, regionFlags, manager.getRegion(region.name()))));
        getLogger().info("Set up the regions " + String.join(", ",
                regions.regions().stream().map(Regions.Region::name).toList()));
    }

    private static ProtectedRegion protectedRegion(
            Regions.Region region, Map<StateFlag, StateFlag.State> flags, ProtectedRegion existing) {
        ProtectedRegion protectedRegion = new ProtectedCuboidRegion(
                region.name(), vector(region.first()), vector(region.second()));
        if (existing != null) {
            protectedRegion.copyFrom(existing);
        }
        flags.forEach(protectedRegion::setFlag);
        return protectedRegion;
    }

    private static StateFlag stateFlag(String name) {
        Flag<?> flag = WorldGuard.getInstance().getFlagRegistry().get(name);
        if (!(flag instanceof StateFlag stateFlag)) {
            throw new IllegalArgumentException(Regions.FILE_NAME + " sets " + name
                    + ", which isn't a WorldGuard allow/deny flag");
        }
        return stateFlag;
    }

    private static BlockVector3 vector(Regions.Corner corner) {
        return BlockVector3.at(corner.x(), corner.y(), corner.z());
    }
}
