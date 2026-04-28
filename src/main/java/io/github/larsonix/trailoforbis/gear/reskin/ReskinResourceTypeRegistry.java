package io.github.larsonix.trailoforbis.gear.reskin;

import io.github.larsonix.trailoforbis.gear.loot.LootGenerator.EquipmentSlot;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Maps (slot, quality, category) groups to custom ResourceType IDs for the reskin system.
 *
 * <p>Each group represents a set of items that can be reskinned between each other
 * at the Builder's Workbench. The ResourceType ID is added to RPG items during
 * custom item registration so they match the reskin recipes.
 *
 * <p>Only groups with 2+ items get a ResourceType (no point reskinning if there's
 * only one option).
 */
public final class ReskinResourceTypeRegistry {

    private static final String RESOURCE_TYPE_PREFIX = "RPG_Reskin_";

    /** Maps "SLOT:quality:CATEGORY" → ResourceType ID */
    private final Map<String, String> registry = new HashMap<>();

    /**
     * Registers a ResourceType for a (slot, quality, category) group.
     *
     * @param slot      Equipment slot
     * @param qualityId Vanilla quality ID (e.g., "Common", "Epic")
     * @param category  Category name (e.g., "SWORD", "PLATE")
     * @return The ResourceType ID that was registered
     */
    @Nonnull
    public String register(@Nonnull EquipmentSlot slot, @Nonnull String qualityId,
                           @Nonnull String category) {
        String resourceTypeId = RESOURCE_TYPE_PREFIX + slot.name() + "_" + qualityId + "_" + category;
        registry.put(buildKey(slot, qualityId, category), resourceTypeId);
        return resourceTypeId;
    }

    /**
     * Looks up the reskin ResourceType ID for a given item's group.
     *
     * @param slot      Equipment slot
     * @param qualityId Vanilla quality ID
     * @param category  Category name
     * @return The ResourceType ID, or null if no reskin group exists for this combination
     */
    @Nullable
    public String getResourceTypeId(@Nonnull EquipmentSlot slot, @Nonnull String qualityId,
                                    @Nonnull String category) {
        return registry.get(buildKey(slot, qualityId, category));
    }

    /**
     * Gets all registered ResourceType IDs.
     *
     * @return Unmodifiable set of all ResourceType IDs
     */
    @Nonnull
    public Set<String> getAllResourceTypeIds() {
        return Collections.unmodifiableSet(new java.util.HashSet<>(registry.values()));
    }

    /**
     * Gets the total number of registered reskin groups.
     */
    public int size() {
        return registry.size();
    }

    private static String buildKey(EquipmentSlot slot, String qualityId, String category) {
        return slot.name() + ":" + qualityId + ":" + category;
    }
}
