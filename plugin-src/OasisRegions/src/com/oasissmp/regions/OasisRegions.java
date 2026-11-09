package com.oasissmp.regions;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.flags.Flag;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.flags.StringFlag;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
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
        Map<Regions.Region, List<Consumer<ProtectedRegion>>> flags = new LinkedHashMap<>();
        for (Regions.Region region : regions.regions()) {
            List<Consumer<ProtectedRegion>> regionFlags = new ArrayList<>();
            region.flags().forEach((name, value) -> {
                StateFlag flag = stateFlag(name);
                StateFlag.State state = StateFlag.State.valueOf(value.toUpperCase(Locale.ROOT));
                regionFlags.add(protectedRegion -> protectedRegion.setFlag(flag, state));
            });
            region.textFlags().forEach((name, text) -> {
                StringFlag flag = stringFlag(name);
                regionFlags.add(protectedRegion -> protectedRegion.setFlag(flag, text));
            });
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
            Regions.Region region, List<Consumer<ProtectedRegion>> flags, ProtectedRegion existing) {
        ProtectedRegion protectedRegion = new ProtectedCuboidRegion(
                region.name(), vector(region.first()), vector(region.second()));
        if (existing != null) {
            protectedRegion.copyFrom(existing);
        }
        flags.forEach(flag -> flag.accept(protectedRegion));
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

    private static StringFlag stringFlag(String name) {
        Flag<?> flag = WorldGuard.getInstance().getFlagRegistry().get(name);
        if (!(flag instanceof StringFlag stringFlag)) {
            throw new IllegalArgumentException(Regions.FILE_NAME + " sets " + name
                    + " to text, which isn't a WorldGuard text flag");
        }
        return stringFlag;
    }

    private static BlockVector3 vector(Regions.Corner corner) {
        return BlockVector3.at(corner.x(), corner.y(), corner.z());
    }
}
