package io.github.larsonix.trailoforbis.maps.core;

import com.hypixel.hytale.codec.codecs.EnumCodec;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Random;
import java.util.Set;

/**
 * Defines all realm biome types with their visual theme, template prefix, and terrain materials.
 *
 * <p>Each biome maps to a WorldGen V2 environment ({@code Realm_*}) and carries:
 * <ul>
 *   <li>A display name for UI</li>
 *   <li>A template prefix for loading biome-specific assets and prefabs</li>
 *   <li>A theme color for HUD/map tinting</li>
 *   <li>Terrain materials that the biome's WorldGen uses (for structure placement validation)</li>
 * </ul>
 *
 * <p>Terrain materials must match {@code generate-realm-biomes.py} output — if a biome's
 * WorldGen uses materials not listed here, structure placement may fail validation.
 *
 * @see RealmLayoutSize
 * @see io.github.larsonix.trailoforbis.maps.config.RealmsConfig
 */
public enum RealmBiomeType {

    // ═══════════════════════════════════════════════════════════════════
    // BIOME DEFINITIONS
    // ═══════════════════════════════════════════════════════════════════

    // Standard combat biomes
    FOREST("Forest", "Realm_Forest", 0x228B22, "Realm_Map_Forest",
            Set.of("Soil_Grass_Full", "Soil_Dirt", "Rock_Stone")),
    DESERT("Desert", "Realm_Desert", 15583663, "Realm_Map_Desert",
            Set.of("Soil_Sand_White", "Soil_Sand_Red", "Rock_Sandstone")),
    VOLCANO("Volcano", "Realm_Volcano", 0x8B0000, "Realm_Map_Volcano",
            Set.of("Rock_Volcanic", "Rock_Basalt")),
    TUNDRA("Tundra", "Realm_Tundra", 0xE0FFFF, "Realm_Map_Tundra",
            Set.of("Soil_Snow", "Rock_Ice", "Rock_Stone")),
    SWAMP("Swamp", "Realm_Swamp", 0x2F4F4F, "Realm_Map_Swamp",
            Set.of("Soil_Mud", "Soil_Dirt", "Soil_Grass", "Rock_Stone")),
    MOUNTAINS("Mountains", "Realm_Mountains", 7372944, null,
            Set.of("Rock_Stone", "Rock_Basalt", "Rock_Slate")),
    BEACH("Beach", "Realm_Beach", 16445670, "Realm_Map_Beach",
            Set.of("Soil_Sand_White", "Soil_Sand_Red", "Rock_Sandstone", "Rock_Stone")),
    JUNGLE("Jungle", "Realm_Jungle", 25600, null,
            Set.of("Soil_Grass_Full", "Soil_Mud", "Rock_Stone")),

    // Underground / themed biomes
    CAVERNS("Caverns", "Realm_Caverns", 3556687, "Realm_Map_Caverns",
            Set.of("Rock_Stone", "Rock_Shale", "Rock_Slate", "Rock_Basalt")),
    FROZEN_CRYPTS("Frozen Crypts", "Realm_Frozen_Crypts", 0x4488CC, "Realm_Map_Frozen_Crypts",
            Set.of("Rock_Ice", "Rock_Stone")),
    SAND_TOMBS("Sand Tombs", "Realm_Sand_Tombs", 12886112, "Realm_Map_Sand_Tombs",
            Set.of("Rock_Sandstone", "Rock_Stone")),

    // High-difficulty biomes
    VOID("Void", "Realm_Void", 1710638, null,
            Set.of("Rock_Stone", "Rock_Basalt")),
    CORRUPTED("Corrupted", "Realm_Corrupted", 4915330, null,
            Set.of("Rock_Volcanic", "Rock_Basalt")),

    // Non-combat / utility
    SKILL_SANCTUM("Skill Sanctum", "realm_skill_sanctum", 0x9966FF, null,
            Set.of("Soil_Grass_Full", "Soil_Dirt", "Rock_Stone"));

    // ═══════════════════════════════════════════════════════════════════
    // FIELDS
    // ═══════════════════════════════════════════════════════════════════

    /** Default map item ID used when no biome-specific 3D prop exists. */
    public static final String DEFAULT_MAP_ITEM_ID = "Realm_Map";

    private final String displayName;
    private final String templatePrefix;
    private final int themeColor;
    private final String mapItemId;
    private final Set<String> terrainMaterials;

    /** Arena radius tiers for template name resolution (e.g., realm_forest_r50) */
    public static final int[] RADIUS_TIERS = {35, 40, 45, 50, 55, 60, 70, 80, 90, 100, 110, 120, 130, 140, 150};

    public static final EnumCodec<RealmBiomeType> CODEC = new EnumCodec<>(RealmBiomeType.class);

    // ═══════════════════════════════════════════════════════════════════
    // CONSTRUCTOR
    // ═══════════════════════════════════════════════════════════════════

    RealmBiomeType(String displayName, @Nonnull String templatePrefix, int themeColor,
                   @Nullable String mapItemId, Set<String> terrainMaterials) {
        this.displayName = displayName;
        this.templatePrefix = templatePrefix;
        this.themeColor = themeColor;
        this.mapItemId = mapItemId;
        this.terrainMaterials = terrainMaterials;
    }

    // ═══════════════════════════════════════════════════════════════════
    // ACCESSORS
    // ═══════════════════════════════════════════════════════════════════

    @Nonnull
    public String getDisplayName() { return displayName; }

    @Nonnull
    public String getTemplatePrefix() { return templatePrefix; }

    public int getThemeColor() { return themeColor; }

    @Nonnull
    public Set<String> getTerrainMaterials() { return terrainMaterials; }

    /**
     * Gets the base item ID for map items of this biome.
     *
     * <p>Returns the biome-specific item ID (e.g., "Realm_Map_Forest") if a
     * 3D prop exists for this biome, otherwise returns the default blank map.
     *
     * @return The map item ID (never null)
     */
    @Nonnull
    public String getMapItemId() {
        return mapItemId != null ? mapItemId : DEFAULT_MAP_ITEM_ID;
    }

    @Nonnull
    public String getThemeColorHex() {
        return String.format("#%06X", themeColor);
    }

    // ═══════════════════════════════════════════════════════════════════
    // TEMPLATE RESOLUTION
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Gets the WorldGen template name for a specific layout size.
     * Medium size uses the base prefix; other sizes append a suffix.
     */
    @Nonnull
    public String getTemplateName(@Nonnull RealmLayoutSize size) {
        if (size == RealmLayoutSize.MEDIUM) {
            return templatePrefix;
        }
        return templatePrefix + "_" + size.name().toLowerCase();
    }

    /**
     * Gets the template name for a specific arena radius tier.
     * Finds the closest tier >= the given radius and appends it as suffix (e.g., {@code realm_forest_r50}).
     */
    @Nonnull
    public String getTemplateNameForRadius(int arenaRadius) {
        int tier = RADIUS_TIERS[RADIUS_TIERS.length - 1];
        for (int t : RADIUS_TIERS) {
            if (t < arenaRadius) continue;
            tier = t;
            break;
        }
        return templatePrefix.toLowerCase() + "_r" + tier;
    }

    // ═══════════════════════════════════════════════════════════════════
    // CLASSIFICATION
    // ═══════════════════════════════════════════════════════════════════

    /** Volcano, Void, Corrupted — higher mob scaling and environmental hazards */
    public boolean isHighDifficulty() {
        return this == VOLCANO || this == VOID || this == CORRUPTED;
    }

    /** Non-combat biomes (Skill Sanctum) — no mob spawning, no timer */
    public boolean isUtilityBiome() {
        return this == SKILL_SANCTUM;
    }

    /** Biomes with environmental damage zones (lava, poison, void damage) */
    public boolean hasEnvironmentalHazards() {
        return this == VOLCANO || this == SWAMP || this == VOID || this == CORRUPTED;
    }

    // ═══════════════════════════════════════════════════════════════════
    // STATIC UTILITIES
    // ═══════════════════════════════════════════════════════════════════

    /** Returns a random non-corrupted combat biome (for realm map generation) */
    @Nonnull
    public static RealmBiomeType randomNonCorrupted(@Nonnull Random random) {
        RealmBiomeType[] combatBiomes = {
                FOREST, DESERT, VOLCANO, TUNDRA, SWAMP, MOUNTAINS,
                BEACH, JUNGLE, CAVERNS, FROZEN_CRYPTS, SAND_TOMBS, VOID
        };
        return combatBiomes[random.nextInt(combatBiomes.length)];
    }

    /** Parses a biome type from string (case-insensitive) */
    @Nonnull
    public static RealmBiomeType fromString(@Nonnull String name) {
        if (name == null) {
            throw new IllegalArgumentException("Biome name cannot be null");
        }
        try {
            return RealmBiomeType.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown biome type: " + name);
        }
    }
}
