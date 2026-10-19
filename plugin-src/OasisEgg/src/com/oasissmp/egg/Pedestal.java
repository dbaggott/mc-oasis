package com.oasissmp.egg;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import java.util.Map;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * The block at spawn where a player leaves the dragon egg for someone else to
 * take: the WorldGuard region {@value #REGION}, which lets anyone place a
 * block there. Only the egg can be placed, and right-clicking it puts it in
 * the clicker's inventory instead of teleporting it.
 */
final class Pedestal implements Listener {

    static final String REGION = "egg-pedestal";

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (event.getBlockPlaced().getType() != Material.DRAGON_EGG && isPedestal(event.getBlockPlaced())) {
            event.setCancelled(true);
            refusePlacing(event.getPlayer());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        if (isPedestal(event.getBlock())) {
            event.setCancelled(true);
            refusePlacing(event.getPlayer());
        }
    }

    // A click on the egg, either button, teleports it as this event.
    @EventHandler(ignoreCancelled = true)
    public void onTeleport(BlockFromToEvent event) {
        if (event.getBlock().getType() == Material.DRAGON_EGG && isPedestal(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    // Fired once per hand; only the main hand's takes the egg.
    @EventHandler(ignoreCancelled = true)
    public void onRightClick(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND
                || block == null || block.getType() != Material.DRAGON_EGG || !isPedestal(block)) {
            return;
        }
        event.setUseInteractedBlock(Event.Result.DENY);
        event.setUseItemInHand(Event.Result.DENY);
        Map<Integer, ItemStack> left = event.getPlayer().getInventory().addItem(new ItemStack(Material.DRAGON_EGG));
        if (!left.isEmpty()) {
            ((Audience) event.getPlayer()).sendMessage(
                    Component.text("Your inventory is full.", NamedTextColor.RED));
            return;
        }
        block.setType(Material.AIR);
    }

    // Looked up each time: the region is made by a startup command, after
    // this plugin enables.
    private static boolean isPedestal(Block block) {
        RegionManager regions = WorldGuard.getInstance().getPlatform().getRegionContainer()
                .get(BukkitAdapter.adapt(block.getWorld()));
        ProtectedRegion region = regions == null ? null : regions.getRegion(REGION);
        return region != null && region.contains(block.getX(), block.getY(), block.getZ());
    }

    private static void refusePlacing(Player player) {
        ((Audience) player).sendMessage(
                Component.text("Only the dragon egg can be placed here.", NamedTextColor.RED));
    }
}
