package io.th0rgal.oraxen.mechanics.provided.block;

import io.th0rgal.oraxen.OraxenPlugin;
import io.th0rgal.oraxen.items.OraxenItems;
import io.th0rgal.oraxen.mechanics.Mechanic;
import io.th0rgal.oraxen.mechanics.MechanicFactory;
import io.th0rgal.oraxen.mechanics.MechanicsManager;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import java.util.*;

public class BlockMechanicFactory extends MechanicFactory {

    public BlockMechanicFactory(ConfigurationSection section) {
        super(section);
        MechanicsManager.registerListeners(OraxenPlugin.get(), new BlockMechanicsManager(this));
    }

    @Override
    public Mechanic parse(ConfigurationSection itemMechanicConfiguration) {
        Mechanic mechanic = new BlockMechanic(this, itemMechanicConfiguration);
        addToImplemented(mechanic);
        return mechanic;
    }

}

class BlockMechanicsManager implements Listener {

    private MechanicFactory factory;

    public BlockMechanicsManager(BlockMechanicFactory factory) {
        this.factory = factory;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    private void onPlacingCustomBlock(PlayerInteractEvent event) {
        ItemStack item = event.getItem();
        String itemID = OraxenItems.getIdByItem(item);
        if (factory.isNotImplementedIn(itemID))
            return;

        Player player = event.getPlayer();
        Block placedAgainst = event.getClickedBlock();
        Block target;
        Material type = placedAgainst.getType();
        if (!type.equals(Material.SNOW)
                && !type.equals(Material.GRASS_BLOCK)
                && !type.equals(Material.VINE)
                && !type.equals(Material.TALL_GRASS))
            target = placedAgainst.getRelative(event.getBlockFace());
        else
            target = placedAgainst;

        BlockPlaceEvent blockBreakEvent = new BlockPlaceEvent(target, target.getState(), placedAgainst, item, player, true, event.getHand());
        Bukkit.getPluginManager().callEvent(blockBreakEvent);

        if (target.getLocation().distance(player.getLocation()) > 1 && target.getLocation().distance(player.getLocation()) > 1) {
            if (blockBreakEvent.canBuild() && !blockBreakEvent.isCancelled()) {

                event.setCancelled(true);
                target.setType(Material.BROWN_TERRACOTTA);
                if (!player.getGameMode().equals(GameMode.CREATIVE))
                    item.setAmount(item.getAmount() - 1);
            }
        }
    }

}

class BlockMechanic extends Mechanic {

    List<LinkedHashMap<String, Object>> loots;
    boolean defaultBreakAnimation;

    @SuppressWarnings("unchecked")
    public BlockMechanic(MechanicFactory mechanicFactory, ConfigurationSection section) {
        /* We give:
        - an instance of the Factory which created the mechanic
        - the section used to configure the mechanic
         */
        super(mechanicFactory, section);
        loots = (List<LinkedHashMap<String, Object>>) section.getList("loots");

        if (!section.isConfigurationSection("break_animation")) {
            defaultBreakAnimation = true;
        } else {
            ConfigurationSection breakAnimation = section.getConfigurationSection("break_animation");
            defaultBreakAnimation = !breakAnimation.isBoolean("default") || breakAnimation.getBoolean("default");
        }
    }

    public List<LinkedHashMap<String, Object>> getLoots() {
        return loots;
    }

    public boolean isDefaultBreakAnimation() {
        return defaultBreakAnimation;
    }
}