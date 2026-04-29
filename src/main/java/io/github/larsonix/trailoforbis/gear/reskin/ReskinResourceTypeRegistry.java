package io.github.larsonix.trailoforbis.gear.reskin;

import io.github.larsonix.trailoforbis.gear.loot.LootGenerator.EquipmentSlot;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Maps reskin groups to custom ResourceType IDs.
 *
 * <p>Two grouping strategies:
 * <ul>
 *   <li><b>Armor</b>: (slot, quality) — all Rare helmets share one ResourceType</li>
 *   <li><b>Weapons</b>: (slot, quality, category) — Rare daggers separate from Rare swords</li>
 * </ul>
 */
public final class ReskinResourceTypeRegistry {

    private static final String RESOURCE_TYPE_PREFIX = "RPG_Reskin_";

    private final Map<String, String> registry = new HashMap<>();

    /** Armor: register by (slot, quality). */
    @Nonnull
    public String register(@Nonnull EquipmentSlot slot, @Nonnull String qualityId) {
        String key = slot.name() + ":" + qualityId;
        return registry.computeIfAbsent(key,
                k -> RESOURCE_TYPE_PREFIX + slot.name() + "_" + qualityId);
    }

    /** Weapons: register by (slot, quality, category). */
    @Nonnull
    public String register(@Nonnull EquipmentSlot slot, @Nonnull String qualityId,
                           @Nonnull String category) {
        String key = slot.name() + ":" + qualityId + ":" + category;
        return registry.computeIfAbsent(key,
                k -> RESOURCE_TYPE_PREFIX + slot.name() + "_" + qualityId + "_" + category);
    }

    /** Armor lookup: (slot, quality). */
    @Nullable
    public String getResourceTypeId(@Nonnull EquipmentSlot slot, @Nonnull String qualityId) {
        return registry.get(slot.name() + ":" + qualityId);
    }

    /** Weapons lookup: (slot, quality, category). */
    @Nullable
    public String getResourceTypeId(@Nonnull EquipmentSlot slot, @Nonnull String qualityId,
                                    @Nonnull String category) {
        return registry.get(slot.name() + ":" + qualityId + ":" + category);
    }

    @Nonnull
    public Set<String> getAllResourceTypeIds() {
        return Collections.unmodifiableSet(new java.util.HashSet<>(registry.values()));
    }

    public int size() {
        return registry.size();
    }
}
