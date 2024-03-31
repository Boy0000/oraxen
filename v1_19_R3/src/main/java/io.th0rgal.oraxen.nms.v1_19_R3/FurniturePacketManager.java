package io.th0rgal.oraxen.nms.v1_19_R3;

import com.ticxo.modelengine.api.ModelEngineAPI;
import com.ticxo.modelengine.api.generator.blueprint.ModelBlueprint;
import io.papermc.paper.math.BlockPosition;
import io.papermc.paper.math.Position;
import io.th0rgal.oraxen.OraxenPlugin;
import io.th0rgal.oraxen.mechanics.MechanicsManager;
import io.th0rgal.oraxen.mechanics.provided.gameplay.furniture.FurnitureMechanic;
import io.th0rgal.oraxen.mechanics.provided.gameplay.furniture.FurnitureSubEntity;
import io.th0rgal.oraxen.mechanics.provided.gameplay.furniture.IFurniturePacketManager;
import io.th0rgal.oraxen.mechanics.provided.gameplay.furniture.hitbox.InteractionHitbox;
import io.th0rgal.oraxen.utils.BlockHelpers;
import io.th0rgal.oraxen.utils.VersionUtil;
import io.th0rgal.oraxen.utils.logs.Logs;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.craftbukkit.v1_19_R3.entity.CraftPlayer;
import org.bukkit.craftbukkit.v1_19_R3.inventory.CraftItemStack;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3f;

import java.util.*;
import java.util.stream.Collectors;

public class FurniturePacketManager implements IFurniturePacketManager {

    public FurniturePacketManager() {
        if (VersionUtil.isPaperServer()) MechanicsManager.registerListeners(OraxenPlugin.get(), "furniture", new FurniturePacketListener());
        else {
            Logs.logWarning("Seems that your server is a Spigot-server");
            Logs.logWarning("FurnitureHitboxes will not work due to it relying on Paper-only events");
            Logs.logWarning("It is heavily recommended to make the upgrade to Paper");
        }
    }

    private final int INTERACTION_WIDTH_ID = 8;
    private final int INTERACTION_HEIGHT_ID = 9;
    private final Map<UUID, Set<FurnitureInteractionHitboxPacket>> interactionHitboxPacketMap = new HashMap<>();
    private final Map<UUID, Set<FurnitureSubEntityPacket>> outlineHitboxPacketMap = new HashMap<>();
    @Override
    public void sendInteractionEntityPacket(@NotNull Entity baseEntity, @NotNull FurnitureMechanic mechanic, @NotNull Player player) {
        List<InteractionHitbox> interactionHitboxes = mechanic.hitbox().interactionHitboxes();
        if (interactionHitboxes.isEmpty()) return;
        if (mechanic.isModelEngine()) {
            ModelBlueprint blueprint = ModelEngineAPI.getBlueprint(mechanic.getModelEngineID());
            if (blueprint != null && blueprint.getMainHitbox() != null) return;
        }

        Location baseLoc = BlockHelpers.toCenterBlockLocation(baseEntity.getLocation());
        interactionHitboxPacketMap.computeIfAbsent(baseEntity.getUniqueId(), key -> {
            List<Integer> entityIds = interactionHitboxIdMap.stream()
                    .filter(ids -> ids.baseUUID().equals(baseEntity.getUniqueId()))
                    .findFirst()
                    .map(FurnitureSubEntity::entityIds)
                    .orElseGet(() -> {
                        List<Integer> newEntityIds = new ArrayList<>(interactionHitboxes.size());
                        while (newEntityIds.size() < interactionHitboxes.size())
                            newEntityIds.add(net.minecraft.world.entity.Entity.nextEntityId());

                        FurnitureSubEntity subEntity = new FurnitureSubEntity(baseEntity.getUniqueId(), newEntityIds);
                        interactionHitboxIdMap.add(subEntity);
                        return subEntity.entityIds();
                    });

            Set<FurnitureInteractionHitboxPacket> packets = new HashSet<>();
            for (int i = 0; i < interactionHitboxes.size(); i++) {
                InteractionHitbox hitbox = interactionHitboxes.get(i);
                int entityId = entityIds.get(i);

                Location loc = baseLoc.clone().add(hitbox.offset(baseEntity.getLocation().getYaw()));
                ClientboundAddEntityPacket addEntityPacket = new ClientboundAddEntityPacket(
                        entityId, UUID.randomUUID(),
                        loc.x(), loc.y(), loc.z(), loc.getPitch(), loc.getYaw(),
                        EntityType.INTERACTION, 0, Vec3.ZERO, 0.0
                );

                ClientboundSetEntityDataPacket metadataPacket = new ClientboundSetEntityDataPacket(
                        entityId, Arrays.asList(
                        new SynchedEntityData.DataValue<>(INTERACTION_WIDTH_ID, EntityDataSerializers.FLOAT, hitbox.width()),
                        new SynchedEntityData.DataValue<>(INTERACTION_HEIGHT_ID, EntityDataSerializers.FLOAT, hitbox.height())
                ));

                packets.add(new FurnitureInteractionHitboxPacket(entityId, addEntityPacket, metadataPacket));
            }
            return packets;
        }).forEach(packets -> {
            ((CraftPlayer) player).getHandle().connection.send(packets.addEntity);
            ((CraftPlayer) player).getHandle().connection.send(packets.metadata);
        });

    }

    @Override
    public void removeInteractionHitboxPacket(@NotNull Entity baseEntity, @NotNull FurnitureMechanic mechanic) {
        for (Player player : baseEntity.getWorld().getPlayers()) {
            removeInteractionHitboxPacket(baseEntity, mechanic, player);
        }
        interactionHitboxIdMap.removeIf(id -> id.baseUUID().equals(baseEntity.getUniqueId()));
        interactionHitboxPacketMap.remove(baseEntity.getUniqueId());
    }

    @Override
    public void removeInteractionHitboxPacket(@NotNull Entity baseEntity, @NotNull FurnitureMechanic mechanic, @NotNull Player player) {
        IntList entityIds = interactionHitboxIdMap.stream().filter(id -> id.baseUUID().equals(baseEntity.getUniqueId())).findFirst().map(FurnitureSubEntity::entityIds).orElse(IntList.of());
        ((CraftPlayer) player).getHandle().connection.send(new ClientboundRemoveEntitiesPacket(entityIds.toIntArray()));
    }

    @Override
    public void sendHitboxOutlinePacket(@NotNull Entity baseEntity, @NotNull FurnitureMechanic mechanic, @NotNull Player player) {
        List<InteractionHitbox> interactionHitboxes = mechanic.hitbox().interactionHitboxes();
        if (hitboxOutlinePlayerMap.get(player.getUniqueId()) == baseEntity.getUniqueId()) return;
        removeHitboxOutlinePacket(player);
        hitboxOutlinePlayerMap.put(player.getUniqueId(), baseEntity.getUniqueId());

        if (interactionHitboxes.isEmpty()) return;
        Location baseLocation = BlockHelpers.toCenterBlockLocation(baseEntity.getLocation());

//        IntList entityIds = hitboxOutlineIdMap.stream().filter(o -> o.baseUUID().equals(baseEntity.getUniqueId())).findFirst().orElseGet(() -> {
//            List<Integer> outlineIds = new ArrayList<>(interactionHitboxes.size());
//            while (outlineIds.size() < interactionHitboxes.size()) {
//                outlineIds.add(net.minecraft.world.entity.Entity.nextEntityId());
//            }
//            return new FurnitureSubEntity(baseEntity.getUniqueId(), outlineIds);
//        }).entityIds();

        outlineHitboxPacketMap.computeIfAbsent(baseEntity.getUniqueId(), key -> {
            List<Integer> entityIds = hitboxOutlineIdMap.stream()
                    .filter(o -> o.baseUUID().equals(baseEntity.getUniqueId()))
                    .findFirst()
                    .map(FurnitureSubEntity::entityIds)
                    .orElseGet(() -> {
                        List<Integer> outlineIds = new ArrayList<>(interactionHitboxes.size());
                        while (outlineIds.size() < interactionHitboxes.size())
                            outlineIds.add(net.minecraft.world.entity.Entity.nextEntityId());

                        FurnitureSubEntity subEntity = new FurnitureSubEntity(baseEntity.getUniqueId(), outlineIds);
                        hitboxOutlineIdMap.add(subEntity);
                        return subEntity.entityIds();
                    });

            ItemDisplay.ItemDisplayTransform transform = mechanic.hasDisplayEntityProperties() ? mechanic.displayEntityProperties().getDisplayTransform() : ItemDisplay.ItemDisplayTransform.FIXED;
            Set<FurnitureSubEntityPacket> packets = new HashSet<>();
            for (int i = 0; i < interactionHitboxes.size(); i++) {
                InteractionHitbox hitbox = interactionHitboxes.get(i);
                int entityId = entityIds.get(i);

                Location loc = baseLocation.clone().add(hitbox.offset(baseEntity.getLocation().getYaw())).add(0,hitbox.height() / 2, 0);
                ClientboundAddEntityPacket addEntityPacket = new ClientboundAddEntityPacket(
                        entityId, UUID.randomUUID(),
                        loc.x(), loc.y(), loc.z(), loc.getPitch(), loc.getYaw(),
                        EntityType.ITEM_DISPLAY, 0, Vec3.ZERO, 0.0
                );

                ClientboundSetEntityDataPacket metadataPacket = new ClientboundSetEntityDataPacket(
                        entityId, Arrays.asList(
                        new SynchedEntityData.DataValue<>(12, EntityDataSerializers.VECTOR3, new Vector3f(hitbox.width(), hitbox.height(), hitbox.width())),
                        new SynchedEntityData.DataValue<>(23, EntityDataSerializers.ITEM_STACK, CraftItemStack.asNMSCopy(new ItemStack(Material.GLASS))),
                        new SynchedEntityData.DataValue<>(24, EntityDataSerializers.INT, transform.ordinal())
                ));

                packets.add(new FurnitureSubEntityPacket(entityId, addEntityPacket, metadataPacket));
            }
            return packets;
        }).forEach(packets -> {
            ((CraftPlayer) player).getHandle().connection.send(packets.addEntity);
            ((CraftPlayer) player).getHandle().connection.send(packets.metadata);
        });
    }

    @Override
    public void removeHitboxOutlinePacket(@NotNull Entity baseEntity, @NotNull FurnitureMechanic mechanic) {
        for (Player player : baseEntity.getWorld().getPlayers()) {
            removeHitboxOutlinePacket(baseEntity, mechanic, player);
        }
    }

    @Override
    public void removeHitboxOutlinePacket(@NotNull Entity baseEntity, @NotNull FurnitureMechanic mechanic, @NotNull Player player) {
        hitboxOutlineIdMap.stream().filter(o -> o.baseUUID().equals(baseEntity.getUniqueId())).findFirst().ifPresent(outline -> {
            ((CraftPlayer) player).getHandle().connection.send(new ClientboundRemoveEntitiesPacket(outline.entityIds()));
            hitboxOutlineIdMap.removeIf(o -> o.baseUUID().equals(baseEntity.getUniqueId()));
            hitboxOutlinePlayerMap.remove(player.getUniqueId());
        });
    }

    @Override
    public void removeHitboxOutlinePacket(@NotNull Player player) {
        hitboxOutlinePlayerMap.entrySet().stream().filter(o -> o.getKey().equals(player.getUniqueId())).findFirst().ifPresent(entry -> {
            hitboxOutlineIdMap.stream().filter(o -> o.baseUUID().equals(entry.getValue())).findFirst().ifPresent(outline -> {
                ((CraftPlayer) player).getHandle().connection.send(new ClientboundRemoveEntitiesPacket(outline.entityIds()));
                hitboxOutlinePlayerMap.remove(entry.getKey(), entry.getValue());
            });
        });
    }

    @Override
    public void sendBarrierHitboxPacket(@NotNull Entity baseEntity, @NotNull FurnitureMechanic mechanic, @NotNull Player player) {
        Map<Position, BlockData> positions = mechanic.hitbox().barrierHitboxes().stream()
                .map(c -> c.groundRotate(baseEntity.getLocation().getYaw()).add(baseEntity.getLocation()))
                .collect(Collectors.toMap(Position::block, l -> BARRIER_DATA));
        player.sendMultiBlockChange(positions);

        for (BlockPosition position : positions.keySet().stream().map(Position::toBlock).toList()) {
            barrierHitboxPositionMap.compute(baseEntity.getUniqueId(), (d, blockPos) -> {
                Set<com.comphenix.protocol.wrappers.BlockPosition> newBlockPos = new HashSet<>();
                com.comphenix.protocol.wrappers.BlockPosition newPos = new com.comphenix.protocol.wrappers.BlockPosition(position.blockX(), position.blockY(), position.blockZ());
                newBlockPos.add(newPos);
                if (blockPos != null) newBlockPos.addAll(blockPos);
                return newBlockPos;
            });
        }
    }

    @Override
    public void removeBarrierHitboxPacket(@NotNull Entity baseEntity, @NotNull FurnitureMechanic mechanic) {
        for (Player player : baseEntity.getWorld().getPlayers()) {
            removeBarrierHitboxPacket(baseEntity, mechanic, player);
        }
        barrierHitboxPositionMap.remove(baseEntity.getUniqueId());
    }

    @Override
    public void removeBarrierHitboxPacket(@NotNull Entity baseEntity, @NotNull FurnitureMechanic mechanic, @NotNull Player player) {
        Map<Position, BlockData> positions = mechanic.hitbox().barrierHitboxes().stream()
                .map(c -> c.groundRotate(baseEntity.getLocation().getYaw()).add(baseEntity.getLocation()))
                .collect(Collectors.toMap(Position::block, l -> AIR_DATA));
        player.sendMultiBlockChange(positions);
    }
}
