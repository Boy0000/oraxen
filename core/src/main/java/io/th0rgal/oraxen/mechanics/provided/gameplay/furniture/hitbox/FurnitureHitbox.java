package io.th0rgal.oraxen.mechanics.provided.gameplay.furniture.hitbox;

import io.th0rgal.oraxen.config.Settings;
import io.th0rgal.oraxen.items.ItemParser;
import io.th0rgal.oraxen.mechanics.provided.gameplay.furniture.FurnitureFactory;
import io.th0rgal.oraxen.mechanics.provided.gameplay.furniture.FurnitureMechanic;
import io.th0rgal.oraxen.mechanics.provided.gameplay.furniture.IFurniturePacketManager;
import net.minecraft.world.level.block.state.BlockState;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.craftbukkit.v1_20_R3.block.CraftBlockState;
import org.bukkit.craftbukkit.v1_20_R3.block.data.CraftBlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class FurnitureHitbox {
    public static final FurnitureHitbox EMPTY = new FurnitureHitbox(List.of(), List.of(), false);
    private final List<BarrierHitbox> barrierHitboxes;
    private final List<InteractionHitbox> interactionHitboxes;
    private final boolean outline;
    private static final ItemStack outlineItem = new ItemParser(Settings.FURNITURE_OUTLINE_ITEM.toConfigSection()).buildItem().build();
    private static final int outlineBlock = BlockData

    public FurnitureHitbox(@NotNull ConfigurationSection hitboxSection) {
        List<BarrierHitbox> barrierHitboxes = new ArrayList<>();
        for (String barrierString : hitboxSection.getStringList("barrierHitboxes"))
            barrierHitboxes.add(new BarrierHitbox(barrierString));

        List<InteractionHitbox> interactionHitboxes = new ArrayList<>();
        for (String interactionString : hitboxSection.getStringList("interactionHitboxes"))
            interactionHitboxes.add(new InteractionHitbox(interactionString));

        this.barrierHitboxes = barrierHitboxes;
        this.interactionHitboxes = interactionHitboxes;
        this.outline = hitboxSection.getBoolean("outline", Settings.FURNITURE_OUTLINE_HITBOX.toBool());
    }

    public FurnitureHitbox(Collection<BarrierHitbox> barrierHitboxes, Collection<InteractionHitbox> interactionHitboxes, boolean outlineHitboxes) {
        this.barrierHitboxes = new ArrayList<>(barrierHitboxes);
        this.interactionHitboxes = new ArrayList<>(interactionHitboxes);
        this.outline = outlineHitboxes;
    }

    public List<BarrierHitbox> barrierHitboxes() {
        return barrierHitboxes;
    }

    public List<InteractionHitbox> interactionHitboxes() {
        return interactionHitboxes;
    }

    public void handleHitboxes(Entity baseEntity, FurnitureMechanic mechanic) {
        IFurniturePacketManager packetManager = FurnitureFactory.instance.furniturePacketManager();

        for (Player player : baseEntity.getWorld().getNearbyPlayers(baseEntity.getLocation(), 32.0)) {
            packetManager.sendInteractionEntityPacket(baseEntity, mechanic, player);
            packetManager.sendBarrierHitboxPacket(baseEntity, mechanic, player);
        }
    }

    public List<Location> hitboxLocations(Location center, float rotation) {
        List<Location> hitboxLocations = new ArrayList<>();
        hitboxLocations.addAll(barrierHitboxLocations(center, rotation));
        hitboxLocations.addAll(interactionHitboxLocations(center, rotation));

        return hitboxLocations;
    }

    public List<Location> barrierHitboxLocations(Location center, float rotation) {
        return barrierHitboxes.stream().map(b -> b.groundRotate(rotation).add(center)).toList();
    }

    public List<Location> interactionHitboxLocations(Location center, float rotation) {
        return interactionHitboxes.stream().map(i -> center.add(i.offset(rotation))).toList();
    }

    public boolean outlineHitboxes() {
        return outline;
    }
    public ItemStack outlineItem() {
        return outlineItem;
    }

    public int outlineBlock() {
        return outlineBlock;
    }
}
