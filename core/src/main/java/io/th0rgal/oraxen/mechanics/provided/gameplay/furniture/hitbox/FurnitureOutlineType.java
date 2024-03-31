package io.th0rgal.oraxen.mechanics.provided.gameplay.furniture.hitbox;

import io.th0rgal.oraxen.config.Settings;
import io.th0rgal.oraxen.utils.logs.Logs;
import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;

public enum FurnitureOutlineType {
    ITEM, BLOCK;

    public static FurnitureOutlineType fromSetting() {
        try {
            return FurnitureOutlineType.valueOf(Settings.FURNITURE_OUTLINE_TYPE.toString().toUpperCase());
        } catch (IllegalArgumentException e) {
            Logs.logError("Invalid value for FURNITURE_OUTLINE_TYPE: " + Settings.FURNITURE_OUTLINE_TYPE.getValue());
            Logs.logWarning("Valid values are: " + StringUtils.join(Arrays.stream(FurnitureOutlineType.values()).map(FurnitureOutlineType::name), ","));
            Logs.logWarning("Defaulting to ITEM");
            return FurnitureOutlineType.ITEM;
        }
    }
}
