package io.th0rgal.oraxen.nms.v1_20_R3;

import com.ticxo.modelengine.api.ModelEngineAPI;
import com.ticxo.modelengine.api.generator.blueprint.ModelBlueprint;
import io.papermc.paper.math.BlockPosition;
import io.papermc.paper.math.Position;
import io.th0rgal.oraxen.OraxenPlugin;
import io.th0rgal.oraxen.api.OraxenFurniture;
import io.th0rgal.oraxen.mechanics.MechanicsManager;
import io.th0rgal.oraxen.mechanics.provided.gameplay.furniture.FurnitureMechanic;
import io.th0rgal.oraxen.mechanics.provided.gameplay.furniture.FurnitureSubEntity;
import io.th0rgal.oraxen.mechanics.provided.gameplay.furniture.IFurniturePacketManager;
import io.th0rgal.oraxen.mechanics.provided.gameplay.furniture.hitbox.FurnitureOutlineType;
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
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.bukkit.Location;
import org.bukkit.block.data.BlockData;
import org.bukkit.craftbukkit.v1_20_R3.entity.CraftPlayer;
import org.bukkit.craftbukkit.v1_20_R3.inventory.CraftItemStack;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
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
    private final int ITEM_DISPLAY_SCALE = 12;
    private final int ITEM_DISPLAY_ITEMSTACK = 23;
    private final int ITEM_DISPLAY_TRANSFORM = 24;
    private final Map<UUID, Set<FurnitureSubEntityPacket>> interactionHitboxPacketMap = new HashMap<>();
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

            Set<FurnitureSubEntityPacket> packets = new HashSet<>();
            for (int i = 0; i < interactionHitboxes.size(); i++) {
                InteractionHitbox hitbox = interactionHitboxes.get(i);
                int entityId = entityIds.get(i);

                Location loc = baseLoc.clone().add(hitbox.offset(baseEntity.getYaw()));
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

                packets.add(new FurnitureSubEntityPacket(entityId, addEntityPacket, metadataPacket));
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

                Location loc = hitbox.location(baseEntity);
                ClientboundAddEntityPacket addEntityPacket = new ClientboundAddEntityPacket(
                        entityId, UUID.randomUUID(),
                        loc.x(), loc.y(), loc.z(), 0, loc.getYaw(),
                        FurnitureOutlineType.fromSetting() == FurnitureOutlineType.BLOCK ? EntityType.BLOCK_DISPLAY : EntityType.ITEM_DISPLAY,
                        0, Vec3.ZERO, 0.0
                );

                ClientboundSetEntityDataPacket metadataPacket = new ClientboundSetEntityDataPacket(
                        entityId, Arrays.asList(
                        new SynchedEntityData.DataValue<>(12, EntityDataSerializers.VECTOR3, new Vector3f(hitbox.width(), hitbox.height(), hitbox.width())),
                        new SynchedEntityData.DataValue<>(23, EntityDataSerializers.INT, mechanic.hitbox().outlineItem()),
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
                .map(c -> c.groundRotate(baseEntity.getYaw()).add(baseEntity.getLocation()))
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
                .map(c -> c.groundRotate(baseEntity.getYaw()).add(baseEntity.getLocation()))
                .collect(Collectors.toMap(Position::block, l -> AIR_DATA));
        player.sendMultiBlockChange(positions);
    }

    @Override @Nullable
    public Entity getTargetFurnitureHitbox(Player player, double maxDistance) {
        if (maxDistance < 1 || maxDistance > 120) return null;
        CraftPlayer craftPlayer = (CraftPlayer) player;
        net.minecraft.world.entity.player.Player nmsPlayer = craftPlayer.getHandle();
        Vec3 start = nmsPlayer.getEyePosition(1.0F);
        Vec3 direction = nmsPlayer.getLookAngle();
        Vec3 distanceDirection = new Vec3(direction.x * maxDistance, direction.y * maxDistance, direction.z * maxDistance);
        Vec3 end = start.add(distanceDirection);
        List<net.minecraft.world.entity.Entity> entities = nmsPlayer.level().getEntities(nmsPlayer, nmsPlayer.getBoundingBox().expandTowards(distanceDirection).inflate(1.0,1.0,1.0), EntitySelector.NO_SPECTATORS);
        double distance = 0.0;
        Iterator<net.minecraft.world.entity.Entity> entityIterator = entities.iterator();

        Entity baseEntity = null;
        while (true) {
            net.minecraft.world.entity.Entity entity;
            Vec3 rayTrace;
            double distanceTo;
            do {
                Optional<Vec3> rayTraceResult = Optional.empty();
                do {
                    if (!entityIterator.hasNext()) return baseEntity;

                    entity = entityIterator.next();
                    Entity bukkitEntity = entity.getBukkitEntity();
                    FurnitureMechanic mechanic = OraxenFurniture.getFurnitureMechanic(bukkitEntity);
                    // If entity is furniture, check all interactionHitboxes if their "bounding box" is colliding
                    if (mechanic != null) for (InteractionHitbox hitbox : mechanic.hitbox().interactionHitboxes()) {
                        Location hitboxLoc = hitbox.location(bukkitEntity);
                        Vec3 hitboxVec = new Vec3(hitboxLoc.x(), hitboxLoc.y(), hitboxLoc.z());
                        AABB hitboxAABB = AABB.ofSize(hitboxVec, hitbox.width(), hitbox.height(), hitbox.width());
                        rayTraceResult = hitboxAABB.clip(start, end);
                        if (rayTraceResult.isPresent()) break;
                    }
                } while (rayTraceResult.isEmpty());

                rayTrace = rayTraceResult.get();
                distanceTo = start.distanceToSqr(rayTrace);
            } while(!(distanceTo < distance) && distance != 0.0);

            baseEntity = entity.getBukkitEntity();
            distance = distanceTo;
        }
    }
}
