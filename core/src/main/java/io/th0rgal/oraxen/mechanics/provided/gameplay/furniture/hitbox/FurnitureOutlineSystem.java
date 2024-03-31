package io.th0rgal.oraxen.mechanics.provided.gameplay.furniture.hitbox;

import io.th0rgal.oraxen.api.OraxenFurniture;
import io.th0rgal.oraxen.mechanics.provided.gameplay.furniture.FurnitureFactory;
import io.th0rgal.oraxen.mechanics.provided.gameplay.furniture.FurnitureMechanic;
import io.th0rgal.oraxen.mechanics.provided.gameplay.furniture.IFurniturePacketManager;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

public class FurnitureOutlineSystem extends BukkitRunnable {
    @Override
    public void run() {
        for (World world : Bukkit.getWorlds()) {
            for (Player player : world.getPlayers()) {
                IFurniturePacketManager packetManager = FurnitureFactory.instance.furniturePacketManager();
                Entity baseEntity = packetManager.getTargetFurnitureHitbox(player, 5);
                FurnitureMechanic mechanic = OraxenFurniture.getFurnitureMechanic(baseEntity);
                if (baseEntity == null || mechanic == null) packetManager.removeHitboxOutlinePacket(player);
                else packetManager.sendHitboxOutlinePacket(baseEntity, mechanic, player);
            }
        }
    }
}
