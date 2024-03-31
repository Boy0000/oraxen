package io.th0rgal.oraxen.mechanics.provided.gameplay.furniture.hitbox;

import io.th0rgal.oraxen.api.OraxenFurniture;
import io.th0rgal.oraxen.mechanics.provided.gameplay.furniture.FurnitureFactory;
import io.th0rgal.oraxen.mechanics.provided.gameplay.furniture.FurnitureMechanic;
import io.th0rgal.oraxen.utils.BlockHelpers;
import io.th0rgal.oraxen.utils.logs.Logs;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.List;

public class FurnitureOutlineSystem extends BukkitRunnable {
    @Override
    public void run() {
        world:
        for (World world : Bukkit.getWorlds()) {
            player:
            for (Player player : world.getPlayers()) {
                Location location = player.getEyeLocation();
                Vector direction = location.getDirection().clone().multiply(0.1);
                RayTraceResult result = player.rayTraceBlocks(5.0);
                double distanceEyeToRaycastBlock = result != null && result.getHitBlock() != null ? location.distance(result.getHitBlock().getLocation()) : (5.0 * 5.0);

                while (BlockHelpers.toBlockLocation(location).distanceSquared(player.getEyeLocation()) < distanceEyeToRaycastBlock) {
                    List<Entity> entities = location.getNearbyEntities(5.0, 5.0, 5.0).stream().toList();
                    entity:
                    for (Entity baseEntity : entities) {
                        FurnitureMechanic mechanic = OraxenFurniture.getFurnitureMechanic(baseEntity);
                        if (mechanic == null || !mechanic.hitbox().outlineHitboxes()) continue;

                        hitbox:
                        for (InteractionHitbox hitbox : mechanic.hitbox().interactionHitboxes()) {
                            BoundingBox hitboxBounding = BoundingBox.of(baseEntity.getLocation().add(hitbox.offset(baseEntity.getYaw())), hitbox.width() / 2, hitbox.height() / 2, hitbox.width() / 2);
                            BoundingBox rayBounding = BoundingBox.of(location, 0.1, 1.0, 0.1);
                            if (hitboxBounding.overlaps(rayBounding)) {
                                Logs.logError(hitboxBounding.toString());
                                Logs.logWarning(rayBounding.toString());
                                FurnitureFactory.instance.furniturePacketManager().sendHitboxOutlinePacket(baseEntity, mechanic, player);
                                break player;
                            }
                        }
                    }
                    location.add(direction);
                }
                FurnitureFactory.instance.furniturePacketManager().removeHitboxOutlinePacket(player);
            }
        }
    }
}
